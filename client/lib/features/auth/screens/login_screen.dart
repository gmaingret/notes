import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../providers/auth_provider.dart';

class LoginScreen extends ConsumerWidget {
  const LoginScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authNotifierProvider);
    final isLoading = authState.status == AuthStatus.unknown;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.notes, size: 72, color: Colors.indigo),
                const SizedBox(height: 24),
                Text(
                  'Notes',
                  style: Theme.of(context).textTheme.headlineLarge,
                ),
                const SizedBox(height: 8),
                Text(
                  'Your personal outliner',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 48),
                if (isLoading)
                  const CircularProgressIndicator()
                else
                  _GoogleSignInButton(
                    onPressed: () async {
                      await ref
                          .read(authNotifierProvider.notifier)
                          .signInWithGoogle();
                      if (context.mounted) {
                        final status =
                            ref.read(authNotifierProvider).status;
                        if (status == AuthStatus.authenticated) {
                          context.go('/documents');
                        }
                      }
                    },
                  ),
                if (authState.errorMessage != null) ...[
                  const SizedBox(height: 16),
                  Text(
                    authState.errorMessage!,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _GoogleSignInButton extends StatelessWidget {
  const _GoogleSignInButton({required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      key: const Key('google_sign_in_button'),
      onPressed: onPressed,
      icon: const Icon(Icons.login),
      label: const Text('Sign in with Google'),
      style: OutlinedButton.styleFrom(
        minimumSize: const Size.fromHeight(48),
      ),
    );
  }
}
