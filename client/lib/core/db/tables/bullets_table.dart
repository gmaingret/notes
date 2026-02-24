import 'package:drift/drift.dart';

class BulletsTable extends Table {
  @override
  String get tableName => 'bullets';

  TextColumn get id => text()();
  TextColumn get documentId => text()();
  TextColumn get parentId => text().nullable()();
  TextColumn get content => text().withDefault(const Constant(''))();
  TextColumn get position => text()();
  BoolColumn get isComplete => boolean().withDefault(const Constant(false))();
  IntColumn get createdAt => integer()();
  IntColumn get updatedAt => integer()();
  IntColumn get deletedAt => integer().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}
