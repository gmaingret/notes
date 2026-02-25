import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notes/core/db/app_database.dart';
import 'package:notes/core/db/database_provider.dart';
import 'package:notes/features/attachments/providers/attachment_provider.dart';
import 'package:notes/features/bullets/providers/bullet_tree_provider.dart';
import 'package:notes/features/bullets/repositories/bullet_repository.dart';
import 'package:notes/features/bullets/widgets/bullet_item.dart';
import 'package:notes/features/bullets/widgets/swipe_action_wrapper.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

AppDatabase _openInMemory() => AppDatabase(NativeDatabase.memory());

/// Fake notifier: returns empty tree without subscribing to any Drift stream,
/// preventing the zero-duration StreamQueryStore cleanup timer from triggering
/// the 'pending timers' assertion in widget tests.
class _FakeBulletTreeNotifier extends BulletTreeNotifier {
  @override
  Future<BulletTreeState> build(String documentId) async {
    return BulletTreeState(
      documentId: documentId,
      flatList: const [],
      roots: const [],
    );
  }
}

// Shared overrides: suppress Drift streams opened by BulletItem so no
// cleanup timer is left pending after the widget tree is disposed.
final _noAttachmentOverride = attachmentsForBulletProvider.overrideWith(
  (ref, bulletId) => Stream.value(<AttachmentModel>[]),
);
final _noBulletTreeOverride =
    bulletTreeNotifierProvider.overrideWith(_FakeBulletTreeNotifier.new);

/// Builds a [SwipeActionWrapper] with a fixed 400x800 screen.
Widget _buildSwipeWrapper(AppDatabase db, BulletNode node) {
  return ProviderScope(
    overrides: [databaseProvider.overrideWithValue(db), _noAttachmentOverride],
    child: MediaQuery(
      data: const MediaQueryData(size: Size(400, 800)),
      child: MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 400,
            height: 60,
            child: SwipeActionWrapper(
              key: ValueKey(node.data.id),
              node: node,
              documentId: node.data.documentId,
              child: Center(child: Text(node.data.content)),
            ),
          ),
        ),
      ),
    ),
  );
}

/// Builds a [DraggableSiblingList] wrapped in a fixed-size scaffold.
Widget _buildDraggableList(AppDatabase db, List<BulletNode> nodes) {
  return ProviderScope(
    overrides: [
      databaseProvider.overrideWithValue(db),
      _noAttachmentOverride,
      _noBulletTreeOverride,
    ],
    child: MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(
          child: DraggableSiblingList(
            nodes: nodes,
            documentId: 'doc1',
            parentId: null,
            indentLevel: 0,
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
  group('Gesture flow — SwipeActionWrapper', () {
    late AppDatabase db;
    late BulletRepository repo;

    setUp(() {
      db = _openInMemory();
      repo = BulletRepository(db);
    });
    tearDown(() async => db.close());

    testWidgets('right swipe > 50% marks bullet complete in DB',
        (tester) async {
      final created = await repo.createBullet(
        documentId: 'doc1',
        content: 'Hello',
        position: 'n',
      );
      final node = BulletNode(data: created);

      await tester.pumpWidget(_buildSwipeWrapper(db, node));

      final center = tester.getCenter(find.text('Hello'));
      // Swipe right 220 px — past 50% of 400px width.
      await tester.dragFrom(center, const Offset(220, 0));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pumpAndSettle();

      final updated = await repo.listFlatBullets('doc1');
      expect(updated.first.isComplete, isTrue);
    });

    testWidgets('left swipe > 50% soft-deletes bullet in DB', (tester) async {
      final created = await repo.createBullet(
        documentId: 'doc1',
        content: 'Hello',
        position: 'n',
      );
      final node = BulletNode(data: created);

      await tester.pumpWidget(_buildSwipeWrapper(db, node));

      final center = tester.getCenter(find.text('Hello'));
      await tester.dragFrom(center, const Offset(-220, 0));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pumpAndSettle();

      final remaining = await repo.listFlatBullets('doc1');
      expect(remaining, isEmpty);
    });

    testWidgets('undo after left swipe restores bullet in DB', (tester) async {
      final created = await repo.createBullet(
        documentId: 'doc1',
        content: 'Hello',
        position: 'n',
      );
      final node = BulletNode(data: created);

      await tester.pumpWidget(_buildSwipeWrapper(db, node));

      // Swipe left to delete.
      final center = tester.getCenter(find.text('Hello'));
      await tester.dragFrom(center, const Offset(-220, 0));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pumpAndSettle();

      expect(await repo.listFlatBullets('doc1'), isEmpty);

      // Tap Undo in snackbar.
      await tester.tap(find.text('Undo'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pumpAndSettle();

      final restored = await repo.listFlatBullets('doc1');
      expect(restored.length, 1);
      expect(restored.first.id, created.id);
    });

    testWidgets('short right swipe < 50% does not complete bullet',
        (tester) async {
      final created = await repo.createBullet(
        documentId: 'doc1',
        content: 'Hello',
        position: 'n',
      );
      final node = BulletNode(data: created);

      await tester.pumpWidget(_buildSwipeWrapper(db, node));

      final center = tester.getCenter(find.text('Hello'));
      // Only 100 px — under 50% of 400px.
      await tester.dragFrom(center, const Offset(100, 0));
      await tester.pumpAndSettle();

      final flat = await repo.listFlatBullets('doc1');
      expect(flat.first.isComplete, isFalse);
    });

    testWidgets('short left swipe < 50% does not delete bullet', (tester) async {
      final created = await repo.createBullet(
        documentId: 'doc1',
        content: 'Hello',
        position: 'n',
      );
      final node = BulletNode(data: created);

      await tester.pumpWidget(_buildSwipeWrapper(db, node));

      final center = tester.getCenter(find.text('Hello'));
      await tester.dragFrom(center, const Offset(-100, 0));
      await tester.pumpAndSettle();

      final flat = await repo.listFlatBullets('doc1');
      expect(flat.length, 1);
    });
  });

  group('Gesture flow — DraggableSiblingList', () {
    late AppDatabase db;
    late BulletRepository repo;

    setUp(() {
      db = _openInMemory();
      repo = BulletRepository(db);
    });
    tearDown(() async => db.close());

    testWidgets('renders all bullets in sibling list', (tester) async {
      final b1 = await repo.createBullet(
        documentId: 'doc1',
        content: 'Alpha',
        position: 'a',
      );
      final b2 = await repo.createBullet(
        documentId: 'doc1',
        content: 'Beta',
        position: 'n',
      );

      final nodes = [BulletNode(data: b1), BulletNode(data: b2)];

      await tester.pumpWidget(_buildDraggableList(db, nodes));
      await tester.pump();

      expect(find.text('Alpha'), findsOneWidget);
      expect(find.text('Beta'), findsOneWidget);
    });

    testWidgets('both bullets are rendered in the sibling list', (tester) async {
      final b1 = await repo.createBullet(
        documentId: 'doc1',
        content: 'Alpha',
        position: 'a',
      );
      final b2 = await repo.createBullet(
        documentId: 'doc1',
        content: 'Beta',
        position: 'n',
      );

      final nodes = [BulletNode(data: b1), BulletNode(data: b2)];
      await tester.pumpWidget(_buildDraggableList(db, nodes));
      await tester.pump();

      // Both bullet content strings should be visible in the sibling list.
      expect(find.text('Alpha'), findsOneWidget);
      expect(find.text('Beta'), findsOneWidget);
    });

    testWidgets('long-press drag starts on a bullet', (tester) async {
      final b1 = await repo.createBullet(
        documentId: 'doc1',
        content: 'Draggable',
        position: 'n',
      );
      final nodes = [BulletNode(data: b1)];

      await tester.pumpWidget(_buildDraggableList(db, nodes));
      await tester.pump();

      // Long-press to begin drag.
      final gesture = await tester.startGesture(
        tester.getCenter(find.text('Draggable')),
      );
      await tester.pump(const Duration(seconds: 1));

      // After long-press threshold, the LongPressDraggable activates.
      await gesture.up();
      await tester.pumpAndSettle();
    });
  });
}
