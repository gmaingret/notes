import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/utils/fractional_index.dart';
import '../providers/bullet_tree_provider.dart';
import '../repositories/bullet_repository.dart';
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
          return InkWell(
            onTap: () {
              ref
                  .read(bulletRepositoryProvider)
                  .createBullet(
                    documentId: documentId,
                    content: '',
                    position: FractionalIndex.first(),
                  )
                  .then((newBullet) {
                ref
                    .read(bulletTreeNotifierProvider(documentId).notifier)
                    .requestFocus(newBullet.id);
              });
            },
            child: Padding(
              padding: const EdgeInsets.fromLTRB(8, 12, 8, 8),
              child: Row(
                children: [
                  Text(
                    '●',
                    style: TextStyle(
                      color: Theme.of(context)
                          .colorScheme
                          .primary
                          .withValues(alpha: 0.3),
                      fontSize: 10,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    'Click to add a bullet',
                    style: TextStyle(
                      color: Theme.of(context)
                          .colorScheme
                          .onSurface
                          .withValues(alpha: 0.3),
                    ),
                  ),
                ],
              ),
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
