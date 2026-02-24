import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../../core/db/app_database.dart';
import '../../../core/db/database_provider.dart';

const _uuid = Uuid();

// ---------------------------------------------------------------------------
// In-memory tree node
// ---------------------------------------------------------------------------

class BulletNode {
  BulletNode({required this.data, List<BulletNode>? children})
      : children = children ?? [];

  final BulletsTableData data;
  final List<BulletNode> children;
}

// ---------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------

class BulletRepository {
  BulletRepository(this._db);

  final AppDatabase _db;

  BulletDao get _bulletDao => _db.bulletDao;
  SyncOperationDao get _syncDao => _db.syncOperationDao;

  // -------------------------------------------------------------------------
  // Queries
  // -------------------------------------------------------------------------

  /// Fetch the flat bullet list for [documentId] and build a tree.
  Future<List<BulletNode>> loadTree(String documentId) async {
    final flat = await _bulletDao.listBulletsForDocument(documentId);
    return buildTree(flat);
  }

  /// One-shot fetch of the flat bullet list (not built into tree).
  Future<List<BulletsTableData>> listFlatBullets(String documentId) =>
      _bulletDao.listBulletsForDocument(documentId);

  Stream<List<BulletsTableData>> watchBulletsForDocument(String documentId) =>
      _bulletDao.watchBulletsForDocument(documentId);

  // -------------------------------------------------------------------------
  // Tree builder (static so tests can call it directly)
  // -------------------------------------------------------------------------

  /// Build a tree from a flat list of bullets.
  /// Returns only root-level nodes (parentId == null).
  static List<BulletNode> buildTree(List<BulletsTableData> flat) {
    final map = <String, BulletNode>{};
    final roots = <BulletNode>[];

    // Sort by position first.
    final sorted = List<BulletsTableData>.from(flat)
      ..sort((a, b) => a.position.compareTo(b.position));

    for (final b in sorted) {
      map[b.id] = BulletNode(data: b);
    }

    for (final b in sorted) {
      final node = map[b.id]!;
      if (b.parentId == null) {
        roots.add(node);
      } else {
        final parent = map[b.parentId];
        if (parent != null) {
          parent.children.add(node);
        } else {
          // Orphaned bullet (parent was soft-deleted); treat as root.
          roots.add(node);
        }
      }
    }

    return roots;
  }

  // -------------------------------------------------------------------------
  // Mutations
  // -------------------------------------------------------------------------

  Future<BulletsTableData> createBullet({
    required String documentId,
    String? parentId,
    required String content,
    required String position,
    bool isComplete = false,
  }) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final id = _uuid.v4();

    await _bulletDao.insertBullet(
      BulletsTableCompanion.insert(
        id: id,
        documentId: documentId,
        parentId: Value(parentId),
        content: Value(content),
        position: position,
        isComplete: Value(isComplete),
        createdAt: now,
        updatedAt: now,
      ),
    );

    await _enqueueSyncOp(
      operationType: 'upsert',
      entityType: 'bullet',
      entityId: id,
      payload: {
        'id': id,
        'document_id': documentId,
        'parent_id': parentId,
        'content': content,
        'position': position,
        'is_complete': isComplete,
        'created_at': now,
        'updated_at': now,
      },
    );

    return (await _bulletDao.listBulletsForDocument(documentId))
        .firstWhere((b) => b.id == id);
  }

  Future<BulletsTableData> updateBullet({
    required String id,
    required String documentId,
    String? content,
    String? position,
    String? parentId,
    bool? isComplete,
  }) async {
    final now = DateTime.now().millisecondsSinceEpoch;

    final existing = (await _bulletDao.listBulletsForDocument(documentId))
        .firstWhere((b) => b.id == id);

    final updated = BulletsTableCompanion.insert(
      id: id,
      documentId: documentId,
      parentId: Value(parentId ?? existing.parentId),
      content: Value(content ?? existing.content),
      position: position ?? existing.position,
      isComplete: Value(isComplete ?? existing.isComplete),
      createdAt: existing.createdAt,
      updatedAt: now,
    );

    await _bulletDao.insertBullet(updated);

    await _enqueueSyncOp(
      operationType: 'upsert',
      entityType: 'bullet',
      entityId: id,
      payload: {
        'id': id,
        'document_id': documentId,
        'parent_id': parentId ?? existing.parentId,
        'content': content ?? existing.content,
        'position': position ?? existing.position,
        'is_complete': isComplete ?? existing.isComplete,
        'created_at': existing.createdAt,
        'updated_at': now,
      },
    );

    return (await _bulletDao.listBulletsForDocument(documentId))
        .firstWhere((b) => b.id == id);
  }

  Future<void> deleteBullet(String id, String documentId) async {
    final now = DateTime.now().millisecondsSinceEpoch;

    // Cascade soft-delete to descendants first.
    await _bulletDao.softDeleteDescendants(documentId, id, now);
    await _bulletDao.softDeleteBullet(id, now);

    await _enqueueSyncOp(
      operationType: 'delete',
      entityType: 'bullet',
      entityId: id,
      payload: {'id': id, 'document_id': documentId, 'deleted_at': now},
    );
  }

  Future<BulletsTableData> moveBullet({
    required String id,
    required String documentId,
    String? newParentId,
    required String newPosition,
  }) async {
    return updateBullet(
      id: id,
      documentId: documentId,
      parentId: newParentId,
      position: newPosition,
    );
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  Future<void> _enqueueSyncOp({
    required String operationType,
    required String entityType,
    required String entityId,
    required Map<String, dynamic> payload,
  }) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final opId = _uuid.v4();

    await _syncDao.insertOperation(
      SyncOperationsTableCompanion.insert(
        id: opId,
        deviceId: '',
        operationType: operationType,
        entityType: entityType,
        entityId: entityId,
        payload: jsonEncode(payload),
        clientTimestamp: now,
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

final bulletRepositoryProvider = Provider<BulletRepository>((ref) {
  final db = ref.watch(databaseProvider);
  return BulletRepository(db);
});
