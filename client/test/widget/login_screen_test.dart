import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:notes/features/auth/providers/auth_provider.dart';
import 'package:notes/features/auth/screens/login_screen.dart';

// ---------------------------------------------------------------------------
// Test notifiers
// ---------------------------------------------------------------------------

/// Notifier that starts in unauthenticated state. Sign-in is a no-op.
class _UnauthNotifier extends AuthNotifier {
  @override
  AuthState build() => const AuthState(status: AuthStatus.unauthenticated);

  @override
  Future<void> signInWithGoogle() async {}

  @override
  Future<void> logout() async {}
}

/// Notifier that starts in unknown state (simulates session restore in progress).
class _UnknownStatusNotifier extends AuthNotifier {
  @override
  AuthState build() => const AuthState(status: AuthStatus.unknown);

  @override
  Future<void> signInWithGoogle() async {}

  @override
  Future<void> logout() async {}
}

/// Notifier that transitions to authenticated on signInWithGoogle().
class _FakeSignInNotifier extends AuthNotifier {
  bool signInCalled = false;

  @override
  AuthState build() => const AuthState(status: AuthStatus.unauthenticated);

  @override
  Future<void> signInWithGoogle() async {
    signInCalled = true;
    state = const AuthState(status: AuthStatus.authenticated);
  }

  @override
  Future<void> logout() async {}
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('LoginScreen', () {
    testWidgets('renders Sign in with Google button when unauthenticated',
        (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authNotifierProvider.overrideWith(_UnauthNotifier.new),
          ],
          child: const MaterialApp(home: LoginScreen()),
        ),
      );
      await tester.pump();

      expect(find.byKey(const Key('google_sign_in_button')), findsOneWidget);
      expect(find.text('Sign in with Google'), findsOneWidget);
    });

    testWidgets('shows CircularProgressIndicator while auth status is unknown',
        (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authNotifierProvider.overrideWith(_UnknownStatusNotifier.new),
          ],
          child: const MaterialApp(home: LoginScreen()),
        ),
      );
      await tester.pump();

      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.byKey(const Key('google_sign_in_button')), findsNothing);
    });

    testWidgets('tapping sign-in button calls signInWithGoogle', (tester) async {
      final notifier = _FakeSignInNotifier();

      final router = GoRouter(
        initialLocation: '/',
        routes: [
          GoRoute(
            path: '/',
            builder: (_, __) => const LoginScreen(),
          ),
          GoRoute(
            path: '/documents',
            builder: (_, __) => const Scaffold(body: Text('Documents')),
          ),
        ],
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authNotifierProvider.overrideWith(() => notifier),
          ],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.pump();

      // Tap the sign-in button.
      await tester.tap(find.byKey(const Key('google_sign_in_button')));
      await tester.pumpAndSettle();

      expect(notifier.signInCalled, isTrue);
    });

    testWidgets('shows error message when auth fails', (tester) async {
      final notifier = _ErrorSignInNotifier();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authNotifierProvider.overrideWith(() => notifier),
          ],
          child: const MaterialApp(home: LoginScreen()),
        ),
      );
      await tester.pump();

      await tester.tap(find.byKey(const Key('google_sign_in_button')));
      await tester.pumpAndSettle();

      expect(find.text('Sign in failed'), findsOneWidget);
    });
  });
}

class _ErrorSignInNotifier extends AuthNotifier {
  @override
  AuthState build() => const AuthState(status: AuthStatus.unauthenticated);

  @override
  Future<void> signInWithGoogle() async {
    state = const AuthState(
      status: AuthStatus.unauthenticated,
      errorMessage: 'Sign in failed',
    );
  }

  @override
  Future<void> logout() async {}
}
