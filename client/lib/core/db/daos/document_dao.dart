import 'package:drift/drift.dart';

import '../app_database.dart';
import '../tables/documents_table.dart';

part 'document_dao.g.dart';

@DriftAccessor(tables: [DocumentsTable])
class DocumentDao extends DatabaseAccessor<AppDatabase>
    with _$DocumentDaoMixin {
  DocumentDao(super.db);

  /// Stream of all non-deleted documents, ordered by position.
  Stream<List<DocumentsTableData>> watchAllDocuments() {
    return (select(documentsTable)
          ..where((t) => t.deletedAt.isNull())
          ..orderBy([(t) => OrderingTerm.asc(t.position)]))
        .watch();
  }

  /// One-shot fetch of all non-deleted documents.
  Future<List<DocumentsTableData>> listDocuments() {
    return (select(documentsTable)
          ..where((t) => t.deletedAt.isNull())
          ..orderBy([(t) => OrderingTerm.asc(t.position)]))
        .get();
  }

  /// Insert or replace a document row.
  Future<void> insertDocument(DocumentsTableCompanion entry) {
    return into(documentsTable).insertOnConflictUpdate(entry);
  }

  /// Update an existing document (title, position, updatedAt).
  Future<bool> updateDocument(DocumentsTableCompanion entry) {
    return update(documentsTable).replace(entry);
  }

  /// Soft-delete: set deletedAt to [timestamp] (Unix ms).
  Future<int> softDeleteDocument(String id, int timestamp) {
    return (update(documentsTable)
          ..where((t) => t.id.equals(id)))
        .write(DocumentsTableCompanion(deletedAt: Value(timestamp)));
  }
}
