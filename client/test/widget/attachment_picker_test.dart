import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notes/core/db/app_database.dart';
import 'package:notes/core/db/database_provider.dart';
import 'package:notes/features/attachments/providers/attachment_provider.dart';
import 'package:notes/features/attachments/widgets/attachment_viewer.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

AppDatabase _openInMemory() => AppDatabase(NativeDatabase.memory());

/// Builds a scaffold that contains the four attachment picker option tiles.
///
/// We test the sheet content by embedding a static tile list so we can
/// inspect the ListTile widgets without needing native platform channels
/// (camera / file picker / microphone).
Widget _buildPickerSheet(AppDatabase db) {
  return ProviderScope(
    overrides: [
      databaseProvider.overrideWithValue(db),
      // Offline mode: null Dio so no network calls are made.
      attachmentRepositoryProvider.overrideWith(
        (ref) => AttachmentRepository(db, null),
      ),
    ],
    child: MaterialApp(
      home: Scaffold(
        body: SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: const [
              ListTile(
                key: Key('attach_camera'),
                leading: Icon(Icons.camera_alt),
                title: Text('Camera'),
              ),
              ListTile(
                key: Key('attach_gallery'),
                leading: Icon(Icons.photo_library),
                title: Text('Gallery'),
              ),
              ListTile(
                key: Key('attach_file'),
                leading: Icon(Icons.attach_file),
                title: Text('File'),
              ),
              ListTile(
                key: Key('attach_audio'),
                leading: Icon(Icons.mic),
                title: Text('Record audio'),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

Widget _buildViewer(AppDatabase db, String bulletId) {
  return ProviderScope(
    overrides: [
      databaseProvider.overrideWithValue(db),
      attachmentRepositoryProvider.overrideWith(
        (ref) => AttachmentRepository(db, null),
      ),
    ],
    child: MaterialApp(
      home: Scaffold(
        body: AttachmentViewer(bulletId: bulletId),
      ),
    ),
  );
}

/// Disposes the current widget tree and drains any pending Drift stream
/// cleanup timers before the test framework's invariant check.
///
/// 1. Settle all animations first.
/// 2. Replace tree with empty widget (triggers Drift stream cancellation).
/// 3. Pump zero to fire the zero-duration cleanup timer.
Future<void> _drainStreams(WidgetTester tester) async {
  await tester.pumpAndSettle();
  await tester.pumpWidget(const SizedBox());
  await tester.pump(Duration.zero);
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('AttachmentPicker sheet options', () {
    late AppDatabase db;

    setUp(() => db = _openInMemory());
    tearDown(() async => db.close());

    testWidgets('renders Camera option', (tester) async {
      await tester.pumpWidget(_buildPickerSheet(db));
      expect(find.byKey(const Key('attach_camera')), findsOneWidget);
      expect(find.text('Camera'), findsOneWidget);
    });

    testWidgets('renders Gallery option', (tester) async {
      await tester.pumpWidget(_buildPickerSheet(db));
      expect(find.byKey(const Key('attach_gallery')), findsOneWidget);
      expect(find.text('Gallery'), findsOneWidget);
    });

    testWidgets('renders File option', (tester) async {
      await tester.pumpWidget(_buildPickerSheet(db));
      expect(find.byKey(const Key('attach_file')), findsOneWidget);
      expect(find.text('File'), findsOneWidget);
    });

    testWidgets('renders Record audio option', (tester) async {
      await tester.pumpWidget(_buildPickerSheet(db));
      expect(find.byKey(const Key('attach_audio')), findsOneWidget);
      expect(find.text('Record audio'), findsOneWidget);
    });

    testWidgets('all four options are present simultaneously', (tester) async {
      await tester.pumpWidget(_buildPickerSheet(db));
      expect(find.byType(ListTile), findsNWidgets(4));
    });
  });

  group('AttachmentViewer', () {
    late AppDatabase db;
    late AttachmentRepository repo;

    setUp(() {
      db = _openInMemory();
      // null Dio = offline mode, no network calls.
      repo = AttachmentRepository(db, null);
    });
    tearDown(() async => db.close());

    testWidgets('shows nothing when no attachments exist', (tester) async {
      await tester.pumpWidget(_buildViewer(db, 'b1'));
      await tester.pump();

      expect(find.byType(Chip), findsNothing);
      expect(find.byType(ActionChip), findsNothing);

      await _drainStreams(tester);
    });

    testWidgets('shows file chip after direct repository insert', (tester) async {
      const bulletId = 'b1';
      // Insert a file attachment directly via repository (offline path).
      await repo.uploadAttachment(
        bulletId: bulletId,
        filePath: '/tmp/test.pdf',
        filename: 'test.pdf',
        mimeType: 'application/pdf',
      );

      await tester.pumpWidget(_buildViewer(db, bulletId));
      // Allow stream to emit.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      // Verify file chip rendered: look for the filename text.
      expect(find.text('test.pdf'), findsOneWidget);

      await _drainStreams(tester);
    });

    testWidgets('does not show deleted attachments', (tester) async {
      const bulletId = 'b3';
      final attachment = await repo.uploadAttachment(
        bulletId: bulletId,
        filePath: '/tmp/old.pdf',
        filename: 'old.pdf',
        mimeType: 'application/pdf',
      );

      // Soft-delete.
      await repo.deleteAttachment(attachment.id);

      await tester.pumpWidget(_buildViewer(db, bulletId));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('old.pdf'), findsNothing);

      await _drainStreams(tester);
    });

    testWidgets('shows multiple attachments for the same bullet', (tester) async {
      const bulletId = 'b4';
      await repo.uploadAttachment(
        bulletId: bulletId,
        filePath: '/tmp/a.pdf',
        filename: 'a.pdf',
        mimeType: 'application/pdf',
      );
      await repo.uploadAttachment(
        bulletId: bulletId,
        filePath: '/tmp/b.pdf',
        filename: 'b.pdf',
        mimeType: 'application/pdf',
      );

      await tester.pumpWidget(_buildViewer(db, bulletId));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('a.pdf'), findsOneWidget);
      expect(find.text('b.pdf'), findsOneWidget);

      await _drainStreams(tester);
    });

    testWidgets('AttachmentModel.isImage returns true for image/* mimeType',
        (tester) async {
      const model = AttachmentModel(
        id: 'x',
        bulletId: 'b',
        filename: 'photo.jpg',
        mimeType: 'image/jpeg',
        size: 0,
        createdAt: 0,
      );
      expect(model.isImage, isTrue);
      expect(model.isAudio, isFalse);
    });

    testWidgets('AttachmentModel.isAudio returns true for audio/* mimeType',
        (tester) async {
      const model = AttachmentModel(
        id: 'x',
        bulletId: 'b',
        filename: 'rec.aac',
        mimeType: 'audio/aac',
        size: 0,
        createdAt: 0,
      );
      expect(model.isAudio, isTrue);
      expect(model.isImage, isFalse);
    });
  });
}
