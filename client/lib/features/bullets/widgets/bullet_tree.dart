import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers/bullet_tree_provider.dart';
import 'bullet_item.dart';

/// Renders the bullet tree from the current [zoomedNodeId] down.
/// Indentation is 16 px per level.  Each level is a [DraggableSiblingList]
/// that supports drag-to-reorder within the sibling group.
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
          child: DraggableSiblingList(
            nodes: visible,
            documentId: documentId,
            parentId: null,
            indentLevel: 0,
          ),
        );
      },
    );
  }
}
