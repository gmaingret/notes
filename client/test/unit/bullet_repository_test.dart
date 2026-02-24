import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notes/core/db/app_database.dart';
import 'package:notes/features/bullets/repositories/bullet_repository.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

AppDatabase _openInMemory() =>
    AppDatabase(NativeDatabase.memory());

BulletsTableData _bullet({
  required String id,
  String? parentId,
  String position = 'n',
  String content = '',
}) {
  final now = DateTime.now().millisecondsSinceEpoch;
  return BulletsTableData(
    id: id,
    documentId: 'doc1',
    parentId: parentId,
    content: content,
    position: position,
    isComplete: false,
    createdAt: now,
    updatedAt: now,
    deletedAt: null,
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('BulletRepository.buildTree', () {
    test('builds tree from flat list — root only', () {
      final flat = [
        _bullet(id: 'a', position: 'a'),
        _bullet(id: 'b', position: 'b'),
        _bullet(id: 'c', position: 'c'),
      ];

      final roots = BulletRepository.buildTree(flat);

      expect(roots.length, 3);
      expect(roots.map((n) => n.data.id).toList(), ['a', 'b', 'c']);
    });

    test('builds nested tree correctly', () {
      final flat = [
        _bullet(id: 'root', position: 'n'),
        _bullet(id: 'child1', parentId: 'root', position: 'a'),
        _bullet(id: 'child2', parentId: 'root', position: 'b'),
        _bullet(id: 'grandchild', parentId: 'child1', position: 'a'),
      ];

      final roots = BulletRepository.buildTree(flat);

      expect(roots.length, 1);
      final root = roots.first;
      expect(root.data.id, 'root');
      expect(root.children.length, 2);
      expect(root.children.first.data.id, 'child1');
      expect(root.children.first.children.length, 1);
      expect(root.children.first.children.first.data.id, 'grandchild');
    });

    test('sorts siblings by position', () {
      final flat = [
        _bullet(id: 'b', position: 'b'),
        _bullet(id: 'a', position: 'a'),
        _bullet(id: 'c', position: 'c'),
      ];

      final roots = BulletRepository.buildTree(flat);

      expect(roots.map((n) => n.data.id).toList(), ['a', 'b', 'c']);
    });

    test('orphaned bullets (parent missing) promoted to root', () {
      // Parent 'ghost' is not in the list (soft-deleted).
      final flat = [
        _bullet(id: 'orphan', parentId: 'ghost', position: 'a'),
      ];

      final roots = BulletRepository.buildTree(flat);

      expect(roots.length, 1);
      expect(roots.first.data.id, 'orphan');
    });
  });

  group('BulletRepository DB operations', () {
    late AppDatabase db;
    late BulletRepository repo;

    setUp(() {
      db = _openInMemory();
      repo = BulletRepository(db);
    });

    tearDown(() async => db.close());

    test('createBullet inserts root bullet', () async {
      final bullet = await repo.createBullet(
        documentId: 'doc1',
        content: 'Hello',
        position: 'n',
      );

      expect(bullet.id, isNotEmpty);
      expect(bullet.content, 'Hello');
      expect(bullet.parentId, isNull);

      final flat = await repo.listFlatBullets('doc1');
      expect(flat.length, 1);
    });

    test('createBullet inserts child bullet', () async {
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

      expect(child.parentId, parent.id);

      final tree = await repo.loadTree('doc1');
      expect(tree.length, 1);
      expect(tree.first.children.length, 1);
    });

    test('moveBullet changes parent and position', () async {
      final a = await repo.createBullet(
        documentId: 'doc1',
        content: 'A',
        position: 'a',
      );
      final b = await repo.createBullet(
        documentId: 'doc1',
        content: 'B',
        position: 'b',
      );

      final moved = await repo.moveBullet(
        id: a.id,
        documentId: 'doc1',
        newParentId: b.id,
        newPosition: 'a',
      );

      expect(moved.parentId, b.id);
      expect(moved.position, 'a');
    });

    test('deleteBullet cascades to children', () async {
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
      final grandchild = await repo.createBullet(
        documentId: 'doc1',
        parentId: child.id,
        content: 'Grandchild',
        position: 'a',
      );

      await repo.deleteBullet(parent.id, 'doc1');

      final remaining = await repo.listFlatBullets('doc1');
      // All three should be soft-deleted (not returned by listFlatBullets).
      expect(remaining, isEmpty);
      expect(remaining.any((b) => b.id == parent.id), isFalse);
      expect(remaining.any((b) => b.id == child.id), isFalse);
      expect(remaining.any((b) => b.id == grandchild.id), isFalse);
    });

    test('sync operation enqueued on createBullet', () async {
      await repo.createBullet(
        documentId: 'doc1',
        content: 'X',
        position: 'n',
      );

      final ops = await db.syncOperationDao.listPending();
      expect(ops.isNotEmpty, isTrue);
      expect(ops.first.operationType, 'upsert');
      expect(ops.first.entityType, 'bullet');
    });
  });
}
