import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../auth/providers/auth_provider.dart';
import '../../bullets/widgets/breadcrumb_bar.dart';
import '../../bullets/widgets/bullet_tree.dart';
import '../widgets/document_sidebar.dart';

class DocumentDetailScreen extends ConsumerWidget {
  const DocumentDetailScreen({super.key, required this.documentId});

  final String documentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isWide = MediaQuery.sizeOf(context).width >= 720;

    final logoutButton = IconButton(
      icon: const Icon(Icons.logout),
      onPressed: () => ref.read(authNotifierProvider.notifier).logout(),
    );

    if (isWide) {
      return Scaffold(
        appBar: AppBar(actions: [logoutButton]),
        body: Row(
          children: [
            DocumentSidebar(selectedDocumentId: documentId),
            const VerticalDivider(width: 1),
            Expanded(
              child: Column(
                children: [
                  BreadcrumbBar(documentId: documentId),
                  const Divider(height: 1),
                  Expanded(child: BulletTree(documentId: documentId)),
                ],
              ),
            ),
          ],
        ),
      );
    }

    // Mobile layout: drawer for sidebar.
    return Scaffold(
      appBar: AppBar(
        title: Text(documentId),
        leading: BackButton(onPressed: () => context.go('/documents')),
        actions: [logoutButton],
      ),
      drawer: Drawer(
        child: DocumentSidebar(selectedDocumentId: documentId),
      ),
      body: Column(
        children: [
          BreadcrumbBar(documentId: documentId),
          const Divider(height: 1),
          Expanded(child: BulletTree(documentId: documentId)),
        ],
      ),
    );
  }
}
