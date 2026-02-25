import 'package:drift/drift.dart';
import 'package:drift/wasm.dart';

Future<QueryExecutor> openConnection() async {
  final db = await WasmDatabase.open(
    databaseName: 'notes',
    sqlite3Uri: Uri.parse('sqlite3.wasm'),
    driftWorkerUri: Uri.parse('drift_worker.dart.js'),
  );
  return db.resolvedExecutor;
}
