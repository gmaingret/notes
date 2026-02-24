import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers/bullet_tree_provider.dart';
import '../repositories/bullet_repository.dart';
import 'bullet_item.dart';

/// Renders the bullet tree from the current [zoomedNodeId] down.
/// Indentation is 16 px per level.  Each level is a [Column] of
/// [BulletItem] widgets.
class BulletTree extends ConsumerWidget {
  const BulletTree({super.key, required this.documentId});

  final String documentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncState = ref.watch(bulletTreeNotifierProvider(documentId));

    return asyncState.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (treeState) {
        final visible = treeState.visibleRoots;

        if (visible.isEmpty) {
          return const Center(
            child: Text(
              'No bullets yet.\nStart typing to add one.',
              textAlign: TextAlign.center,
            ),
          );
        }

        return SingleChildScrollView(
          padding: const EdgeInsets.all(8),
          child: _BulletLevel(
            nodes: visible,
            documentId: documentId,
            indentLevel: 0,
          ),
        );
      },
    );
  }
}

/// Renders a list of sibling [BulletNode]s at the same indent level.
class _BulletLevel extends StatelessWidget {
  const _BulletLevel({
    required this.nodes,
    required this.documentId,
    required this.indentLevel,
  });

  final List<BulletNode> nodes;
  final String documentId;
  final int indentLevel;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final node in nodes)
          BulletItem(
            key: ValueKey(node.data.id),
            node: node,
            documentId: documentId,
            indentLevel: indentLevel,
          ),
      ],
    );
  }
}
