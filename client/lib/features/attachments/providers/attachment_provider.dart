import 'dart:io';

import 'package:dio/dio.dart';
import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../../core/api/api_client.dart';
import '../../../core/db/app_database.dart';
import '../../../core/db/database_provider.dart';

const _uuid = Uuid();

// ---------------------------------------------------------------------------
// Model
// ---------------------------------------------------------------------------

class AttachmentModel {
  const AttachmentModel({
    required this.id,
    required this.bulletId,
    required this.filename,
    required this.mimeType,
    this.localPath,
    required this.size,
    required this.createdAt,
  });

  final String id;
  final String bulletId;
  final String filename;
  final String mimeType;
  final String? localPath;
  final int size;
  final int createdAt;

  bool get isImage => mimeType.startsWith('image/');
  bool get isAudio => mimeType.startsWith('audio/');

  factory AttachmentModel.fromData(AttachmentsTableData d) => AttachmentModel(
        id: d.id,
        bulletId: d.bulletId,
        filename: d.filename,
        mimeType: d.mimeType,
        localPath: d.localPath,
        size: d.size,
        createdAt: d.createdAt,
      );
}

// ---------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------

class AttachmentRepository {
  AttachmentRepository(this._db, this._dio);

  final AppDatabase _db;
  final Dio? _dio;

  AttachmentDao get _dao => _db.attachmentDao;

  /// Upload [filePath] to the server then store metadata locally.
  /// Returns the stored [AttachmentModel].
  Future<AttachmentModel> uploadAttachment({
    required String bulletId,
    required String filePath,
    required String filename,
    required String mimeType,
  }) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    String id = _uuid.v4();
    int size = 0;
    final String storedLocalPath = filePath;

    try {
      final f = File(filePath);
      if (f.existsSync()) {
        size = await f.length();
      }
    } catch (_) {}

    // Try server upload if dio is available.
    if (_dio != null) {
      try {
        final formData = FormData.fromMap({
          'file': await MultipartFile.fromFile(filePath, filename: filename),
          'bullet_id': bulletId,
        });
        final response = await _dio.post<Map<String, dynamic>>(
          '/attachments',
          data: formData,
        );
        // Use server-assigned ID if returned.
        if (response.data != null && response.data!['id'] != null) {
          id = response.data!['id'] as String;
        }
      } catch (_) {
        // Offline — keep local path, sync later.
      }
    }

    final companion = AttachmentsTableCompanion.insert(
      id: id,
      bulletId: bulletId,
      filename: filename,
      mimeType: mimeType,
      localPath: Value(storedLocalPath),
      size: Value(size),
      createdAt: now,
    );
    await _dao.insertAttachment(companion);

    return AttachmentModel(
      id: id,
      bulletId: bulletId,
      filename: filename,
      mimeType: mimeType,
      localPath: storedLocalPath,
      size: size,
      createdAt: now,
    );
  }

  /// Soft-delete locally; attempt server delete.
  Future<void> deleteAttachment(String id) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    await _dao.softDelete(id, now);

    if (_dio != null) {
      try {
        await _dio.delete<void>('/attachments/$id');
      } catch (_) {}
    }
  }

  Future<List<AttachmentModel>> listForBullet(String bulletId) async {
    final rows = await _dao.listForBullet(bulletId);
    return rows.map(AttachmentModel.fromData).toList();
  }

  Stream<List<AttachmentModel>> watchForBullet(String bulletId) {
    return _dao
        .watchForBullet(bulletId)
        .map((rows) => rows.map(AttachmentModel.fromData).toList());
  }
}

// ---------------------------------------------------------------------------
// Providers
// ---------------------------------------------------------------------------

final attachmentRepositoryProvider = Provider<AttachmentRepository>((ref) {
  final db = ref.watch(databaseProvider);
  final dio = ref.watch(apiClientProvider);
  return AttachmentRepository(db, dio);
});

final attachmentsForBulletProvider =
    StreamProvider.family<List<AttachmentModel>, String>((ref, bulletId) {
  return ref.watch(attachmentRepositoryProvider).watchForBullet(bulletId);
});
