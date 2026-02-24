import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/api_client.dart';
import '../db/app_database.dart';
import '../db/database_provider.dart';
import 'connectivity_service.dart';

// ---------------------------------------------------------------------------
// Sync status
// ---------------------------------------------------------------------------

enum SyncStatus { idle, syncing, error }

class SyncState {
  const SyncState({
    this.status = SyncStatus.idle,
    this.pendingCount = 0,
    this.lastSyncAt,
    this.errorMessage,
  });

  final SyncStatus status;
  final int pendingCount;
  final int? lastSyncAt; // Unix ms of last successful sync
  final String? errorMessage;

  SyncState copyWith({
    SyncStatus? status,
    int? pendingCount,
    int? lastSyncAt,
    String? errorMessage,
  }) {
    return SyncState(
      status: status ?? this.status,
      pendingCount: pendingCount ?? this.pendingCount,
      lastSyncAt: lastSyncAt ?? this.lastSyncAt,
      errorMessage: errorMessage,
    );
  }
}

// ---------------------------------------------------------------------------
// Device ID key (stored in secure storage alongside the JWT).
// ---------------------------------------------------------------------------

const String _kDeviceIdKey = 'notes_device_id';

// ---------------------------------------------------------------------------
// SyncManager
// ---------------------------------------------------------------------------

/// Optional callback that returns the device ID.
/// Override in tests to avoid FlutterSecureStorage dependency.
typedef DeviceIdResolver = Future<String> Function();

class SyncManager {
  SyncManager({
    required AppDatabase db,
    required Dio dio,
    required ConnectivityService connectivity,
    DeviceIdResolver? deviceIdResolver,
  })  : _db = db,
        _dio = dio,
        _connectivity = connectivity,
        _deviceIdResolver = deviceIdResolver;

  final AppDatabase _db;
  final Dio _dio;
  final ConnectivityService _connectivity;
  final DeviceIdResolver? _deviceIdResolver;

  /// Debounce timer — restarted on every [notifyPendingOp] call.
  Timer? _debounceTimer;
  static const _debounceDuration = Duration(milliseconds: 500);

  /// Cached device ID (lazy-loaded from secure storage).
  String? _deviceId;

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /// Call this whenever a mutation is made to local DB.
  /// Starts a 500 ms debounce; after idle, flushes pending ops if online.
  void notifyPendingOp() {
    _debounceTimer?.cancel();
    _debounceTimer = Timer(_debounceDuration, _flush);
  }

  /// Flush pending operations immediately (used on app start if online).
  Future<SyncResult> flushNow() => _flush();

  /// Perform initial full pull: fetch all documents + their bullets.
  Future<void> initialPull() async {
    try {
      // GET /documents
      final docsResp = await _dio.get<List<dynamic>>('/documents');
      final docs = docsResp.data ?? [];

      for (final docJson in docs) {
        final doc = docJson as Map<String, dynamic>;
        await _db.documentDao.insertDocument(
          DocumentsTableCompanion.insert(
            id: doc['id'] as String,
            title: doc['title'] as String,
            position: doc['position'] as String,
            createdAt: doc['created_at'] as int,
            updatedAt: doc['updated_at'] as int,
          ),
        );

        // GET /documents/:id/bullets
        final bulletsResp = await _dio.get<List<dynamic>>(
          '/documents/${doc['id']}/bullets',
        );
        final bullets = bulletsResp.data ?? [];

        for (final bJson in bullets) {
          final b = bJson as Map<String, dynamic>;
          await _db.bulletDao.insertBullet(
            BulletsTableCompanion.insert(
              id: b['id'] as String,
              documentId: b['document_id'] as String,
              parentId: Value(b['parent_id'] as String?),
              content: b['content'] as String,
              position: b['position'] as String,
              isComplete: b['is_complete'] as bool,
              createdAt: b['created_at'] as int,
              updatedAt: b['updated_at'] as int,
            ),
          );
        }
      }
    } on DioException catch (_) {
      // Network failure during initial pull — silent fail; will retry on
      // next connectivity event.
    }
  }

  // ---------------------------------------------------------------------------
  // Internal flush
  // ---------------------------------------------------------------------------

  Future<SyncResult> _flush() async {
    final status = await _connectivity.currentStatus();
    if (status == ConnectivityStatus.offline) {
      return const SyncResult(success: false, reason: 'offline');
    }

    final pending = await _db.syncOperationDao.listPending();
    if (pending.isEmpty) {
      return const SyncResult(success: true);
    }

    final deviceId = _deviceIdResolver != null
        ? await _deviceIdResolver!()
        : await _getDeviceId();

    final operations = pending
        .map(
          (op) => {
            'id': op.id,
            'operation_type': op.operationType,
            'entity_type': op.entityType,
            'entity_id': op.entityId,
            'payload': jsonDecode(op.payload),
            'client_timestamp': op.clientTimestamp,
          },
        )
        .toList();

    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/sync',
        data: {
          'device_id': deviceId,
          'last_sync_at': 0, // Phase 1: no delta pull yet
          'operations': operations,
        },
      );

      final data = response.data!;
      final applied = (data['applied'] as List<dynamic>)
          .map((e) => e as String)
          .toList();

      await _db.syncOperationDao.markApplied(applied);

      return SyncResult(success: true, appliedCount: applied.length);
    } on DioException catch (e) {
      return SyncResult(
        success: false,
        reason: e.response?.data?['detail'] as String? ?? e.message,
      );
    }
  }

  Future<String> _getDeviceId() async {
    if (_deviceId != null) return _deviceId!;

    final existing = await secureStorage.read(key: _kDeviceIdKey);
    if (existing != null) {
      _deviceId = existing;
      return _deviceId!;
    }

    // Generate a new device ID.
    _deviceId = _generateDeviceId();
    await secureStorage.write(key: _kDeviceIdKey, value: _deviceId!);
    return _deviceId!;
  }

  /// Simple device ID generator (pseudo-UUID v4 style).
  static String _generateDeviceId() {
    final now = DateTime.now().millisecondsSinceEpoch;
    final rand =
        (now * 6364136223846793005 + 1442695040888963407).toRadixString(16);
    return 'device-$rand';
  }
}

// ---------------------------------------------------------------------------
// Result type
// ---------------------------------------------------------------------------

class SyncResult {
  const SyncResult({
    required this.success,
    this.appliedCount = 0,
    this.reason,
  });

  final bool success;
  final int appliedCount;
  final String? reason;
}

// ---------------------------------------------------------------------------
// Riverpod Notifier
// ---------------------------------------------------------------------------

class SyncNotifier extends Notifier<SyncState> {
  @override
  SyncState build() {
    final db = ref.watch(databaseProvider);
    final dio = ref.watch(apiClientProvider);
    final connectivity = ref.watch(connectivityProvider);

    _manager = SyncManager(db: db, dio: dio, connectivity: connectivity);

    // On startup, trigger sync if online.
    unawaited(Future.microtask(_onStartup));

    return const SyncState();
  }

  late SyncManager _manager;

  Future<void> _onStartup() async {
    final status = await ref
        .read(connectivityProvider)
        .currentStatus();
    if (status == ConnectivityStatus.online) {
      await sync();
    }
  }

  /// Trigger a sync flush.
  Future<void> sync() async {
    state = state.copyWith(status: SyncStatus.syncing);
    final result = await _manager.flushNow();
    if (result.success) {
      state = state.copyWith(
        status: SyncStatus.idle,
        lastSyncAt: DateTime.now().millisecondsSinceEpoch,
      );
    } else {
      state = state.copyWith(
        status: SyncStatus.error,
        errorMessage: result.reason,
      );
    }
  }

  /// Notify that a pending operation has been added.
  void notifyPendingOp() {
    _manager.notifyPendingOp();
  }

  /// Run the initial full pull from the server.
  Future<void> initialPull() => _manager.initialPull();
}

final syncNotifierProvider = NotifierProvider<SyncNotifier, SyncState>(
  SyncNotifier.new,
);
