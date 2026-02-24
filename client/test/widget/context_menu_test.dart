import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notes/core/db/app_database.dart';
import 'package:notes/core/db/database_provider.dart';
import 'package:notes/features/bullets/widgets/context_menu.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

AppDatabase _openInMemory() => AppDatabase(NativeDatabase.memory());

Widget _buildMenu(
  AppDatabase db, {
  VoidCallback? onIndent,
  VoidCallback? onOutdent,
  VoidCallback? onDuplicate,
  VoidCallback? onDelete,
  VoidCallback? onAddAttachment,
}) {
  return ProviderScope(
    overrides: [databaseProvider.overrideWithValue(db)],
    child: MaterialApp(
      home: Scaffold(
        body: BulletContextMenu(
          bulletId: 'b1',
          documentId: 'doc1',
          onIndent: onIndent,
          onOutdent: onOutdent,
          onDuplicate: onDuplicate,
          onDelete: onDelete,
          onAddAttachment: onAddAttachment,
        ),
      ),
    ),
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('BulletContextMenu', () {
    late AppDatabase db;

    setUp(() => db = _openInMemory());
    tearDown(() async => db.close());

    testWidgets('all six items render', (tester) async {
      await tester.pumpWidget(_buildMenu(db));

      expect(find.text('Add attachment'), findsOneWidget);
      expect(find.text('Indent'), findsOneWidget);
      expect(find.text('Outdent'), findsOneWidget);
      expect(find.text('Move to document'), findsOneWidget);
      expect(find.text('Duplicate'), findsOneWidget);
      expect(find.text('Delete'), findsOneWidget);
    });

    testWidgets('tapping Indent fires onIndent callback', (tester) async {
      var called = false;
      await tester.pumpWidget(_buildMenu(db, onIndent: () => called = true));

      await tester.tap(find.text('Indent'));
      await tester.pumpAndSettle();

      expect(called, isTrue);
    });

    testWidgets('tapping Outdent fires onOutdent callback', (tester) async {
      var called = false;
      await tester.pumpWidget(_buildMenu(db, onOutdent: () => called = true));

      await tester.tap(find.text('Outdent'));
      await tester.pumpAndSettle();

      expect(called, isTrue);
    });

    testWidgets('tapping Duplicate fires onDuplicate callback', (tester) async {
      var called = false;
      await tester.pumpWidget(_buildMenu(db, onDuplicate: () => called = true));

      await tester.tap(find.text('Duplicate'));
      await tester.pumpAndSettle();

      expect(called, isTrue);
    });

    testWidgets('tapping Delete shows confirmation dialog', (tester) async {
      await tester.pumpWidget(_buildMenu(db));

      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();

      // AlertDialog should appear.
      expect(find.text('Delete bullet?'), findsOneWidget);
      expect(
        find.text(
          'This will permanently delete the bullet and all its children.',
        ),
        findsOneWidget,
      );
      expect(find.text('Cancel'), findsOneWidget);
    });

    testWidgets('confirming Delete fires onDelete callback', (tester) async {
      var called = false;
      await tester.pumpWidget(_buildMenu(db, onDelete: () => called = true));

      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();

      // Tap the red Delete button in the confirmation dialog.
      final deleteButtons = find.text('Delete');
      // First occurrence is the menu item (already tapped), second is dialog button.
      await tester.tap(deleteButtons.last);
      await tester.pumpAndSettle();

      expect(called, isTrue);
    });

    testWidgets('cancelling Delete dialog does not fire callback',
        (tester) async {
      var called = false;
      await tester.pumpWidget(_buildMenu(db, onDelete: () => called = true));

      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();

      expect(called, isFalse);
    });
  });
}
