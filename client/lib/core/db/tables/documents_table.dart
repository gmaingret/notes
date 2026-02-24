import 'package:drift/drift.dart';

class DocumentsTable extends Table {
  @override
  String get tableName => 'documents';

  TextColumn get id => text()();
  TextColumn get title => text().withDefault(const Constant('Untitled'))();
  TextColumn get position => text()();
  IntColumn get createdAt => integer()();
  IntColumn get updatedAt => integer()();
  IntColumn get deletedAt => integer().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}
