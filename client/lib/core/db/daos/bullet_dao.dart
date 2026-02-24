import 'package:drift/drift.dart';

import '../app_database.dart';

part 'bullet_dao.g.dart';

@DriftAccessor(tables: [BulletsTable])
class BulletDao extends DatabaseAccessor<AppDatabase> with _$BulletDaoMixin {
  BulletDao(super.db);

  /// Stream of non-deleted bullets for a document, ordered by position.
  Stream<List<BulletsTableData>> watchBulletsForDocument(String documentId) {
    return (select(bulletsTable)
          ..where(
            (t) => t.documentId.equals(documentId) & t.deletedAt.isNull(),
          )
          ..orderBy([(t) => OrderingTerm.asc(t.position)]))
        .watch();
  }

  /// One-shot fetch of non-deleted bullets for a document.
  Future<List<BulletsTableData>> listBulletsForDocument(String documentId) {
    return (select(bulletsTable)
          ..where(
            (t) => t.documentId.equals(documentId) & t.deletedAt.isNull(),
          )
          ..orderBy([(t) => OrderingTerm.asc(t.position)]))
        .get();
  }

  /// Insert or replace a bullet row.
  Future<void> insertBullet(BulletsTableCompanion entry) {
    return into(bulletsTable).insertOnConflictUpdate(entry);
  }

  /// Update an existing bullet.
  Future<bool> updateBullet(BulletsTableCompanion entry) {
    return update(bulletsTable).replace(entry);
  }

  /// Soft-delete: set deletedAt to [timestamp] (Unix ms).
  Future<int> softDeleteBullet(String id, int timestamp) {
    return (update(bulletsTable)..where((t) => t.id.equals(id)))
        .write(BulletsTableCompanion(deletedAt: Value(timestamp)));
  }

  /// Soft-delete all descendants of [parentId] (cascade).
  /// Caller must also soft-delete the parent itself.
  Future<void> softDeleteDescendants(
    String documentId,
    String parentId,
    int timestamp,
  ) async {
    // Fetch all bullets for the document so we can walk the tree in memory.
    final all = await (select(bulletsTable)
          ..where(
            (t) => t.documentId.equals(documentId) & t.deletedAt.isNull(),
          ))
        .get();

    final toDelete = _collectDescendants(all, parentId);

    for (final id in toDelete) {
      await (update(bulletsTable)..where((t) => t.id.equals(id)))
          .write(BulletsTableCompanion(deletedAt: Value(timestamp)));
    }
  }

  List<String> _collectDescendants(
    List<BulletsTableData> all,
    String parentId,
  ) {
    final result = <String>[];
    for (final b in all) {
      if (b.parentId == parentId) {
        result.add(b.id);
        result.addAll(_collectDescendants(all, b.id));
      }
    }
    return result;
  }
}
