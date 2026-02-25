import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/utils/fractional_index.dart';
import '../../attachments/widgets/attachment_picker.dart';
import '../../attachments/widgets/attachment_viewer.dart';
import '../providers/bullet_tree_provider.dart';
import '../repositories/bullet_repository.dart';
import 'bullet_editor.dart';
import 'context_menu.dart';
import 'swipe_action_wrapper.dart';

// ---------------------------------------------------------------------------
// Drag data
// ---------------------------------------------------------------------------

class _BulletDragData {
  const _BulletDragData({
    required this.id,
    required this.documentId,
  });

  final String id;
  final String documentId;
}

// ---------------------------------------------------------------------------
// DraggableSiblingList  (public — used by both BulletItem and BulletTree)
// ---------------------------------------------------------------------------

/// A column of sibling [BulletNode]s with drag-to-reorder support.
///
/// Each bullet is wrapped in a [LongPressDraggable]. [_DropSlot] widgets
/// between bullets act as [DragTarget]s; on accept they compute a new
/// fractional-index position and call [BulletRepository.moveBullet].
class DraggableSiblingList extends ConsumerWidget {
  const DraggableSiblingList({
    super.key,
    required this.nodes,
    required this.documentId,
    required this.parentId,
    required this.indentLevel,
  });

  final List<BulletNode> nodes;
  final String documentId;

  /// The parent bullet ID for this sibling group, or null for root level.
  final String? parentId;
  final int indentLevel;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _DropSlot(
          slotIndex: 0,
          siblings: nodes,
          parentId: parentId,
          documentId: documentId,
        ),
        for (int i = 0; i < nodes.length; i++) ...[
          LongPressDraggable<_BulletDragData>(
            data: _BulletDragData(
              id: nodes[i].data.id,
              documentId: documentId,
            ),
            feedback: _DragFeedback(
              node: nodes[i],
              documentId: documentId,
              indentLevel: indentLevel,
            ),
            childWhenDragging: Opacity(
              opacity: 0.3,
              child: BulletItem(
                node: nodes[i],
                documentId: documentId,
                indentLevel: indentLevel,
                isDragging: true,
              ),
            ),
            child: SwipeActionWrapper(
              key: ValueKey(nodes[i].data.id),
              node: nodes[i],
              documentId: documentId,
              child: BulletItem(
                node: nodes[i],
                documentId: documentId,
                indentLevel: indentLevel,
              ),
            ),
          ),
          _DropSlot(
            slotIndex: i + 1,
            siblings: nodes,
            parentId: parentId,
            documentId: documentId,
          ),
        ],
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// Drop slot
// ---------------------------------------------------------------------------

/// A thin [DragTarget] between bullet rows.
///
/// Highlights and expands when a [_BulletDragData] is hovering. On accept,
/// computes the new fractional-index position and calls [moveBullet].
class _DropSlot extends ConsumerWidget {
  const _DropSlot({
    required this.slotIndex,
    required this.siblings,
    required this.parentId,
    required this.documentId,
  });

  final int slotIndex;
  final List<BulletNode> siblings;
  final String? parentId;
  final String documentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DragTarget<_BulletDragData>(
      builder: (ctx, candidates, _) {
        final isActive = candidates.isNotEmpty;
        return AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          height: isActive ? 24.0 : 8.0,
          decoration: BoxDecoration(
            color: isActive
                ? Theme.of(ctx).colorScheme.primary.withValues(alpha: 0.25)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(2),
          ),
        );
      },
      onWillAcceptWithDetails: (details) {
        if (details.data.documentId != documentId) return false;
        // Reject slots immediately adjacent to the current position.
        final dragIdx =
            siblings.indexWhere((n) => n.data.id == details.data.id);
        if (dragIdx == -1) return true; // From a different parent group.
        return slotIndex != dragIdx && slotIndex != dragIdx + 1;
      },
      onAcceptWithDetails: (details) => _onAccept(ref, details.data),
    );
  }

  void _onAccept(WidgetRef ref, _BulletDragData data) {
    final String newPosition;
    if (siblings.isEmpty) {
      newPosition = FractionalIndex.first();
    } else if (slotIndex == 0) {
      newPosition = FractionalIndex.before(siblings.first.data.position);
    } else if (slotIndex >= siblings.length) {
      newPosition = FractionalIndex.after(siblings.last.data.position);
    } else {
      newPosition = FractionalIndex.between(
        siblings[slotIndex - 1].data.position,
        siblings[slotIndex].data.position,
      );
    }

    unawaited(
      ref.read(bulletRepositoryProvider).moveBullet(
            id: data.id,
            documentId: data.documentId,
            newParentId: parentId,
            newPosition: newPosition,
          ),
    );
  }
}

// ---------------------------------------------------------------------------
// Drag feedback (ghost)
// ---------------------------------------------------------------------------

/// The widget shown under the finger/pointer during a drag.
///
/// Rendered as a Material card with a drag-handle icon and bullet content.
class _DragFeedback extends StatelessWidget {
  const _DragFeedback({
    required this.node,
    required this.documentId,
    required this.indentLevel,
  });

  final BulletNode node;
  final String documentId;
  final int indentLevel;

  @override
  Widget build(BuildContext context) {
    return Material(
      elevation: 4,
      borderRadius: BorderRadius.circular(4),
      color: Theme.of(context).colorScheme.surface,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minWidth: 200, maxWidth: 480),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.drag_handle, size: 16, color: Colors.grey),
              const SizedBox(width: 4),
              Text(
                '●',
                style: TextStyle(
                  color: Theme.of(context).colorScheme.primary,
                  fontSize: 10,
                ),
              ),
              const SizedBox(width: 4),
              Flexible(
                child: Text(
                  node.data.content,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// BulletItem
// ---------------------------------------------------------------------------

/// Renders a single bullet row: drag-handle + glyph + [BulletEditor] + expand/collapse.
///
/// Each [BulletItem] is wrapped externally in a [SwipeActionWrapper] by its
/// parent [DraggableSiblingList]. The row renders isComplete styling
/// (strikethrough + dim).
class BulletItem extends ConsumerStatefulWidget {
  const BulletItem({
    super.key,
    required this.node,
    required this.documentId,
    required this.indentLevel,
    this.isDragging = false,
  });

  final BulletNode node;
  final String documentId;
  final int indentLevel;

  /// When true the drag-handle icon is shown on the left of the row (used
  /// by [LongPressDraggable.childWhenDragging]).
  final bool isDragging;

  @override
  ConsumerState<BulletItem> createState() => _BulletItemState();
}

class _BulletItemState extends ConsumerState<BulletItem> {
  bool _isExpanded = true;

  bool get _hasChildren => widget.node.children.isNotEmpty;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildRow(context),
        AttachmentViewer(bulletId: widget.node.data.id),
        if (_hasChildren && _isExpanded)
          Padding(
            padding: const EdgeInsets.only(left: 16),
            child: DraggableSiblingList(
              nodes: widget.node.children,
              documentId: widget.documentId,
              parentId: widget.node.data.id,
              indentLevel: widget.indentLevel + 1,
            ),
          ),
      ],
    );
  }

  Widget _buildRow(BuildContext context) {
    final isComplete = widget.node.data.isComplete;
    return Opacity(
      opacity: isComplete ? 0.5 : 1.0,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Drag handle — visible when this item is the childWhenDragging ghost.
          AnimatedOpacity(
            opacity: widget.isDragging ? 1.0 : 0.0,
            duration: const Duration(milliseconds: 200),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(4, 12, 0, 8),
              child: Icon(
                Icons.drag_handle,
                size: 16,
                color: Theme.of(context)
                    .colorScheme
                    .onSurface
                    .withValues(alpha: 0.5),
              ),
            ),
          ),

          // Bullet glyph — double-tap to zoom; long-press for context menu.
          GestureDetector(
            onDoubleTap: () {
              ref
                  .read(bulletTreeNotifierProvider(widget.documentId).notifier)
                  .zoomTo(widget.node.data.id);
            },
            onLongPress: () => showBulletContextMenu(
              context,
              bulletId: widget.node.data.id,
              documentId: widget.documentId,
              onIndent: () => _indent(context),
              onOutdent: () => _outdent(context),
              onDuplicate: () => _duplicate(context),
              onDelete: () => _deleteWithConfirm(context),
              onAddAttachment: () => showAttachmentPicker(
                context,
                ref,
                bulletId: widget.node.data.id,
              ),
            ),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(8, 12, 4, 8),
              child: Text(
                '●',
                style: TextStyle(
                  color: isComplete
                      ? Theme.of(context)
                          .colorScheme
                          .onSurface
                          .withValues(alpha: 0.4)
                      : Theme.of(context).colorScheme.primary,
                  fontSize: 10,
                ),
              ),
            ),
          ),

          // Rich text editor.
          Expanded(
            child: BulletEditor(
              bulletId: widget.node.data.id,
              documentId: widget.documentId,
              initialContent: widget.node.data.content,
              isComplete: isComplete,
              onEnter: () => _createSiblingBelow(context),
              onTab: () => _indent(context),
              onShiftTab: () => _outdent(context),
              onCollapse: () => setState(() => _isExpanded = false),
              onExpand: () => setState(() => _isExpanded = true),
              onDeleteEmpty: () => _deleteEmpty(context),
            ),
          ),

          // Expand/collapse toggle — only shown when there are children.
          // ExcludeFocus prevents Tab/arrow traversal from landing here.
          if (_hasChildren)
            ExcludeFocus(
              child: IconButton(
                key: Key('toggle_${widget.node.data.id}'),
                icon: Icon(
                  _isExpanded ? Icons.expand_less : Icons.expand_more,
                  size: 16,
                ),
                onPressed: () => setState(() => _isExpanded = !_isExpanded),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
              ),
            ),
        ],
      ),
    );
  }

  // -------------------------------------------------------------------------
  // Keyboard / context-menu actions
  // -------------------------------------------------------------------------

  void _createSiblingBelow(BuildContext context) {
    ref
        .read(bulletRepositoryProvider)
        .createEmptySiblingAfter(
          bulletId: widget.node.data.id,
          documentId: widget.documentId,
        )
        .then((newBullet) {
      ref
          .read(bulletTreeNotifierProvider(widget.documentId).notifier)
          .requestFocus(newBullet.id);
    });
  }

  void _indent(BuildContext context) {
    unawaited(
      ref.read(bulletRepositoryProvider).indentBullet(
            bulletId: widget.node.data.id,
            documentId: widget.documentId,
          ),
    );
  }

  void _outdent(BuildContext context) {
    unawaited(
      ref.read(bulletRepositoryProvider).outdentBullet(
            bulletId: widget.node.data.id,
            documentId: widget.documentId,
          ),
    );
  }

  void _duplicate(BuildContext context) {
    unawaited(
      ref.read(bulletRepositoryProvider).duplicateBullet(
            bulletId: widget.node.data.id,
            documentId: widget.documentId,
          ),
    );
  }

  void _deleteWithConfirm(BuildContext context) {
    unawaited(
      ref.read(bulletRepositoryProvider).deleteBullet(
            widget.node.data.id,
            widget.documentId,
          ),
    );
  }

  /// Called by [BulletEditor] when Backspace is pressed on empty content (web).
  ///
  /// Requests focus on the previous bullet before deleting so the cursor
  /// lands on the bullet above.
  void _deleteEmpty(BuildContext context) {
    FocusScope.of(context).previousFocus();
    unawaited(
      ref.read(bulletRepositoryProvider).deleteBullet(
            widget.node.data.id,
            widget.documentId,
          ),
    );
  }
}
