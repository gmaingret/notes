import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_database.dart';
import 'connection.dart';

Future<AppDatabase> openAppDatabase() async {
  final executor = await openConnection();
  return AppDatabase(executor);
}

final databaseProvider = Provider<AppDatabase>((ref) {
  throw UnimplementedError(
    'AppDatabase must be overridden in ProviderScope.overrides. '
    'Call openAppDatabase() at startup and override this provider.',
  );
});
