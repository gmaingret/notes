import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers/bullet_tree_provider.dart';
import '../repositories/bullet_repository.dart';
import 'bullet_editor.dart';
import 'context_menu.dart';
import 'swipe_action_wrapper.dart';

/// Renders a single bullet row: glyph + [BulletEditor] + expand/collapse.
///
/// Each [BulletItem] is wrapped externally in a [SwipeActionWrapper] by its
/// parent container ([_BulletLevel] in bullet_tree.dart or [_ChildrenColumn]
/// below).  The row itself renders isComplete styling (strikethrough + dim).
class BulletItem extends ConsumerStatefulWidget {
  const BulletItem({
    super.key,
    required this.node,
    required this.documentId,
    required this.indentLevel,
  });

  final BulletNode node;
  final String documentId;
  final int indentLevel;

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
        if (_hasChildren && _isExpanded)
          Padding(
            padding: const EdgeInsets.only(left: 16),
            child: _ChildrenColumn(
              children: widget.node.children,
              documentId: widget.documentId,
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
            ),
          ),

          // Expand/collapse toggle — only shown when there are children.
          if (_hasChildren)
            IconButton(
              key: Key('toggle_${widget.node.data.id}'),
              icon: Icon(
                _isExpanded ? Icons.expand_less : Icons.expand_more,
                size: 16,
              ),
              onPressed: () => setState(() => _isExpanded = !_isExpanded),
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
            ),
        ],
      ),
    );
  }

  // -------------------------------------------------------------------------
  // Keyboard / context-menu actions
  // -------------------------------------------------------------------------

  void _createSiblingBelow(BuildContext context) {
    // TODO(1.7): implement via BulletRepository + fractional index.
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
}

// ---------------------------------------------------------------------------
// Children column
// ---------------------------------------------------------------------------

/// Renders child [BulletNode]s, each wrapped in a [SwipeActionWrapper] so
/// nested bullets also support swipe gestures independently.
class _ChildrenColumn extends StatelessWidget {
  const _ChildrenColumn({
    required this.children,
    required this.documentId,
    required this.indentLevel,
  });

  final List<BulletNode> children;
  final String documentId;
  final int indentLevel;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final child in children)
          SwipeActionWrapper(
            key: ValueKey(child.data.id),
            node: child,
            documentId: documentId,
            child: BulletItem(
              node: child,
              documentId: documentId,
              indentLevel: indentLevel,
            ),
          ),
      ],
    );
  }
}
