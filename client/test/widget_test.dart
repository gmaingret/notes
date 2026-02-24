import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:notes/app.dart';

void main() {
  testWidgets('App renders without crashing', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: NotesApp()));
    // The app should render the login screen on first launch.
    expect(find.text('Login — Phase 1'), findsOneWidget);
  });
}
