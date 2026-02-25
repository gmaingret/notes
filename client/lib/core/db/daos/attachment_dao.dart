import 'package:drift/drift.dart';

import '../app_database.dart';

part 'attachment_dao.g.dart';

@DriftAccessor(tables: [AttachmentsTable])
class AttachmentDao extends DatabaseAccessor<AppDatabase>
    with _$AttachmentDaoMixin {
  AttachmentDao(super.db);

  Future<void> insertAttachment(AttachmentsTableCompanion c) =>
      into(attachmentsTable).insertOnConflictUpdate(c);

  Future<List<AttachmentsTableData>> listForBullet(String bulletId) =>
      (select(attachmentsTable)
            ..where((t) => t.bulletId.equals(bulletId))
            ..where((t) => t.deletedAt.isNull()))
          .get();

  Stream<List<AttachmentsTableData>> watchForBullet(String bulletId) =>
      (select(attachmentsTable)
            ..where((t) => t.bulletId.equals(bulletId))
            ..where((t) => t.deletedAt.isNull()))
          .watch();

  Future<void> softDelete(String id, int deletedAtMs) =>
      (update(attachmentsTable)..where((t) => t.id.equals(id)))
          .write(AttachmentsTableCompanion(deletedAt: Value(deletedAtMs)));
}
