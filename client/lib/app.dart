import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'features/auth/screens/login_screen.dart';

// Placeholder screens — replaced with real implementations in Phase 1.
class _DocumentsScreen extends ConsumerWidget {
  const _DocumentsScreen();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return const Scaffold(
      body: Center(child: Text('Documents — Phase 1')),
    );
  }
}

class _DocumentDetailScreen extends ConsumerWidget {
  final String documentId;
  const _DocumentDetailScreen({required this.documentId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(title: Text(documentId)),
      body: const Center(child: Text('Bullet tree — Phase 1')),
    );
  }
}

final _router = GoRouter(
  initialLocation: '/login',
  routes: [
    GoRoute(
      path: '/login',
      builder: (context, state) => const LoginScreen(),
    ),
    GoRoute(
      path: '/documents',
      builder: (context, state) => const _DocumentsScreen(),
      routes: [
        GoRoute(
          path: ':id',
          builder: (context, state) =>
              _DocumentDetailScreen(documentId: state.pathParameters['id']!),
        ),
      ],
    ),
  ],
);

class NotesApp extends StatelessWidget {
  const NotesApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'Notes',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
        useMaterial3: true,
      ),
      routerConfig: _router,
    );
  }
}
