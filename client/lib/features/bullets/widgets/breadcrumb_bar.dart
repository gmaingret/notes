import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../documents/providers/documents_provider.dart';
import '../providers/bullet_tree_provider.dart';

/// A horizontally scrollable breadcrumb bar showing the path from document
/// root to the current [zoomedNodeId].
///
/// The document title is always the first crumb.  Each crumb is a
/// [TextButton] that, when tapped, sets [zoomedNodeId] to that node
/// (or null for the document root crumb).
class BreadcrumbBar extends ConsumerWidget {
  const BreadcrumbBar({super.key, required this.documentId});

  final String documentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncTree = ref.watch(bulletTreeNotifierProvider(documentId));
    final asyncDocs = ref.watch(documentsNotifierProvider);

    final docTitle = asyncDocs.when(
      data: (docs) {
        try {
          return docs.firstWhere((d) => d.id == documentId).title;
        } catch (_) {
          return 'Document';
        }
      },
      loading: () => 'Document',
      error: (_, __) => 'Document',
    );

    return asyncTree.when(
      loading: () => const SizedBox.shrink(),
      error: (_, __) => const SizedBox.shrink(),
      data: (treeState) {
        final path = treeState.breadcrumbPath;

        return Container(
          height: 40,
          color: Theme.of(context).colorScheme.surfaceContainerLow,
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                // Document root crumb.
                _Crumb(
                  label: docTitle,
                  isCurrent: path.isEmpty,
                  onTap: () {
                    ref
                        .read(
                          bulletTreeNotifierProvider(documentId).notifier,
                        )
                        .zoomTo(null);
                  },
                ),
                // Ancestor crumbs.
                for (int i = 0; i < path.length; i++) ...[
                  const Padding(
                    padding: EdgeInsets.symmetric(horizontal: 2),
                    child: Icon(Icons.chevron_right, size: 16),
                  ),
                  _Crumb(
                    label: path[i].data.content.isNotEmpty
                        ? path[i].data.content
                        : '(empty)',
                    isCurrent: i == path.length - 1,
                    onTap: () {
                      ref
                          .read(
                            bulletTreeNotifierProvider(documentId).notifier,
                          )
                          .zoomTo(path[i].data.id);
                    },
                  ),
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}

class _Crumb extends StatelessWidget {
  const _Crumb({
    required this.label,
    required this.isCurrent,
    required this.onTap,
  });

  final String label;
  final bool isCurrent;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return TextButton(
      onPressed: isCurrent ? null : onTap,
      style: TextButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: 8),
        minimumSize: Size.zero,
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        foregroundColor: isCurrent
            ? Theme.of(context).colorScheme.onSurface
            : Theme.of(context).colorScheme.primary,
      ),
      child: Text(
        label,
        style: TextStyle(
          fontWeight: isCurrent ? FontWeight.bold : FontWeight.normal,
        ),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
    );
  }
}
