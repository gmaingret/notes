import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/db/app_database.dart';
import '../repositories/bullet_repository.dart';

/// Collects [node] and all its descendants into a flat list (pre-order).
///
/// Called before deletion so the full subtree can be restored on undo.
List<BulletsTableData> collectSubtree(BulletNode node) {
  final result = <BulletsTableData>[node.data];
  for (final child in node.children) {
    result.addAll(collectSubtree(child));
  }
  return result;
}

/// Returns how far [offset] is as a fraction of [screenWidth] (0.0–1.0).
double swipeProgress(double offset, double screenWidth) =>
    screenWidth > 0 ? (offset.abs() / screenWidth).clamp(0.0, 1.0) : 0.0;

/// Wraps [child] with horizontal swipe gesture detection.
///
/// - Swipe **right** > 50 % screen width → mark bullet complete.
///   Background turns green with a ✓ icon.
///
/// - Swipe **left** > 50 % screen width → delete bullet with a 5-second undo
///   snackbar.  Tapping Undo re-inserts the bullet (and all its descendants)
///   at the same position with the same IDs.
///   Background turns red with a 🗑 icon.
class SwipeActionWrapper extends ConsumerStatefulWidget {
  const SwipeActionWrapper({
    super.key,
    required this.node,
    required this.documentId,
    required this.child,
  });

  final BulletNode node;
  final String documentId;
  final Widget child;

  @override
  ConsumerState<SwipeActionWrapper> createState() => _SwipeActionWrapperState();
}

class _SwipeActionWrapperState extends ConsumerState<SwipeActionWrapper>
    with SingleTickerProviderStateMixin {
  double _dragOffset = 0;
  bool _isSnapping = false;

  late final AnimationController _snapController;
  late Animation<double> _snapAnimation;

  @override
  void initState() {
    super.initState();
    _snapController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 200),
    );
    _snapAnimation =
        Tween<double>(begin: 0, end: 0).animate(_snapController);
  }

  @override
  void dispose() {
    _snapController.dispose();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Gesture callbacks
  // ---------------------------------------------------------------------------

  void _onDragStart(DragStartDetails _) {
    _snapController.stop();
    setState(() {
      _isSnapping = false;
      _dragOffset = 0;
    });
  }

  void _onDragUpdate(DragUpdateDetails d) {
    if (_isSnapping) return;
    setState(() => _dragOffset += d.delta.dx);
  }

  void _onDragEnd(DragEndDetails _) async {
    final sw = MediaQuery.of(context).size.width;
    final threshold = sw * 0.5;

    if (_dragOffset > threshold) {
      await _markComplete();
    } else if (_dragOffset < -threshold) {
      await _deleteWithUndo();
    }

    _snapBack();
  }

  void _snapBack() {
    final start = _dragOffset;
    _snapAnimation = Tween<double>(begin: start, end: 0).animate(
      CurvedAnimation(parent: _snapController, curve: Curves.easeOut),
    );
    setState(() => _isSnapping = true);
    _snapController.forward(from: 0).then((_) {
      if (mounted) {
        setState(() {
          _dragOffset = 0;
          _isSnapping = false;
        });
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Actions
  // ---------------------------------------------------------------------------

  Future<void> _markComplete() async {
    if (widget.node.data.isComplete) return;
    await ref.read(bulletRepositoryProvider).updateBullet(
          id: widget.node.data.id,
          documentId: widget.documentId,
          isComplete: true,
        );
  }

  Future<void> _deleteWithUndo() async {
    final subtree = collectSubtree(widget.node);
    final repo = ref.read(bulletRepositoryProvider);
    await repo.deleteBullet(widget.node.data.id, widget.documentId);

    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Text('Bullet deleted'),
        duration: const Duration(seconds: 5),
        action: SnackBarAction(
          label: 'Undo',
          onPressed: () => repo.restoreBullets(subtree),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Build
  // ---------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _snapController,
      builder: (context, child) {
        final offset = _isSnapping ? _snapAnimation.value : _dragOffset;
        final isRight = offset > 0;
        final isLeft = offset < 0;

        return GestureDetector(
          onHorizontalDragStart: _onDragStart,
          onHorizontalDragUpdate: _onDragUpdate,
          onHorizontalDragEnd: _onDragEnd,
          child: Stack(
            clipBehavior: Clip.hardEdge,
            children: [
              Positioned.fill(
                child: _SwipeBackground(isRight: isRight, isLeft: isLeft),
              ),
              Transform.translate(
                offset: Offset(offset, 0),
                child: child,
              ),
            ],
          ),
        );
      },
      child: widget.child,
    );
  }
}

// ---------------------------------------------------------------------------
// Background widget
// ---------------------------------------------------------------------------

class _SwipeBackground extends StatelessWidget {
  const _SwipeBackground({required this.isRight, required this.isLeft});

  final bool isRight;
  final bool isLeft;

  @override
  Widget build(BuildContext context) {
    if (isRight) {
      return Container(
        color: Colors.green,
        alignment: Alignment.centerLeft,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: const Icon(Icons.check, color: Colors.white),
      );
    }
    if (isLeft) {
      return Container(
        color: Colors.red,
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: const Icon(Icons.delete_outline, color: Colors.white),
      );
    }
    return const SizedBox.shrink();
  }
}
