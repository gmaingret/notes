import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/// Base URL for the Notes API.
/// Override with --dart-define=API_BASE_URL=https://notes.lan when building.
const String kApiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://10.0.2.2:8000',
);

const String kTokenStorageKey = 'notes_access_token';
const String kTokenExpiresStorageKey = 'notes_token_expires_at';

// ---------------------------------------------------------------------------
// Secure storage
// ---------------------------------------------------------------------------

const FlutterSecureStorage secureStorage = FlutterSecureStorage(
  aOptions: AndroidOptions(encryptedSharedPreferences: true),
);

Future<void> storeToken(String accessToken, int expiresAt) async {
  await secureStorage.write(key: kTokenStorageKey, value: accessToken);
  await secureStorage.write(
    key: kTokenExpiresStorageKey,
    value: expiresAt.toString(),
  );
}

Future<String?> readToken() => secureStorage.read(key: kTokenStorageKey);

Future<int?> readTokenExpiry() async {
  final raw = await secureStorage.read(key: kTokenExpiresStorageKey);
  if (raw == null) return null;
  return int.tryParse(raw);
}

/// Returns true if a non-expired token is stored.
Future<bool> hasValidToken() async {
  final token = await readToken();
  if (token == null) return false;
  final expiry = await readTokenExpiry();
  if (expiry == null) return false;
  return DateTime.now().millisecondsSinceEpoch < expiry;
}

Future<void> clearToken() async {
  await secureStorage.delete(key: kTokenStorageKey);
  await secureStorage.delete(key: kTokenExpiresStorageKey);
}

// ---------------------------------------------------------------------------
// Dio builder
// ---------------------------------------------------------------------------

typedef OnUnauthorized = void Function();

/// Build a [Dio] instance with JWT injection and 401 handling.
Dio buildDio({OnUnauthorized? onUnauthorized}) {
  final dio = Dio(
    BaseOptions(
      baseUrl: kApiBaseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 30),
      headers: {'Content-Type': 'application/json'},
    ),
  );

  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) async {
        if (!options.path.startsWith('/auth/')) {
          final token = await readToken();
          if (token != null) {
            options.headers['Authorization'] = 'Bearer $token';
          }
        }
        handler.next(options);
      },
      onError: (error, handler) {
        if (error.response?.statusCode == 401) {
          onUnauthorized?.call();
        }
        handler.next(error);
      },
    ),
  );

  return dio;
}

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

/// Holds the optional [OnUnauthorized] callback so that [apiClientProvider]
/// can be constructed without depending on [authNotifierProvider].
final onUnauthorizedCallbackProvider =
    StateProvider<OnUnauthorized?>((ref) => null);

/// Shared Dio instance.  The 401 callback is wired up at app startup via
/// [onUnauthorizedCallbackProvider].
final apiClientProvider = Provider<Dio>((ref) {
  final callback = ref.watch(onUnauthorizedCallbackProvider);
  return buildDio(onUnauthorized: callback);
});
