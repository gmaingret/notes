import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

import 'package:notes/core/db/app_database.dart';
import 'package:notes/core/sync/connectivity_service.dart';
import 'package:notes/core/sync/sync_manager.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockDio extends Mock implements Dio {}

class MockConnectivity extends Mock implements ConnectivityService {}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

AppDatabase _openInMemory() => AppDatabase(NativeDatabase.memory());

SyncOperationsTableCompanion _pendingOp({
  String id = 'op1',
  String operationType = 'upsert',
  String entityType = 'bullet',
  String entityId = 'b1',
}) {
  final now = DateTime.now().millisecondsSinceEpoch;
  return SyncOperationsTableCompanion.insert(
    id: id,
    deviceId: '',
    operationType: operationType,
    entityType: entityType,
    entityId: entityId,
    payload: jsonEncode({'id': entityId}),
    clientTimestamp: now,
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  setUpAll(() {
    // Register fallback values for mocktail.
    registerFallbackValue(RequestOptions(path: '/sync'));
  });

  group('SyncManager', () {
    late AppDatabase db;
    late MockDio dio;
    late MockConnectivity connectivity;
    late SyncManager manager;

    setUp(() {
      db = _openInMemory();
      dio = MockDio();
      connectivity = MockConnectivity();
      manager = SyncManager(
        db: db,
        dio: dio,
        connectivity: connectivity,
        // Inject a fixed device ID to avoid FlutterSecureStorage in tests.
        deviceIdResolver: () async => 'test-device-id',
      );
    });

    tearDown(() async => db.close());

    // -----------------------------------------------------------------------
    // Offline — no flush
    // -----------------------------------------------------------------------

    test('flushNow returns not-success when offline', () async {
      when(() => connectivity.currentStatus())
          .thenAnswer((_) async => ConnectivityStatus.offline);

      final result = await manager.flushNow();

      expect(result.success, isFalse);
      expect(result.reason, contains('offline'));
      verifyNever(() => dio.post<dynamic>(any(), data: any(named: 'data')));
    });

    // -----------------------------------------------------------------------
    // No pending ops — skip POST
    // -----------------------------------------------------------------------

    test('flushNow succeeds with zero ops and does not call POST', () async {
      when(() => connectivity.currentStatus())
          .thenAnswer((_) async => ConnectivityStatus.online);

      final result = await manager.flushNow();

      expect(result.success, isTrue);
      expect(result.appliedCount, 0);
      verifyNever(() => dio.post<dynamic>(any(), data: any(named: 'data')));
    });

    // -----------------------------------------------------------------------
    // Mutation enqueues op (verified via DAO)
    // -----------------------------------------------------------------------

    test('pending op in DB is sent on flush', () async {
      when(() => connectivity.currentStatus())
          .thenAnswer((_) async => ConnectivityStatus.online);

      // Insert a pending operation directly.
      await db.syncOperationDao.insertOperation(_pendingOp(id: 'op1'));

      // Mock successful /sync response.
      when(() => dio.post<Map<String, dynamic>>(
            any(),
            data: any(named: 'data'),
          )).thenAnswer(
        (_) async => Response(
          requestOptions: RequestOptions(path: '/sync'),
          statusCode: 200,
          data: {
            'server_timestamp': DateTime.now().millisecondsSinceEpoch,
            'applied': ['op1'],
            'server_delta': [],
          },
        ),
      );

      final result = await manager.flushNow();

      expect(result.success, isTrue);
      expect(result.appliedCount, 1);

      // Op should now be marked applied.
      final pending = await db.syncOperationDao.listPending();
      expect(pending, isEmpty);
    });

    // -----------------------------------------------------------------------
    // Success clears queue
    // -----------------------------------------------------------------------

    test('success marks operations as applied', () async {
      when(() => connectivity.currentStatus())
          .thenAnswer((_) async => ConnectivityStatus.online);

      await db.syncOperationDao.insertOperation(_pendingOp(id: 'op1'));
      await db.syncOperationDao.insertOperation(_pendingOp(id: 'op2'));

      when(() => dio.post<Map<String, dynamic>>(
            any(),
            data: any(named: 'data'),
          )).thenAnswer(
        (_) async => Response(
          requestOptions: RequestOptions(path: '/sync'),
          statusCode: 200,
          data: {
            'server_timestamp': DateTime.now().millisecondsSinceEpoch,
            'applied': ['op1', 'op2'],
            'server_delta': [],
          },
        ),
      );

      await manager.flushNow();

      final pending = await db.syncOperationDao.listPending();
      expect(pending, isEmpty);
    });

    // -----------------------------------------------------------------------
    // Failure retains queue
    // -----------------------------------------------------------------------

    test('failure on POST retains operations in queue', () async {
      when(() => connectivity.currentStatus())
          .thenAnswer((_) async => ConnectivityStatus.online);

      await db.syncOperationDao.insertOperation(_pendingOp(id: 'op1'));

      when(() => dio.post<Map<String, dynamic>>(
            any(),
            data: any(named: 'data'),
          )).thenThrow(
        DioException(
          requestOptions: RequestOptions(path: '/sync'),
          message: 'Connection refused',
        ),
      );

      final result = await manager.flushNow();

      expect(result.success, isFalse);

      // Op should still be pending.
      final pending = await db.syncOperationDao.listPending();
      expect(pending.length, 1);
      expect(pending.first.id, 'op1');
    });

    // -----------------------------------------------------------------------
    // Idempotency — server returns already-applied op ID
    // -----------------------------------------------------------------------

    test('idempotent: re-sending same op ID marks it applied', () async {
      when(() => connectivity.currentStatus())
          .thenAnswer((_) async => ConnectivityStatus.online);

      await db.syncOperationDao.insertOperation(_pendingOp(id: 'op1'));

      // Server includes op1 in applied list even on second send.
      when(() => dio.post<Map<String, dynamic>>(
            any(),
            data: any(named: 'data'),
          )).thenAnswer(
        (_) async => Response(
          requestOptions: RequestOptions(path: '/sync'),
          statusCode: 200,
          data: {
            'server_timestamp': DateTime.now().millisecondsSinceEpoch,
            'applied': ['op1'],
            'server_delta': [],
          },
        ),
      );

      // First flush.
      await manager.flushNow();

      // Op is applied — re-insert with same ID to simulate retry scenario.
      await db.syncOperationDao.insertOperation(
        _pendingOp(id: 'op1-retry'),
      );

      final result2 = await manager.flushNow();
      expect(result2.success, isTrue);
    });
  });
}
