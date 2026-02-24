import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:notes/features/auth/screens/login_screen.dart';

void main() {
  testWidgets('LoginScreen renders without crashing', (WidgetTester tester) async {
    await tester.pumpWidget(const MaterialApp(home: LoginScreen()));
    expect(find.text('Login — Phase 1'), findsOneWidget);
  });
}
