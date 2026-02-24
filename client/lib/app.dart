import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'core/api/api_client.dart';
import 'features/auth/providers/auth_provider.dart';
import 'features/auth/screens/login_screen.dart';
import 'features/documents/screens/document_detail_screen.dart';
import 'features/documents/screens/documents_screen.dart';

// ---------------------------------------------------------------------------
// Router
// ---------------------------------------------------------------------------

final _routerProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authNotifierProvider);

  return GoRouter(
    initialLocation: '/login',
    redirect: (context, state) {
      final status = authState.status;

      // Still determining auth state — do not redirect.
      if (status == AuthStatus.unknown) return null;

      final isLoggedIn = status == AuthStatus.authenticated;
      final isOnLoginPage = state.matchedLocation == '/login';

      if (!isLoggedIn && !isOnLoginPage) return '/login';
      if (isLoggedIn && isOnLoginPage) return '/documents';
      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/documents',
        builder: (context, state) => const DocumentsScreen(),
        routes: [
          GoRoute(
            path: ':id',
            builder: (context, state) => DocumentDetailScreen(
              documentId: state.pathParameters['id']!,
            ),
          ),
        ],
      ),
    ],
  );
});

// ---------------------------------------------------------------------------
// App root
// ---------------------------------------------------------------------------

class NotesApp extends ConsumerWidget {
  const NotesApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Wire up the 401 → logout callback at app startup.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(onUnauthorizedCallbackProvider.notifier).state = () {
        ref.read(authNotifierProvider.notifier).logout();
      };
    });

    final router = ref.watch(_routerProvider);

    return MaterialApp.router(
      title: 'Notes',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
        useMaterial3: true,
      ),
      routerConfig: router,
    );
  }
}
