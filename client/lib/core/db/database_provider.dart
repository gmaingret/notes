import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqlite3_flutter_libs/sqlite3_flutter_libs.dart';

import 'app_database.dart';

/// Opens the production database backed by a file on disk.
/// For web, drift uses the sqlite3 WASM + OPFS backend automatically
/// when the sqlite3 package is initialised with the WASM executor.
Future<AppDatabase> openAppDatabase() async {
  if (kIsWeb) {
    // Web: use in-memory for now; OPFS wiring is done separately.
    return AppDatabase(NativeDatabase.memory());
  }

  // Android/desktop: use a file-backed database.
  await applyWorkaroundToOpenSqlite3OnOldAndroidVersions();

  final dbFolder = await getApplicationDocumentsDirectory();
  final file = File(p.join(dbFolder.path, 'notes.db'));

  return AppDatabase(NativeDatabase(file));
}

final databaseProvider = Provider<AppDatabase>((ref) {
  throw UnimplementedError(
    'AppDatabase must be overridden in ProviderScope.overrides. '
    'Call openAppDatabase() at startup and override this provider.',
  );
});
