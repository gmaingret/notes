import 'package:drift/drift.dart' show Value;
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notes/core/db/app_database.dart';
import 'package:notes/core/db/database_provider.dart';
import 'package:notes/features/bullets/providers/bullet_tree_provider.dart';
import 'package:notes/features/bullets/repositories/bullet_repository.dart';
import 'package:notes/features/bullets/widgets/breadcrumb_bar.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

AppDatabase _openInMemory() => AppDatabase(NativeDatabase.memory());

Widget _buildUnderTest(AppDatabase db) {
  return ProviderScope(
    overrides: [databaseProvider.overrideWithValue(db)],
    child: const MaterialApp(
      home: Scaffold(body: BreadcrumbBar(documentId: 'doc1')),
    ),
  );
}

Future<void> _insertDocAndBullets(AppDatabase db) async {
  final now = DateTime.now().millisecondsSinceEpoch;

  // Insert document.
  await db.documentDao.insertDocument(
    DocumentsTableCompanion.insert(
      id: 'doc1',
      title: const Value('Test Doc'),
      position: 'n',
      createdAt: now,
      updatedAt: now,
    ),
  );

  // Insert 3-level bullet hierarchy.
  await db.bulletDao.insertBullet(
    BulletsTableCompanion.insert(
      id: 'root',
      documentId: 'doc1',
      parentId: const Value(null),
      content: const Value('Root bullet'),
      position: 'a',
      isComplete: const Value(false),
      createdAt: now,
      updatedAt: now,
    ),
  );
  await db.bulletDao.insertBullet(
    BulletsTableCompanion.insert(
      id: 'child',
      documentId: 'doc1',
      parentId: const Value('root'),
      content: const Value('Child bullet'),
      position: 'a',
      isComplete: const Value(false),
      createdAt: now,
      updatedAt: now,
    ),
  );
  await db.bulletDao.insertBullet(
    BulletsTableCompanion.insert(
      id: 'grandchild',
      documentId: 'doc1',
      parentId: const Value('child'),
      content: const Value('Grandchild bullet'),
      position: 'a',
      isComplete: const Value(false),
      createdAt: now,
      updatedAt: now,
    ),
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('BreadcrumbBar', () {
    late AppDatabase db;

    setUp(() => db = _openInMemory());
    tearDown(() async => db.close());

    testWidgets('shows only document title when at root', (tester) async {
      await _insertDocAndBullets(db);
      await tester.pumpWidget(_buildUnderTest(db));
      await tester.pumpAndSettle();

      expect(find.text('Test Doc'), findsOneWidget);
      // No chevron separators.
      expect(find.byIcon(Icons.chevron_right), findsNothing);
    });

    testWidgets('renders correct crumbs for 3-level zoom', (tester) async {
      await _insertDocAndBullets(db);
      await tester.pumpWidget(_buildUnderTest(db));
      await tester.pumpAndSettle();

      // Zoom to grandchild.
      final container = ProviderScope.containerOf(
        tester.element(find.byType(BreadcrumbBar)),
      );
      container
          .read(bulletTreeNotifierProvider('doc1').notifier)
          .zoomTo('grandchild');
      await tester.pumpAndSettle();

      expect(find.text('Test Doc'), findsOneWidget);
      expect(find.text('Root bullet'), findsOneWidget);
      expect(find.text('Child bullet'), findsOneWidget);
      expect(find.text('Grandchild bullet'), findsOneWidget);
      expect(find.byIcon(Icons.chevron_right), findsNWidgets(3));
    });

    testWidgets('tapping document crumb zooms out to root', (tester) async {
      await _insertDocAndBullets(db);
      await tester.pumpWidget(_buildUnderTest(db));
      await tester.pumpAndSettle();

      // Zoom to grandchild first.
      final container = ProviderScope.containerOf(
        tester.element(find.byType(BreadcrumbBar)),
      );
      container
          .read(bulletTreeNotifierProvider('doc1').notifier)
          .zoomTo('grandchild');
      await tester.pumpAndSettle();

      // Tap document title crumb.
      await tester.tap(find.text('Test Doc'));
      await tester.pumpAndSettle();

      // Should now show no chevrons (back at root).
      expect(find.byIcon(Icons.chevron_right), findsNothing);
    });
  });
}
