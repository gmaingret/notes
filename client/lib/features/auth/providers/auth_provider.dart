import 'package:dio/dio.dart';
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

final _googleSignIn = GoogleSignIn(scopes: ['email', 'profile']);

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
    state = const AuthState(status: AuthStatus.unknown);
    try {
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
