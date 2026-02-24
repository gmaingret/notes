// Integration test: auth flow
//
// This test runs on a real device/emulator with mocked HTTP via a
// Dio adapter.  Google sign-in is mocked at the provider level.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:mocktail/mocktail.dart';

import 'package:notes/app.dart';
import 'package:notes/core/api/api_client.dart';
import 'package:drift/native.dart';
import 'package:notes/core/db/app_database.dart';
import 'package:notes/core/db/database_provider.dart';
import 'package:notes/features/auth/providers/auth_provider.dart';

// ---------------------------------------------------------------------------
// Mock auth notifier that bypasses Google sign-in
// ---------------------------------------------------------------------------

class _MockAuthNotifier extends AuthNotifier {
  @override
  AuthState build() => const AuthState(status: AuthStatus.unauthenticated);

  @override
  Future<void> signInWithGoogle() async {
    // Simulate successful sign-in by storing a fake token.
    final futureExpiry = DateTime.now()
        .add(const Duration(hours: 24))
        .millisecondsSinceEpoch;
    await storeToken('fake.jwt.token', futureExpiry);
    state = const AuthState(status: AuthStatus.authenticated);
  }

  @override
  Future<void> logout() async {
    await clearToken();
    state = const AuthState(status: AuthStatus.unauthenticated);
  }
}

// ---------------------------------------------------------------------------
// Test
// ---------------------------------------------------------------------------

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Auth flow integration', () {
    late AppDatabase db;

    setUp(() async {
      db = AppDatabase(NativeDatabase.memory());
    });

    tearDown(() async => db.close());

    testWidgets('mock sign-in → JWT stored → /documents accessible',
        (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            databaseProvider.overrideWithValue(db),
            authNotifierProvider.overrideWith(_MockAuthNotifier.new),
          ],
          child: const NotesApp(),
        ),
      );
      await tester.pumpAndSettle();

      // Initially on login screen.
      expect(find.byKey(const Key('google_sign_in_button')), findsOneWidget);

      // Tap sign-in button.
      await tester.tap(find.byKey(const Key('google_sign_in_button')));
      await tester.pumpAndSettle();

      // Should navigate to documents screen.
      expect(find.text('Documents'), findsWidgets);
      expect(find.byKey(const Key('google_sign_in_button')), findsNothing);
    });

    testWidgets('logout → back to /login', (tester) async {
      // Start authenticated.
      final futureExpiry = DateTime.now()
          .add(const Duration(hours: 24))
          .millisecondsSinceEpoch;
      await storeToken('fake.jwt.token', futureExpiry);

      final authenticatedNotifier = _AlreadyAuthNotifier();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            databaseProvider.overrideWithValue(db),
            authNotifierProvider.overrideWith(() => authenticatedNotifier),
          ],
          child: const NotesApp(),
        ),
      );
      await tester.pumpAndSettle();

      // Should be on documents screen.
      expect(find.text('Documents'), findsWidgets);

      // Tap logout button.
      await tester.tap(find.byIcon(Icons.logout));
      await tester.pumpAndSettle();

      // Should be back on login screen.
      expect(find.byKey(const Key('google_sign_in_button')), findsOneWidget);
    });
  });
}

class _AlreadyAuthNotifier extends AuthNotifier {
  @override
  AuthState build() => const AuthState(status: AuthStatus.authenticated);

  @override
  Future<void> signInWithGoogle() async {}

  @override
  Future<void> logout() async {
    await clearToken();
    state = const AuthState(status: AuthStatus.unauthenticated);
  }
}
