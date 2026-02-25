import 'package:drift/drift.dart';

import 'daos/attachment_dao.dart';
import 'daos/bullet_dao.dart';
import 'daos/document_dao.dart';
import 'daos/sync_operation_dao.dart';
import 'tables/attachments_table.dart';
import 'tables/bullets_table.dart';
import 'tables/documents_table.dart';
import 'tables/sync_operations_table.dart';

export 'daos/attachment_dao.dart';
export 'daos/bullet_dao.dart';
export 'daos/document_dao.dart';
export 'daos/sync_operation_dao.dart';
export 'tables/attachments_table.dart';
export 'tables/bullets_table.dart';
export 'tables/documents_table.dart';
export 'tables/sync_operations_table.dart';

part 'app_database.g.dart';

@DriftDatabase(
  tables: [DocumentsTable, BulletsTable, SyncOperationsTable, AttachmentsTable],
  daos: [DocumentDao, BulletDao, SyncOperationDao, AttachmentDao],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.e);

  @override
  int get schemaVersion => 2;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (m) => m.createAll(),
        onUpgrade: (m, from, to) async {
          if (from < 2) {
            await m.createTable(attachmentsTable);
          }
        },
      );
}
