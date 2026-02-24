import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../../core/db/app_database.dart';
import '../../../core/db/database_provider.dart';
import '../../../core/utils/fractional_index.dart';

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

  /// Update fields on bullet [id].
  ///
  /// [parentId] uses [Value<String?>] so callers can distinguish
  /// "do not change" ([Value.absent()]) from "set to null / root" ([Value(null)]).
  Future<BulletsTableData> updateBullet({
    required String id,
    required String documentId,
    String? content,
    String? position,
    Value<String?> parentId = const Value.absent(),
    bool? isComplete,
  }) async {
    final now = DateTime.now().millisecondsSinceEpoch;

    final existing = (await _bulletDao.listBulletsForDocument(documentId))
        .firstWhere((b) => b.id == id);

    // Use provided parentId if present; otherwise keep existing.
    final resolvedParentId =
        parentId.present ? parentId : Value(existing.parentId);

    final updated = BulletsTableCompanion.insert(
      id: id,
      documentId: documentId,
      parentId: resolvedParentId,
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
        'parent_id': resolvedParentId.value,
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
      parentId: Value(newParentId),
      position: newPosition,
    );
  }

  /// Re-insert a list of previously soft-deleted bullets (undo delete).
  Future<void> restoreBullets(List<BulletsTableData> bullets) async {
    for (final bullet in bullets) {
      final now = DateTime.now().millisecondsSinceEpoch;
      // Use the full companion constructor (not .insert()) so that
      // deletedAt: const Value(null) is included in the UPDATE SET clause,
      // which clears the soft-delete flag on conflict.
      await _bulletDao.insertBullet(
        BulletsTableCompanion(
          id: Value(bullet.id),
          documentId: Value(bullet.documentId),
          parentId: Value(bullet.parentId),
          content: Value(bullet.content),
          position: Value(bullet.position),
          isComplete: Value(bullet.isComplete),
          createdAt: Value(bullet.createdAt),
          updatedAt: Value(now),
          deletedAt: const Value(null),
        ),
      );
      await _enqueueSyncOp(
        operationType: 'upsert',
        entityType: 'bullet',
        entityId: bullet.id,
        payload: {
          'id': bullet.id,
          'document_id': bullet.documentId,
          'parent_id': bullet.parentId,
          'content': bullet.content,
          'position': bullet.position,
          'is_complete': bullet.isComplete,
          'created_at': bullet.createdAt,
          'updated_at': now,
        },
      );
    }
  }

  /// Move [bulletId] from [fromDocumentId] to the root of [toDocumentId].
  ///
  /// The bullet is placed at the end of [toDocumentId]'s root level.
  Future<void> moveBulletToDocument({
    required String bulletId,
    required String fromDocumentId,
    required String toDocumentId,
  }) async {
    final now = DateTime.now().millisecondsSinceEpoch;

    final existing = (await _bulletDao.listBulletsForDocument(fromDocumentId))
        .firstWhere((b) => b.id == bulletId);

    final targetBullets =
        await _bulletDao.listBulletsForDocument(toDocumentId);
    final rootBullets =
        targetBullets.where((b) => b.parentId == null).toList()
          ..sort((a, b) => a.position.compareTo(b.position));
    final newPosition = rootBullets.isEmpty
        ? FractionalIndex.first()
        : FractionalIndex.after(rootBullets.last.position);

    await _bulletDao.insertBullet(
      BulletsTableCompanion.insert(
        id: bulletId,
        documentId: toDocumentId,
        parentId: const Value(null),
        content: Value(existing.content),
        position: newPosition,
        isComplete: Value(existing.isComplete),
        createdAt: existing.createdAt,
        updatedAt: now,
      ),
    );

    await _enqueueSyncOp(
      operationType: 'upsert',
      entityType: 'bullet',
      entityId: bulletId,
      payload: {
        'id': bulletId,
        'document_id': toDocumentId,
        'parent_id': null,
        'content': existing.content,
        'position': newPosition,
        'is_complete': existing.isComplete,
        'created_at': existing.createdAt,
        'updated_at': now,
      },
    );
  }

  /// Create a copy of [bulletId] placed immediately after the original.
  Future<BulletsTableData> duplicateBullet({
    required String bulletId,
    required String documentId,
  }) async {
    final existing = (await _bulletDao.listBulletsForDocument(documentId))
        .firstWhere((b) => b.id == bulletId);

    final siblings = (await _bulletDao.listBulletsForDocument(documentId))
        .where((b) => b.parentId == existing.parentId)
        .toList()
      ..sort((a, b) => a.position.compareTo(b.position));

    final idx = siblings.indexWhere((b) => b.id == bulletId);
    final String newPosition;
    if (idx >= 0 && idx < siblings.length - 1) {
      newPosition = FractionalIndex.between(
        existing.position,
        siblings[idx + 1].position,
      );
    } else {
      newPosition = FractionalIndex.after(existing.position);
    }

    return createBullet(
      documentId: documentId,
      parentId: existing.parentId,
      content: existing.content,
      position: newPosition,
      isComplete: existing.isComplete,
    );
  }

  /// Indent [bulletId] by making it a child of its previous sibling.
  Future<void> indentBullet({
    required String bulletId,
    required String documentId,
  }) async {
    final flat = await _bulletDao.listBulletsForDocument(documentId);
    final bullet = flat.firstWhere((b) => b.id == bulletId);

    final siblings = flat
        .where((b) => b.parentId == bullet.parentId)
        .toList()
      ..sort((a, b) => a.position.compareTo(b.position));

    final idx = siblings.indexWhere((b) => b.id == bulletId);
    if (idx <= 0) return; // No previous sibling — cannot indent.

    final prevSibling = siblings[idx - 1];
    final prevChildren = flat
        .where((b) => b.parentId == prevSibling.id)
        .toList()
      ..sort((a, b) => a.position.compareTo(b.position));

    final newPosition = prevChildren.isEmpty
        ? FractionalIndex.first()
        : FractionalIndex.after(prevChildren.last.position);

    await moveBullet(
      id: bulletId,
      documentId: documentId,
      newParentId: prevSibling.id,
      newPosition: newPosition,
    );
  }

  /// Outdent [bulletId] by moving it to be a sibling after its parent.
  Future<void> outdentBullet({
    required String bulletId,
    required String documentId,
  }) async {
    final flat = await _bulletDao.listBulletsForDocument(documentId);
    final bullet = flat.firstWhere((b) => b.id == bulletId);

    if (bullet.parentId == null) return; // Already at root.

    final parent = flat.firstWhere((b) => b.id == bullet.parentId);

    final parentSiblings = flat
        .where((b) => b.parentId == parent.parentId)
        .toList()
      ..sort((a, b) => a.position.compareTo(b.position));

    final parentIdx = parentSiblings.indexWhere((b) => b.id == parent.id);
    final String newPosition;
    if (parentIdx >= 0 && parentIdx < parentSiblings.length - 1) {
      newPosition = FractionalIndex.between(
        parent.position,
        parentSiblings[parentIdx + 1].position,
      );
    } else {
      newPosition = FractionalIndex.after(parent.position);
    }

    await moveBullet(
      id: bulletId,
      documentId: documentId,
      newParentId: parent.parentId,
      newPosition: newPosition,
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
