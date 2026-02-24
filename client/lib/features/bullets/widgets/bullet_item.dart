import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers/bullet_tree_provider.dart';
import '../repositories/bullet_repository.dart';
import 'bullet_editor.dart';

/// Renders a single bullet row: glyph + [BulletEditor] + expand/collapse.
/// Children are rendered below via an indented column.
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
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Bullet glyph — double-tap to zoom into this node.
        GestureDetector(
          onDoubleTap: () {
            ref
                .read(bulletTreeNotifierProvider(widget.documentId).notifier)
                .zoomTo(widget.node.data.id);
          },
          child: Padding(
            padding: const EdgeInsets.fromLTRB(8, 12, 4, 8),
            child: Text(
              '●',
              style: TextStyle(
                color: widget.node.data.isComplete
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
    );
  }

  // -------------------------------------------------------------------------
  // Keyboard actions
  // -------------------------------------------------------------------------

  void _createSiblingBelow(BuildContext context) {
    // TODO(1.7): implement via BulletRepository + fractional index.
    // For now, no-op — will be wired up in a future task.
  }

  void _indent(BuildContext context) {
    // TODO(1.7): move bullet under previous sibling.
  }

  void _outdent(BuildContext context) {
    // TODO(1.7): move bullet up one level.
  }
}

// ---------------------------------------------------------------------------
// Children column
// ---------------------------------------------------------------------------

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
          BulletItem(
            key: ValueKey(child.data.id),
            node: child,
            documentId: documentId,
            indentLevel: indentLevel,
          ),
      ],
    );
  }
}
