import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notes/core/db/app_database.dart';
import 'package:notes/core/db/database_provider.dart';
import 'package:notes/features/bullets/repositories/bullet_repository.dart';
import 'package:notes/features/bullets/widgets/swipe_action_wrapper.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

AppDatabase _openInMemory() => AppDatabase(NativeDatabase.memory());

BulletsTableData _bullet({
  String id = 'b1',
  bool isComplete = false,
  String position = 'n',
}) {
  final now = DateTime.now().millisecondsSinceEpoch;
  return BulletsTableData(
    id: id,
    documentId: 'doc1',
    parentId: null,
    content: 'Hello',
    position: position,
    isComplete: isComplete,
    createdAt: now,
    updatedAt: now,
    deletedAt: null,
  );
}

/// Pumps a [SwipeActionWrapper] around a labelled [Text] child.
///
/// The MediaQueryData is forced to 400 × 800 so that the 50% swipe threshold
/// is always 200 px in tests, regardless of the host screen size.
Widget _buildWrapper(
  AppDatabase db,
  BulletNode node, {
  String label = 'item',
}) {
  return ProviderScope(
    overrides: [databaseProvider.overrideWithValue(db)],
    child: MediaQuery(
      data: const MediaQueryData(size: Size(400, 800)),
      child: MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 400,
            height: 60,
            child: SwipeActionWrapper(
              node: node,
              documentId: 'doc1',
              child: Center(child: Text(label)),
            ),
          ),
        ),
      ),
    ),
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('SwipeActionWrapper widget', () {
    late AppDatabase db;
    late BulletRepository repo;

    setUp(() {
      db = _openInMemory();
      repo = BulletRepository(db);
    });

    tearDown(() async => db.close());

    testWidgets('renders child normally with no drag', (tester) async {
      final node = BulletNode(data: _bullet());
      await tester.pumpWidget(_buildWrapper(db, node));
      expect(find.text('item'), findsOneWidget);
      expect(find.byIcon(Icons.check), findsNothing);
      expect(find.byIcon(Icons.delete_outline), findsNothing);
    });

    testWidgets('right swipe beyond 50% marks bullet complete', (tester) async {
      // Insert bullet into DB so updateBullet can find it.
      await repo.createBullet(
        documentId: 'doc1',
        content: 'Hello',
        position: 'n',
      );
      final flat = await repo.listFlatBullets('doc1');
      final node = BulletNode(data: flat.first);

      await tester.pumpWidget(_buildWrapper(db, node));

      // Swipe right past 50% (screen width = 400, threshold = 200).
      final center = tester.getCenter(find.text('item'));
      await tester.dragFrom(center, const Offset(220, 0));
      // Pump twice: once to process the drag end callback, once to let the
      // async DB write from _markComplete() propagate.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pumpAndSettle();

      // Bullet should now be complete in the DB.
      final updated = await repo.listFlatBullets('doc1');
      expect(updated.first.isComplete, isTrue);
    });

    testWidgets('right swipe shows green background with check icon',
        (tester) async {
      final node = BulletNode(data: _bullet());
      await tester.pumpWidget(_buildWrapper(db, node));

      final center = tester.getCenter(find.text('item'));
      // Start drag but don't release — just check mid-drag state.
      final gesture = await tester.startGesture(center);
      await gesture.moveBy(const Offset(120, 0));
      await tester.pump();

      expect(find.byIcon(Icons.check), findsOneWidget);
      expect(
        tester.widget<Container>(
          find.ancestor(
            of: find.byIcon(Icons.check),
            matching: find.byType(Container),
          ).first,
        ).color,
        Colors.green,
      );

      await gesture.cancel();
    });

    testWidgets('left swipe beyond 50% removes bullet and shows snackbar',
        (tester) async {
      final created = await repo.createBullet(
        documentId: 'doc1',
        content: 'Hello',
        position: 'n',
      );
      final node = BulletNode(data: created);

      await tester.pumpWidget(_buildWrapper(db, node));

      final center = tester.getCenter(find.text('item'));
      await tester.dragFrom(center, const Offset(-220, 0));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pumpAndSettle();

      // Bullet should be soft-deleted.
      final remaining = await repo.listFlatBullets('doc1');
      expect(remaining, isEmpty);

      // SnackBar should be visible.
      expect(find.text('Bullet deleted'), findsOneWidget);
      expect(find.text('Undo'), findsOneWidget);
    });

    testWidgets('left swipe shows red background with delete icon',
        (tester) async {
      final node = BulletNode(data: _bullet());
      await tester.pumpWidget(_buildWrapper(db, node));

      final center = tester.getCenter(find.text('item'));
      final gesture = await tester.startGesture(center);
      await gesture.moveBy(const Offset(-120, 0));
      await tester.pump();

      expect(find.byIcon(Icons.delete_outline), findsOneWidget);
      expect(
        tester.widget<Container>(
          find.ancestor(
            of: find.byIcon(Icons.delete_outline),
            matching: find.byType(Container),
          ).first,
        ).color,
        Colors.red,
      );

      await gesture.cancel();
    });

    testWidgets('undo after left swipe re-inserts bullet', (tester) async {
      final created = await repo.createBullet(
        documentId: 'doc1',
        content: 'Hello',
        position: 'n',
      );
      final node = BulletNode(data: created);

      await tester.pumpWidget(_buildWrapper(db, node));

      // Swipe left to delete.
      final center = tester.getCenter(find.text('item'));
      await tester.dragFrom(center, const Offset(-220, 0));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pumpAndSettle();

      expect(await repo.listFlatBullets('doc1'), isEmpty);

      // Tap Undo in the snackbar.
      await tester.tap(find.text('Undo'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pumpAndSettle();

      // Bullet should be restored.
      final restored = await repo.listFlatBullets('doc1');
      expect(restored.length, 1);
      expect(restored.first.id, created.id);
    });

    testWidgets('short swipe (< 50%) snaps back without action', (tester) async {
      final created = await repo.createBullet(
        documentId: 'doc1',
        content: 'Hello',
        position: 'n',
      );
      final node = BulletNode(data: created);

      await tester.pumpWidget(_buildWrapper(db, node));

      final center = tester.getCenter(find.text('item'));
      await tester.dragFrom(center, const Offset(100, 0)); // < 50% of 400
      await tester.pumpAndSettle();

      // Bullet should still be in DB, not complete.
      final flat = await repo.listFlatBullets('doc1');
      expect(flat.length, 1);
      expect(flat.first.isComplete, isFalse);
    });
  });
}
