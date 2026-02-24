import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../../core/db/app_database.dart';
import '../../../core/db/database_provider.dart';

const _uuid = Uuid();

class DocumentRepository {
  DocumentRepository(this._db);

  final AppDatabase _db;

  DocumentDao get _documentDao => _db.documentDao;
  SyncOperationDao get _syncDao => _db.syncOperationDao;

  // -------------------------------------------------------------------------
  // Queries
  // -------------------------------------------------------------------------

  Stream<List<DocumentsTableData>> watchDocuments() =>
      _documentDao.watchAllDocuments();

  Future<List<DocumentsTableData>> listDocuments() =>
      _documentDao.listDocuments();

  // -------------------------------------------------------------------------
  // Mutations
  // -------------------------------------------------------------------------

  Future<DocumentsTableData> createDocument({
    required String title,
    required String position,
  }) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final id = _uuid.v4();

    final companion = DocumentsTableCompanion.insert(
      id: id,
      title: title,
      position: position,
      createdAt: now,
      updatedAt: now,
    );
    await _documentDao.insertDocument(companion);

    await _enqueueSyncOp(
      operationType: 'upsert',
      entityType: 'document',
      entityId: id,
      payload: {
        'id': id,
        'title': title,
        'position': position,
        'created_at': now,
        'updated_at': now,
      },
    );

    return (await _documentDao.listDocuments())
        .firstWhere((d) => d.id == id);
  }

  Future<DocumentsTableData> renameDocument(String id, String newTitle) async {
    final now = DateTime.now().millisecondsSinceEpoch;

    // Fetch current row first so we can build the full updated object.
    final current = (await _documentDao.listDocuments())
        .firstWhere((d) => d.id == id);

    final companion = DocumentsTableCompanion.insert(
      id: id,
      title: newTitle,
      position: current.position,
      createdAt: current.createdAt,
      updatedAt: now,
    );
    await _documentDao.insertDocument(companion);

    await _enqueueSyncOp(
      operationType: 'upsert',
      entityType: 'document',
      entityId: id,
      payload: {
        'id': id,
        'title': newTitle,
        'position': current.position,
        'created_at': current.createdAt,
        'updated_at': now,
      },
    );

    return (await _documentDao.listDocuments())
        .firstWhere((d) => d.id == id);
  }

  Future<void> deleteDocument(String id) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    await _documentDao.softDeleteDocument(id, now);

    await _enqueueSyncOp(
      operationType: 'delete',
      entityType: 'document',
      entityId: id,
      payload: {'id': id, 'deleted_at': now},
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
        deviceId: '', // Filled in by SyncManager at flush time.
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

final documentRepositoryProvider = Provider<DocumentRepository>((ref) {
  final db = ref.watch(databaseProvider);
  return DocumentRepository(db);
});
