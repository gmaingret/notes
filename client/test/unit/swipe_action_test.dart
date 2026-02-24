import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notes/core/db/app_database.dart';
import 'package:notes/features/bullets/repositories/bullet_repository.dart';
import 'package:notes/features/bullets/widgets/swipe_action_wrapper.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

AppDatabase _openInMemory() => AppDatabase(NativeDatabase.memory());

BulletsTableData _bullet({
  String id = 'b1',
  String? parentId,
  String position = 'n',
  bool isComplete = false,
}) {
  final now = DateTime.now().millisecondsSinceEpoch;
  return BulletsTableData(
    id: id,
    documentId: 'doc1',
    parentId: parentId,
    content: 'content',
    position: position,
    isComplete: isComplete,
    createdAt: now,
    updatedAt: now,
    deletedAt: null,
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('swipeProgress', () {
    test('returns 0.0 when offset is 0', () {
      expect(swipeProgress(0, 400), 0.0);
    });

    test('returns 0.5 when offset equals half screen width', () {
      expect(swipeProgress(200, 400), 0.5);
    });

    test('returns 1.0 when offset equals full screen width', () {
      expect(swipeProgress(400, 400), 1.0);
    });

    test('clamps to 1.0 when offset exceeds screen width', () {
      expect(swipeProgress(600, 400), 1.0);
    });

    test('works with negative offset (left swipe)', () {
      expect(swipeProgress(-200, 400), 0.5);
    });

    test('returns 0.0 when screenWidth is 0 (guards division by zero)', () {
      expect(swipeProgress(100, 0), 0.0);
    });
  });

  group('collectSubtree', () {
    test('returns only root when node has no children', () {
      final node = BulletNode(data: _bullet(id: 'root'));
      final result = collectSubtree(node);
      expect(result.length, 1);
      expect(result.first.id, 'root');
    });

    test('includes direct children', () {
      final node = BulletNode(
        data: _bullet(id: 'root'),
        children: [
          BulletNode(data: _bullet(id: 'child1', parentId: 'root')),
          BulletNode(data: _bullet(id: 'child2', parentId: 'root')),
        ],
      );
      final result = collectSubtree(node);
      expect(result.length, 3);
      expect(result.map((b) => b.id).toList(), ['root', 'child1', 'child2']);
    });

    test('collects full subtree recursively', () {
      final node = BulletNode(
        data: _bullet(id: 'root'),
        children: [
          BulletNode(
            data: _bullet(id: 'child', parentId: 'root'),
            children: [
              BulletNode(
                data: _bullet(id: 'grandchild', parentId: 'child'),
              ),
            ],
          ),
        ],
      );
      final result = collectSubtree(node);
      expect(result.length, 3);
      expect(result.map((b) => b.id).toList(), [
        'root',
        'child',
        'grandchild',
      ]);
    });
  });

  group('BulletRepository.restoreBullets — undo state management', () {
    late AppDatabase db;
    late BulletRepository repo;

    setUp(() {
      db = _openInMemory();
      repo = BulletRepository(db);
    });

    tearDown(() async => db.close());

    test('re-inserts a soft-deleted bullet (undo after swipe-left)', () async {
      // Create and then delete a bullet.
      final bullet = await repo.createBullet(
        documentId: 'doc1',
        content: 'Hello',
        position: 'n',
      );
      await repo.deleteBullet(bullet.id, 'doc1');

      // Confirm it's gone.
      expect(await repo.listFlatBullets('doc1'), isEmpty);

      // Undo: restore the saved snapshot.
      final snapshot = _bullet(
        id: bullet.id,
        position: bullet.position,
      );
      await repo.restoreBullets([snapshot]);

      final restored = await repo.listFlatBullets('doc1');
      expect(restored.length, 1);
      expect(restored.first.id, bullet.id);
      expect(restored.first.deletedAt, isNull);
    });

    test('restoring preserves original position', () async {
      final bullet = await repo.createBullet(
        documentId: 'doc1',
        content: 'Keep me',
        position: 'z',
      );
      final snapshot = _bullet(id: bullet.id, position: 'z');
      await repo.deleteBullet(bullet.id, 'doc1');
      await repo.restoreBullets([snapshot]);

      final restored = await repo.listFlatBullets('doc1');
      expect(restored.first.position, 'z');
    });

    test('restoring enqueues upsert sync op', () async {
      // Clear existing sync ops from create.
      final bullet = await repo.createBullet(
        documentId: 'doc1',
        content: 'X',
        position: 'n',
      );
      await repo.deleteBullet(bullet.id, 'doc1');

      // Mark all existing ops as applied so we can verify the new one.
      final opsBefore = await db.syncOperationDao.listPending();
      await db.syncOperationDao.markApplied(opsBefore.map((o) => o.id).toList());

      final snapshot = _bullet(id: bullet.id);
      await repo.restoreBullets([snapshot]);

      final newOps = await db.syncOperationDao.listPending();
      expect(newOps.any((o) => o.operationType == 'upsert'), isTrue);
    });

    test('restores a full subtree (parent + children)', () async {
      final parent = await repo.createBullet(
        documentId: 'doc1',
        content: 'Parent',
        position: 'n',
      );
      final child = await repo.createBullet(
        documentId: 'doc1',
        parentId: parent.id,
        content: 'Child',
        position: 'a',
      );
      // Snapshot the subtree before deletion.
      final snapshots = [
        _bullet(id: parent.id, position: 'n'),
        _bullet(id: child.id, parentId: parent.id, position: 'a'),
      ];

      await repo.deleteBullet(parent.id, 'doc1');
      expect(await repo.listFlatBullets('doc1'), isEmpty);

      await repo.restoreBullets(snapshots);

      final restored = await repo.listFlatBullets('doc1');
      expect(restored.length, 2);
      expect(restored.any((b) => b.id == parent.id), isTrue);
      expect(restored.any((b) => b.id == child.id), isTrue);
    });
  });
}
