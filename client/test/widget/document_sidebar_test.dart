import 'package:drift/drift.dart' show Value;
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notes/core/db/app_database.dart';
import 'package:notes/core/db/database_provider.dart';
import 'package:notes/core/utils/fractional_index.dart';
import 'package:notes/features/documents/widgets/document_sidebar.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

AppDatabase _openInMemory() => AppDatabase(NativeDatabase.memory());

/// Drains the FakeTimers that Drift's StreamQueryStore schedules when stream
/// subscriptions are cancelled during ProviderScope disposal.
///
/// MUST be called inside the test body (not in addTearDown) so it executes
/// within the FakeAsync zone that testWidgets sets up.  addTearDown callbacks
/// run after binding.runTest() exits, outside FakeAsync, so tester.pump()
/// there does not advance the fake clock.
Future<void> _drainDriftTimers(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump(Duration.zero);
}

Widget _buildUnderTest(AppDatabase db) {
  return ProviderScope(
    overrides: [databaseProvider.overrideWithValue(db)],
    child: const MaterialApp(
      home: Scaffold(body: DocumentSidebar()),
    ),
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('DocumentSidebar', () {
    late AppDatabase db;

    setUp(() => db = _openInMemory());
    tearDown(() async => db.close());

    testWidgets('shows empty state when no documents', (tester) async {
      await tester.pumpWidget(_buildUnderTest(db));
      await tester.pumpAndSettle();

      expect(find.text('No documents.\nTap + to create one.'), findsOneWidget);

      await _drainDriftTimers(tester);
    });

    testWidgets('lists document titles', (tester) async {
      final now = DateTime.now().millisecondsSinceEpoch;
      await db.documentDao.insertDocument(
        DocumentsTableCompanion.insert(
          id: 'doc1',
          title: const Value('First Doc'),
          position: FractionalIndex.first(),
          createdAt: now,
          updatedAt: now,
        ),
      );

      await tester.pumpWidget(_buildUnderTest(db));
      await tester.pumpAndSettle();

      expect(find.text('First Doc'), findsOneWidget);

      await _drainDriftTimers(tester);
    });

    testWidgets('tapping + button shows create dialog', (tester) async {
      await tester.pumpWidget(_buildUnderTest(db));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('create_document_button')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('document_name_field')), findsOneWidget);
      expect(find.text('New Document'), findsOneWidget);

      await _drainDriftTimers(tester);
    });

    testWidgets('creating a document adds it to the list', (tester) async {
      await tester.pumpWidget(_buildUnderTest(db));
      await tester.pumpAndSettle();

      // Open dialog.
      await tester.tap(find.byKey(const Key('create_document_button')));
      await tester.pumpAndSettle();

      // Type name.
      await tester.enterText(
        find.byKey(const Key('document_name_field')),
        'My New Doc',
      );
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();

      expect(find.text('My New Doc'), findsOneWidget);

      await _drainDriftTimers(tester);
    });
  });
}
