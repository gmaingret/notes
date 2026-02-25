import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/db/app_database.dart';
import '../repositories/bullet_repository.dart';

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

class BulletTreeState {
  const BulletTreeState({
    required this.documentId,
    required this.flatList,
    required this.roots,
    this.zoomedNodeId,
    this.pendingFocusBulletId,
  });

  final String documentId;
  final List<BulletsTableData> flatList;
  final List<BulletNode> roots;

  /// ID of the bullet to use as the "root" when zoomed in, or null for
  /// document root.
  final String? zoomedNodeId;

  /// ID of the bullet that should steal keyboard focus on its next build.
  /// Cleared by [BulletEditor] once focus is granted.
  final String? pendingFocusBulletId;

  BulletTreeState copyWith({
    List<BulletsTableData>? flatList,
    List<BulletNode>? roots,
    String? zoomedNodeId,
    bool clearZoom = false,
    String? pendingFocusBulletId,
    bool clearPendingFocus = false,
  }) {
    return BulletTreeState(
      documentId: documentId,
      flatList: flatList ?? this.flatList,
      roots: roots ?? this.roots,
      zoomedNodeId: clearZoom ? null : (zoomedNodeId ?? this.zoomedNodeId),
      pendingFocusBulletId: clearPendingFocus
          ? null
          : (pendingFocusBulletId ?? this.pendingFocusBulletId),
    );
  }

  /// The nodes to render — either from the zoomed node or from the document
  /// root.
  List<BulletNode> get visibleRoots {
    if (zoomedNodeId == null) return roots;
    return _findNodeById(roots, zoomedNodeId!)?.children ?? [];
  }

  /// Breadcrumb path from document root to the current zoomed node (inclusive).
  /// Empty list means we are at document root.
  List<BulletNode> get breadcrumbPath {
    if (zoomedNodeId == null) return [];
    final path = <BulletNode>[];
    _buildPath(roots, zoomedNodeId!, path);
    return path;
  }

  static BulletNode? _findNodeById(List<BulletNode> nodes, String id) {
    for (final node in nodes) {
      if (node.data.id == id) return node;
      final found = _findNodeById(node.children, id);
      if (found != null) return found;
    }
    return null;
  }

  static bool _buildPath(
    List<BulletNode> nodes,
    String targetId,
    List<BulletNode> path,
  ) {
    for (final node in nodes) {
      if (node.data.id == targetId) {
        path.add(node);
        return true;
      }
      if (_buildPath(node.children, targetId, path)) {
        path.insert(0, node);
        return true;
      }
    }
    return false;
  }
}

// ---------------------------------------------------------------------------
// Notifier
// ---------------------------------------------------------------------------

class BulletTreeNotifier
    extends FamilyAsyncNotifier<BulletTreeState, String> {
  @override
  Future<BulletTreeState> build(String documentId) async {
    final repo = ref.watch(bulletRepositoryProvider);

    // Subscribe to the Drift stream so we rebuild on any DB change.
    ref.listen(
      _bulletStreamProvider(documentId),
      (_, next) {
        next.whenData((flat) {
          final roots = BulletRepository.buildTree(flat);
          state = state.whenData(
            (prev) => prev.copyWith(flatList: flat, roots: roots),
          );
        });
      },
    );

    final flat = await repo.listFlatBullets(documentId);
    final roots = BulletRepository.buildTree(flat);

    return BulletTreeState(
      documentId: documentId,
      flatList: flat,
      roots: roots,
    );
  }

  /// Request that the [BulletEditor] with [bulletId] steals focus on its
  /// next build (or immediately if it is already built).
  void requestFocus(String bulletId) {
    state = state.whenData(
      (prev) => prev.copyWith(pendingFocusBulletId: bulletId),
    );
  }

  /// Clear the pending focus request (called by [BulletEditor] after focusing).
  void clearPendingFocus() {
    state = state.whenData(
      (prev) => prev.copyWith(clearPendingFocus: true),
    );
  }

  void zoomTo(String? nodeId) {
    state = state.whenData(
      (prev) => nodeId == null
          ? prev.copyWith(clearZoom: true)
          : prev.copyWith(zoomedNodeId: nodeId),
    );
  }
}

final bulletTreeNotifierProvider = AsyncNotifierProviderFamily<
    BulletTreeNotifier, BulletTreeState, String>(
  BulletTreeNotifier.new,
);

// ---------------------------------------------------------------------------
// Underlying stream provider (per document)
// ---------------------------------------------------------------------------

final _bulletStreamProvider =
    StreamProvider.family<List<BulletsTableData>, String>(
  (ref, documentId) {
    final repo = ref.watch(bulletRepositoryProvider);
    return repo.watchBulletsForDocument(documentId);
  },
);
