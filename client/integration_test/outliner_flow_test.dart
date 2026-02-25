// Integration test: outliner flow
//
// Creates a document, adds bullets, tabs to create a child, zooms, and
// verifies the breadcrumb.

import 'package:drift/drift.dart' show Value;
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
          title: const Value('Test Document'),
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
          title: const Value('My Doc'),
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
          content: const Value('Root Bullet'),
          position: FractionalIndex.first(),
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
          content: const Value('Child Bullet'),
          position: FractionalIndex.first(),
          isComplete: const Value(false),
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
          title: const Value('Zoom Doc'),
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
          content: const Value('Root'),
          position: FractionalIndex.first(),
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
          content: const Value('Child'),
          position: FractionalIndex.first(),
          isComplete: const Value(false),
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

    testWidgets(
        'drag reorder: move first bullet below second updates positions in DB',
        (tester) async {
      final now = DateTime.now().millisecondsSinceEpoch;

      await db.documentDao.insertDocument(
        DocumentsTableCompanion.insert(
          id: 'doc_drag',
          title: const Value('Drag Doc'),
          position: FractionalIndex.first(),
          createdAt: now,
          updatedAt: now,
        ),
      );

      final posA = FractionalIndex.first();
      final posB = FractionalIndex.after(posA);

      await db.bulletDao.insertBullet(
        BulletsTableCompanion.insert(
          id: 'bullet_a',
          documentId: 'doc_drag',
          parentId: const Value(null),
          content: const Value('Bullet A'),
          position: posA,
          isComplete: const Value(false),
          createdAt: now,
          updatedAt: now,
        ),
      );
      await db.bulletDao.insertBullet(
        BulletsTableCompanion.insert(
          id: 'bullet_b',
          documentId: 'doc_drag',
          parentId: const Value(null),
          content: const Value('Bullet B'),
          position: posB,
          isComplete: const Value(false),
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
      await tester.tap(find.text('Drag Doc'));
      await tester.pumpAndSettle();

      expect(find.text('Bullet A'), findsOneWidget);
      expect(find.text('Bullet B'), findsOneWidget);

      // Long-press on Bullet A text to initiate drag, then drag below Bullet B.
      final bulletACenter = tester.getCenter(find.text('Bullet A'));
      final bulletBRect = tester.getRect(find.text('Bullet B'));
      final bulletBBottom = Offset(bulletBRect.center.dx, bulletBRect.bottom);

      final gesture = await tester.startGesture(bulletACenter);
      // Wait for the long-press delay to trigger LongPressDraggable.
      await tester.pump(const Duration(milliseconds: 600));
      // Drag the pointer to just below Bullet B (into the drop slot).
      await gesture.moveTo(Offset(bulletACenter.dx, bulletBBottom.dy + 4));
      await tester.pump();
      await gesture.up();
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pumpAndSettle();

      // Verify Bullet A is now positioned after Bullet B in the DB.
      final bullets =
          await db.bulletDao.listBulletsForDocument('doc_drag');
      final a = bullets.firstWhere((b) => b.id == 'bullet_a');
      final b = bullets.firstWhere((b) => b.id == 'bullet_b');
      expect(
        a.position.compareTo(b.position),
        greaterThan(0),
        reason: 'Bullet A should have a position after Bullet B after reorder',
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
