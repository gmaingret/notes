import 'package:drift/drift.dart';

import 'tables/documents_table.dart';
import 'tables/bullets_table.dart';
import 'tables/sync_operations_table.dart';

part 'app_database.g.dart';

@DriftDatabase(tables: [DocumentsTable, BulletsTable, SyncOperationsTable])
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.e);

  @override
  int get schemaVersion => 1;
}
