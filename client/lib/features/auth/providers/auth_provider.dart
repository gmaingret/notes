import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_sign_in/google_sign_in.dart';

import '../../../core/api/api_client.dart';

// ---------------------------------------------------------------------------
// Auth state
// ---------------------------------------------------------------------------

enum AuthStatus { unknown, authenticated, unauthenticated }

class AuthState {
  const AuthState({required this.status, this.errorMessage});

  final AuthStatus status;
  final String? errorMessage;

  bool get isAuthenticated => status == AuthStatus.authenticated;
  bool get isUnknown => status == AuthStatus.unknown;

  AuthState copyWith({AuthStatus? status, String? errorMessage}) {
    return AuthState(
      status: status ?? this.status,
      errorMessage: errorMessage,
    );
  }
}

// ---------------------------------------------------------------------------
// Google Sign-In instance
// ---------------------------------------------------------------------------

final _googleSignIn = GoogleSignIn(
  // On web, GIS uses the credential (ID token) flow when no scopes are
  // requested. Requesting OAuth scopes forces the token flow, which returns
  // an access token but NOT an id_token. Email/name/picture are still
  // present as JWT claims in the credential.
  scopes: kIsWeb ? const [] : const ['email', 'profile'],
  clientId: kIsWeb ? const String.fromEnvironment('GOOGLE_CLIENT_ID') : null,
);

// ---------------------------------------------------------------------------
// AuthNotifier
// ---------------------------------------------------------------------------

class AuthNotifier extends Notifier<AuthState> {
  @override
  AuthState build() {
    // Check for a valid stored token asynchronously.
    _restoreSession();
    return const AuthState(status: AuthStatus.unknown);
  }

  Dio get _dio => ref.read(apiClientProvider);

  Future<void> _restoreSession() async {
    final valid = await hasValidToken();
    state = AuthState(
      status: valid ? AuthStatus.authenticated : AuthStatus.unauthenticated,
    );
  }

  /// Sign in with Google, exchange the ID token for a server JWT.
  Future<void> signInWithGoogle() async {
    if (kIsWeb &&
        const String.fromEnvironment('GOOGLE_CLIENT_ID').isEmpty) {
      state = const AuthState(
        status: AuthStatus.unauthenticated,
        errorMessage:
            'Google Sign-In is not configured. Set GOOGLE_CLIENT_ID in .env and rebuild the Docker image.',
      );
      return;
    }
    state = const AuthState(status: AuthStatus.unknown);
    try {
      // On web, clear any stale GIS token-flow session before signing in.
      // Without this, GIS may reuse a cached session that has an accessToken
      // but no idToken, causing the credential flow to be skipped entirely.
      if (kIsWeb) await _googleSignIn.signOut();
      final account = await _googleSignIn.signIn();
      if (account == null) {
        // User cancelled.
        state = const AuthState(status: AuthStatus.unauthenticated);
        return;
      }

      final auth = await account.authentication;
      final idToken = auth.idToken;
      if (idToken == null) {
        state = const AuthState(
          status: AuthStatus.unauthenticated,
          errorMessage: 'Could not obtain Google ID token.',
        );
        return;
      }

      final response = await _dio.post<Map<String, dynamic>>(
        '/auth/google',
        data: {'id_token': idToken},
      );

      final accessToken = response.data!['access_token'] as String;
      final expiresAt = response.data!['expires_at'] as int;
      await storeToken(accessToken, expiresAt);

      state = const AuthState(status: AuthStatus.authenticated);
    } on DioException catch (e) {
      state = AuthState(
        status: AuthStatus.unauthenticated,
        errorMessage: e.response?.data?['detail'] as String? ?? e.message,
      );
    } catch (e) {
      state = AuthState(
        status: AuthStatus.unauthenticated,
        errorMessage: e.toString(),
      );
    }
  }

  /// Sign out: clear local token and Google sign-in session.
  Future<void> logout() async {
    await clearToken();
    try {
      await _googleSignIn.signOut();
    } catch (_) {}
    state = const AuthState(status: AuthStatus.unauthenticated);
  }
}

final authNotifierProvider = NotifierProvider<AuthNotifier, AuthState>(
  AuthNotifier.new,
);
