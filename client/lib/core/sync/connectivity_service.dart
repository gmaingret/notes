import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

// ---------------------------------------------------------------------------
// Connectivity status enum
// ---------------------------------------------------------------------------

enum ConnectivityStatus { online, offline }

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

class ConnectivityService {
  ConnectivityService(this._connectivity);

  final Connectivity _connectivity;

  /// Stream of connectivity status updates.
  Stream<ConnectivityStatus> get statusStream =>
      _connectivity.onConnectivityChanged.map(_toStatus);

  /// One-shot check of current status.
  Future<ConnectivityStatus> currentStatus() async {
    final result = await _connectivity.checkConnectivity();
    return _toStatus(result);
  }

  static ConnectivityStatus _toStatus(List<ConnectivityResult> results) {
    if (results.isEmpty) return ConnectivityStatus.offline;
    // Any result that is not 'none' means we have some connectivity.
    return results.any((r) => r != ConnectivityResult.none)
        ? ConnectivityStatus.online
        : ConnectivityStatus.offline;
  }
}

// ---------------------------------------------------------------------------
// Providers
// ---------------------------------------------------------------------------

final connectivityProvider = Provider<ConnectivityService>((ref) {
  return ConnectivityService(Connectivity());
});

final connectivityStatusProvider = StreamProvider<ConnectivityStatus>((ref) {
  return ref.watch(connectivityProvider).statusStream;
});
