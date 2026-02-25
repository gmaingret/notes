import 'package:drift/drift.dart';

class AttachmentsTable extends Table {
  @override
  String get tableName => 'attachments';

  TextColumn get id => text()();
  TextColumn get bulletId => text()();
  TextColumn get filename => text()();
  TextColumn get mimeType => text()();
  TextColumn get localPath => text().nullable()();
  IntColumn get size => integer().withDefault(const Constant(0))();
  IntColumn get createdAt => integer()();
  IntColumn get deletedAt => integer().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}
