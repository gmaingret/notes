import 'package:drift/drift.dart';

import '../app_database.dart';

part 'sync_operation_dao.g.dart';

@DriftAccessor(tables: [SyncOperationsTable])
class SyncOperationDao extends DatabaseAccessor<AppDatabase>
    with _$SyncOperationDaoMixin {
  SyncOperationDao(super.db);

  /// All unapplied (pending) operations, ordered by client_timestamp.
  Future<List<SyncOperationsTableData>> listPending() {
    return (select(syncOperationsTable)
          ..where((t) => t.applied.equals(false))
          ..orderBy([(t) => OrderingTerm.asc(t.clientTimestamp)]))
        .get();
  }

  /// Insert a new sync operation.
  Future<void> insertOperation(SyncOperationsTableCompanion entry) {
    return into(syncOperationsTable).insertOnConflictUpdate(entry);
  }

  /// Mark a list of operations as applied.
  Future<void> markApplied(List<String> ids) async {
    for (final id in ids) {
      await (update(syncOperationsTable)..where((t) => t.id.equals(id)))
          .write(const SyncOperationsTableCompanion(applied: Value(true)));
    }
  }
}
