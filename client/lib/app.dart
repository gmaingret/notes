import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'core/api/api_client.dart';
import 'features/auth/providers/auth_provider.dart';
import 'features/auth/screens/login_screen.dart';
import 'features/documents/screens/document_detail_screen.dart';
import 'features/documents/screens/documents_screen.dart';

// ---------------------------------------------------------------------------
// Router provider — created once; refreshed when auth state changes
// ---------------------------------------------------------------------------

final routerProvider = Provider<GoRouter>((ref) {
  // ChangeNotifier that triggers router.refresh() on auth changes.
  final authNotifier = _AuthChangeNotifier(ref);

  final router = GoRouter(
    initialLocation: '/login',
    refreshListenable: authNotifier,
    redirect: (context, state) {
      final authState = ref.read(authNotifierProvider);
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

  // Dispose notifier when provider is disposed.
  ref.onDispose(authNotifier.dispose);

  return router;
});

/// A [ChangeNotifier] that calls [notifyListeners] whenever [authNotifierProvider]
/// emits a new state.  Used as [GoRouter.refreshListenable].
class _AuthChangeNotifier extends ChangeNotifier {
  _AuthChangeNotifier(Ref ref) {
    _subscription = ref.listen<AuthState>(
      authNotifierProvider,
      (_, __) => notifyListeners(),
    );
  }

  ProviderSubscription<AuthState>? _subscription;

  @override
  void dispose() {
    _subscription?.close();
    super.dispose();
  }
}

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

    final router = ref.watch(routerProvider);

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
