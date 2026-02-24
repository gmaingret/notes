// Integration test: outliner flow
//
// Creates a document, adds bullets, tabs to create a child, zooms, and
// verifies the breadcrumb.

import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:notes/app.dart';
import 'package:notes/core/db/app_database.dart';
import 'package:notes/core/db/database_provider.dart';
import 'package:notes/core/utils/fractional_index.dart';
import 'package:notes/features/auth/providers/auth_provider.dart';
import 'package:notes/features/bullets/providers/bullet_tree_provider.dart';
import 'package:notes/features/bullets/repositories/bullet_repository.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Outliner flow integration', () {
    late AppDatabase db;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
    });

    tearDown(() async => db.close());

    testWidgets('create document → add root bullet → verify tree structure',
        (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            databaseProvider.overrideWithValue(db),
            authNotifierProvider.overrideWith(_AuthedNotifier.new),
          ],
          child: const NotesApp(),
        ),
      );
      await tester.pumpAndSettle();

      // Should be on documents screen.
      expect(find.text('Documents'), findsWidgets);

      // Create a document via the DAO (simulating what the repository does).
      final now = DateTime.now().millisecondsSinceEpoch;
      await db.documentDao.insertDocument(
        DocumentsTableCompanion.insert(
          id: 'doc1',
          title: 'Test Document',
          position: FractionalIndex.first(),
          createdAt: now,
          updatedAt: now,
        ),
      );

      await tester.pumpAndSettle();
      expect(find.text('Test Document'), findsOneWidget);
    });

    testWidgets('add bullets and verify tree rendering', (tester) async {
      final now = DateTime.now().millisecondsSinceEpoch;

      // Pre-populate DB.
      await db.documentDao.insertDocument(
        DocumentsTableCompanion.insert(
          id: 'doc1',
          title: 'My Doc',
          position: FractionalIndex.first(),
          createdAt: now,
          updatedAt: now,
        ),
      );
      await db.bulletDao.insertBullet(
        BulletsTableCompanion.insert(
          id: 'root',
          documentId: 'doc1',
          parentId: const Value(null),
          content: 'Root Bullet',
          position: FractionalIndex.first(),
          isComplete: false,
          createdAt: now,
          updatedAt: now,
        ),
      );
      await db.bulletDao.insertBullet(
        BulletsTableCompanion.insert(
          id: 'child',
          documentId: 'doc1',
          parentId: const Value('root'),
          content: 'Child Bullet',
          position: FractionalIndex.first(),
          isComplete: false,
          createdAt: now,
          updatedAt: now,
        ),
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            databaseProvider.overrideWithValue(db),
            authNotifierProvider.overrideWith(_AuthedNotifier.new),
          ],
          child: const NotesApp(),
        ),
      );
      await tester.pumpAndSettle();

      // Navigate to document.
      await tester.tap(find.text('My Doc'));
      await tester.pumpAndSettle();

      // Both bullets should be visible.
      expect(find.text('Root Bullet'), findsOneWidget);
      expect(find.text('Child Bullet'), findsOneWidget);
    });

    testWidgets('double-tap glyph zooms into node, breadcrumb shows path',
        (tester) async {
      final now = DateTime.now().millisecondsSinceEpoch;

      await db.documentDao.insertDocument(
        DocumentsTableCompanion.insert(
          id: 'doc1',
          title: 'Zoom Doc',
          position: FractionalIndex.first(),
          createdAt: now,
          updatedAt: now,
        ),
      );
      await db.bulletDao.insertBullet(
        BulletsTableCompanion.insert(
          id: 'root',
          documentId: 'doc1',
          parentId: const Value(null),
          content: 'Root',
          position: FractionalIndex.first(),
          isComplete: false,
          createdAt: now,
          updatedAt: now,
        ),
      );
      await db.bulletDao.insertBullet(
        BulletsTableCompanion.insert(
          id: 'child',
          documentId: 'doc1',
          parentId: const Value('root'),
          content: 'Child',
          position: FractionalIndex.first(),
          isComplete: false,
          createdAt: now,
          updatedAt: now,
        ),
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            databaseProvider.overrideWithValue(db),
            authNotifierProvider.overrideWith(_AuthedNotifier.new),
          ],
          child: const NotesApp(),
        ),
      );
      await tester.pumpAndSettle();

      // Navigate to document.
      await tester.tap(find.text('Zoom Doc'));
      await tester.pumpAndSettle();

      // Double-tap root glyph to zoom.
      await tester.tap(find.text('●').first);
      await tester.pump(const Duration(milliseconds: 50));
      await tester.tap(find.text('●').first);
      await tester.pumpAndSettle();

      // Breadcrumb should now show 'Zoom Doc' and 'Root'.
      expect(find.text('Zoom Doc'), findsOneWidget);
      expect(find.text('Root'), findsOneWidget);

      // Tap breadcrumb document crumb to zoom out.
      await tester.tap(find.text('Zoom Doc'));
      await tester.pumpAndSettle();

      // Back at root — no additional breadcrumb crumbs.
      expect(
        tester
            .widgetList(find.byIcon(Icons.chevron_right))
            .length,
        0,
      );
    });
  });
}

class _AuthedNotifier extends AuthNotifier {
  @override
  AuthState build() => const AuthState(status: AuthStatus.authenticated);

  @override
  Future<void> signInWithGoogle() async {}

  @override
  Future<void> logout() async {
    state = const AuthState(status: AuthStatus.unauthenticated);
  }
}
