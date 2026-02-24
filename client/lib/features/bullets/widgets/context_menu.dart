import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../documents/providers/documents_provider.dart';
import '../repositories/bullet_repository.dart';

/// Shows the bullet context menu as a modal bottom sheet.
Future<void> showBulletContextMenu(
  BuildContext context, {
  required String bulletId,
  required String documentId,
  VoidCallback? onIndent,
  VoidCallback? onOutdent,
  VoidCallback? onDuplicate,
  VoidCallback? onDelete,
}) {
  return showModalBottomSheet<void>(
    context: context,
    builder: (_) => BulletContextMenu(
      bulletId: bulletId,
      documentId: documentId,
      onIndent: onIndent,
      onOutdent: onOutdent,
      onDuplicate: onDuplicate,
      onDelete: onDelete,
    ),
  );
}

/// Modal bottom sheet listing available actions for a bullet.
///
/// [onIndent] and [onOutdent] delegate to the caller so it can perform the
/// tree manipulation with full context.  [onDuplicate] and [onDelete] are
/// also delegated.  "Move to document" is handled internally using the
/// [documentsNotifierProvider].
class BulletContextMenu extends ConsumerStatefulWidget {
  const BulletContextMenu({
    super.key,
    required this.bulletId,
    required this.documentId,
    this.onIndent,
    this.onOutdent,
    this.onDuplicate,
    this.onDelete,
  });

  final String bulletId;
  final String documentId;
  final VoidCallback? onIndent;
  final VoidCallback? onOutdent;
  final VoidCallback? onDuplicate;
  final VoidCallback? onDelete;

  @override
  ConsumerState<BulletContextMenu> createState() => _BulletContextMenuState();
}

class _BulletContextMenuState extends ConsumerState<BulletContextMenu> {
  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            leading: const Icon(Icons.format_indent_increase),
            title: const Text('Indent'),
            onTap: () {
              Navigator.of(context).pop();
              widget.onIndent?.call();
            },
          ),
          ListTile(
            leading: const Icon(Icons.format_indent_decrease),
            title: const Text('Outdent'),
            onTap: () {
              Navigator.of(context).pop();
              widget.onOutdent?.call();
            },
          ),
          ListTile(
            leading: const Icon(Icons.drive_file_move_outline),
            title: const Text('Move to document'),
            onTap: () => _showDocumentPicker(context),
          ),
          ListTile(
            leading: const Icon(Icons.copy),
            title: const Text('Duplicate'),
            onTap: () {
              Navigator.of(context).pop();
              widget.onDuplicate?.call();
            },
          ),
          ListTile(
            leading: const Icon(Icons.delete_outline, color: Colors.red),
            title: const Text('Delete', style: TextStyle(color: Colors.red)),
            onTap: () => _confirmDelete(context),
          ),
        ],
      ),
    );
  }

  void _showDocumentPicker(BuildContext context) {
    final docs = ref.read(documentsNotifierProvider).valueOrNull ?? [];
    showModalBottomSheet<void>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text(
                'Move to document',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
            ),
            for (final doc in docs)
              if (doc.id != widget.documentId)
                ListTile(
                  title: Text(doc.title),
                  onTap: () async {
                    Navigator.of(ctx).pop();
                    Navigator.of(context).pop();
                    await ref
                        .read(bulletRepositoryProvider)
                        .moveBulletToDocument(
                          bulletId: widget.bulletId,
                          fromDocumentId: widget.documentId,
                          toDocumentId: doc.id,
                        );
                  },
                ),
          ],
        ),
      ),
    );
  }

  void _confirmDelete(BuildContext context) {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete bullet?'),
        content: const Text(
          'This will permanently delete the bullet and all its children.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              Navigator.of(context).pop();
              widget.onDelete?.call();
            },
            child: const Text('Delete', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
  }
}
