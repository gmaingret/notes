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

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

AppDatabase _openInMemory() => AppDatabase(NativeDatabase.memory());

/// Fake notifier that returns an empty tree without subscribing to any Drift
/// stream. This prevents the zero-duration cleanup timer that
/// StreamQueryStore posts on cancellation from triggering the
/// "pending timers" assertion in widget tests.
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

BulletsTableData _bullet({
  String id = 'b1',
  String content = 'Hello bullet',
  bool isComplete = false,
  String? parentId,
}) {
  final now = DateTime.now().millisecondsSinceEpoch;
  return BulletsTableData(
    id: id,
    documentId: 'doc1',
    parentId: parentId,
    content: content,
    position: 'n',
    isComplete: isComplete,
    createdAt: now,
    updatedAt: now,
    deletedAt: null,
  );
}

Widget _buildItem(AppDatabase db, BulletNode node) {
  return ProviderScope(
    overrides: [
      databaseProvider.overrideWithValue(db),
      // Override the bullet-tree notifier so it never subscribes to a Drift
      // stream — avoids the zero-duration StreamQueryStore cleanup timer that
      // triggers "pending timers" failures after widget disposal.
      bulletTreeNotifierProvider.overrideWith(_FakeBulletTreeNotifier.new),
      // Same reason: keep the attachment stream out of Drift.
      attachmentsForBulletProvider.overrideWith(
        (ref, bulletId) => Stream.value(<AttachmentModel>[]),
      ),
    ],
    child: MaterialApp(
      home: Scaffold(
        body: BulletItem(
          node: node,
          documentId: 'doc1',
          indentLevel: 0,
        ),
      ),
    ),
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('BulletItem', () {
    late AppDatabase db;

    setUp(() => db = _openInMemory());
    tearDown(() async => db.close());

    testWidgets('renders bullet content', (tester) async {
      final node = BulletNode(data: _bullet(content: 'Hello bullet'));
      await tester.pumpWidget(_buildItem(db, node));
      await tester.pump();

      // TextField shows the content as its text value.
      expect(
        find.widgetWithText(TextField, 'Hello bullet'),
        findsOneWidget,
      );

    });

    testWidgets('renders bullet glyph', (tester) async {
      final node = BulletNode(data: _bullet());
      await tester.pumpWidget(_buildItem(db, node));
      await tester.pump();

      expect(find.text('●'), findsOneWidget);

    });

    testWidgets('no expand/collapse button when no children', (tester) async {
      final node = BulletNode(data: _bullet());
      await tester.pumpWidget(_buildItem(db, node));
      await tester.pump();

      expect(find.byKey(Key('toggle_${node.data.id}')), findsNothing);

    });

    testWidgets('shows expand/collapse button when node has children',
        (tester) async {
      final parent = BulletNode(
        data: _bullet(id: 'parent'),
        children: [BulletNode(data: _bullet(id: 'child', parentId: 'parent'))],
      );

      await tester.pumpWidget(_buildItem(db, parent));
      await tester.pump();

      expect(find.byKey(const Key('toggle_parent')), findsOneWidget);

    });

    testWidgets('collapse/expand toggle works', (tester) async {
      final child = BulletNode(
        data: _bullet(id: 'child', parentId: 'parent', content: 'Child text'),
      );
      final parent = BulletNode(
        data: _bullet(id: 'parent', content: 'Parent text'),
        children: [child],
      );

      await tester.pumpWidget(_buildItem(db, parent));
      await tester.pump();

      // Initially expanded — child text visible.
      expect(
        find.widgetWithText(TextField, 'Child text'),
        findsOneWidget,
      );

      // Tap to collapse.
      await tester.tap(find.byKey(const Key('toggle_parent')));
      await tester.pump();

      expect(find.widgetWithText(TextField, 'Child text'), findsNothing);

      // Tap to expand again.
      await tester.tap(find.byKey(const Key('toggle_parent')));
      await tester.pump();

      expect(
        find.widgetWithText(TextField, 'Child text'),
        findsOneWidget,
      );

    });
  });
}
