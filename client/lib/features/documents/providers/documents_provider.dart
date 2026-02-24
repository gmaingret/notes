import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/db/app_database.dart';
import '../repositories/document_repository.dart';

// ---------------------------------------------------------------------------
// Documents AsyncNotifier
// ---------------------------------------------------------------------------

class DocumentsNotifier extends AsyncNotifier<List<DocumentsTableData>> {
  @override
  Future<List<DocumentsTableData>> build() async {
    // Subscribe to the Drift stream.  Whenever it emits, Riverpod calls
    // build() again and the UI rebuilds.
    ref.watch(_documentsStreamProvider);
    // Always fetch the current list from the DAO.
    return ref.read(documentRepositoryProvider).listDocuments();
  }

  Future<void> createDocument({
    required String title,
    required String position,
  }) async {
    await ref
        .read(documentRepositoryProvider)
        .createDocument(title: title, position: position);
    ref.invalidateSelf();
  }

  Future<void> renameDocument(String id, String newTitle) async {
    await ref.read(documentRepositoryProvider).renameDocument(id, newTitle);
    ref.invalidateSelf();
  }

  Future<void> deleteDocument(String id) async {
    await ref.read(documentRepositoryProvider).deleteDocument(id);
    ref.invalidateSelf();
  }
}

final documentsNotifierProvider =
    AsyncNotifierProvider<DocumentsNotifier, List<DocumentsTableData>>(
  DocumentsNotifier.new,
);

// ---------------------------------------------------------------------------
// Underlying stream provider — watched by the notifier above
// ---------------------------------------------------------------------------

final _documentsStreamProvider =
    StreamProvider<List<DocumentsTableData>>((ref) {
  return ref.watch(documentRepositoryProvider).watchDocuments();
});
