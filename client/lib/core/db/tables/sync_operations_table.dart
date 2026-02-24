import 'package:drift/drift.dart';

class SyncOperationsTable extends Table {
  @override
  String get tableName => 'sync_operations';

  TextColumn get id => text()();
  TextColumn get deviceId => text()();
  TextColumn get operationType => text()(); // 'upsert' | 'delete'
  TextColumn get entityType => text()();    // 'document' | 'bullet' | 'attachment'
  TextColumn get entityId => text()();
  TextColumn get payload => text()();       // JSON
  IntColumn get clientTimestamp => integer()();
  IntColumn get serverTimestamp => integer().nullable()();
  BoolColumn get applied => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}
