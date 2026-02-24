import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/db/database_provider.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final db = await openAppDatabase();

  runApp(
    ProviderScope(
      overrides: [databaseProvider.overrideWithValue(db)],
      child: const NotesApp(),
    ),
  );
}
