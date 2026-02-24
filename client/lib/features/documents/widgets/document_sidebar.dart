import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/db/app_database.dart';
import '../../../core/utils/fractional_index.dart';
import '../providers/documents_provider.dart';

class DocumentSidebar extends ConsumerWidget {
  const DocumentSidebar({
    super.key,
    this.selectedDocumentId,
    this.onDocumentSelected,
  });

  final String? selectedDocumentId;
  final ValueChanged<String>? onDocumentSelected;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncDocs = ref.watch(documentsNotifierProvider);

    return asyncDocs.when(
      loading: () => const _SidebarShell(
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (e, _) => _SidebarShell(
        child: Center(child: Text('Error: $e')),
      ),
      data: (docs) => _SidebarShell(
        child: Column(
          children: [
            _SidebarHeader(docs: docs),
            Expanded(
              child: docs.isEmpty
                  ? const Center(
                      child: Text(
                        'No documents.\nTap + to create one.',
                        textAlign: TextAlign.center,
                      ),
                    )
                  : ListView.builder(
                      itemCount: docs.length,
                      itemBuilder: (context, index) {
                        final doc = docs[index];
                        return _DocumentTile(
                          doc: doc,
                          isSelected: doc.id == selectedDocumentId,
                          onDocumentSelected: onDocumentSelected,
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SidebarShell extends StatelessWidget {
  const _SidebarShell({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 260,
      child: Material(
        color: Theme.of(context).colorScheme.surfaceContainerLow,
        child: child,
      ),
    );
  }
}

class _SidebarHeader extends ConsumerWidget {
  const _SidebarHeader({required this.docs});

  final List<DocumentsTableData> docs;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 8, 8),
      child: Row(
        children: [
          Text(
            'Documents',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const Spacer(),
          IconButton(
            key: const Key('create_document_button'),
            icon: const Icon(Icons.add),
            tooltip: 'New document',
            onPressed: () => _showCreateDialog(context, ref, docs),
          ),
        ],
      ),
    );
  }

  void _showCreateDialog(
    BuildContext context,
    WidgetRef ref,
    List<DocumentsTableData> existingDocs,
  ) {
    showDialog<void>(
      context: context,
      builder: (_) => _RenameDialog(
        title: 'New Document',
        initialValue: '',
        onConfirm: (name) async {
          if (name.trim().isEmpty) return;
          final position = existingDocs.isEmpty
              ? FractionalIndex.first()
              : FractionalIndex.after(existingDocs.last.position);
          await ref.read(documentsNotifierProvider.notifier).createDocument(
                title: name.trim(),
                position: position,
              );
        },
      ),
    );
  }
}

class _DocumentTile extends ConsumerWidget {
  const _DocumentTile({
    required this.doc,
    required this.isSelected,
    this.onDocumentSelected,
  });

  final DocumentsTableData doc;
  final bool isSelected;
  final ValueChanged<String>? onDocumentSelected;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ListTile(
      key: ValueKey(doc.id),
      title: Text(
        doc.title,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      selected: isSelected,
      onTap: () {
        if (onDocumentSelected != null) {
          onDocumentSelected!(doc.id);
        } else {
          context.go('/documents/${doc.id}');
        }
        // Close drawer on mobile.
        if (Scaffold.of(context).isDrawerOpen) {
          Navigator.of(context).pop();
        }
      },
      onLongPress: () => _showOptionsMenu(context, ref),
    );
  }

  void _showOptionsMenu(BuildContext context, WidgetRef ref) {
    showModalBottomSheet<void>(
      context: context,
      builder: (_) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.edit),
              title: const Text('Rename'),
              onTap: () {
                Navigator.pop(context);
                showDialog<void>(
                  context: context,
                  builder: (_) => _RenameDialog(
                    title: 'Rename Document',
                    initialValue: doc.title,
                    onConfirm: (newTitle) async {
                      if (newTitle.trim().isEmpty) return;
                      await ref
                          .read(documentsNotifierProvider.notifier)
                          .renameDocument(doc.id, newTitle.trim());
                    },
                  ),
                );
              },
            ),
            ListTile(
              leading: Icon(
                Icons.delete,
                color: Theme.of(context).colorScheme.error,
              ),
              title: Text(
                'Delete',
                style: TextStyle(
                  color: Theme.of(context).colorScheme.error,
                ),
              ),
              onTap: () async {
                Navigator.pop(context);
                await ref
                    .read(documentsNotifierProvider.notifier)
                    .deleteDocument(doc.id);
              },
            ),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Rename dialog (shared for create + rename)
// ---------------------------------------------------------------------------

class _RenameDialog extends StatefulWidget {
  const _RenameDialog({
    required this.title,
    required this.initialValue,
    required this.onConfirm,
  });

  final String title;
  final String initialValue;
  final Future<void> Function(String) onConfirm;

  @override
  State<_RenameDialog> createState() => _RenameDialogState();
}

class _RenameDialogState extends State<_RenameDialog> {
  late final TextEditingController _controller;
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialValue);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: TextField(
        key: const Key('document_name_field'),
        controller: _controller,
        autofocus: true,
        decoration: const InputDecoration(hintText: 'Document name'),
        onSubmitted: (_) => _submit(),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        TextButton(
          onPressed: _submitting ? null : _submit,
          child: _submitting
              ? const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Text('OK'),
        ),
      ],
    );
  }

  Future<void> _submit() async {
    setState(() => _submitting = true);
    await widget.onConfirm(_controller.text);
    if (mounted) Navigator.pop(context);
  }
}
