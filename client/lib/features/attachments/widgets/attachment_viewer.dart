import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';

import '../providers/attachment_provider.dart';

/// Renders a row of attachment chips/thumbnails below a bullet.
class AttachmentViewer extends ConsumerWidget {
  const AttachmentViewer({super.key, required this.bulletId});

  final String bulletId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final attachmentsAsync = ref.watch(attachmentsForBulletProvider(bulletId));
    return attachmentsAsync.when(
      data: (attachments) {
        if (attachments.isEmpty) return const SizedBox.shrink();
        return Padding(
          padding: const EdgeInsets.only(left: 24, top: 2, bottom: 4),
          child: Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              for (final a in attachments) _buildChip(context, ref, a),
            ],
          ),
        );
      },
      loading: () => const SizedBox.shrink(),
      error: (_, __) => const SizedBox.shrink(),
    );
  }

  Widget _buildChip(
    BuildContext context,
    WidgetRef ref,
    AttachmentModel attachment,
  ) {
    if (attachment.isImage) {
      return _ImageChip(attachment: attachment, ref: ref);
    }
    if (attachment.isAudio) {
      return _AudioChip(attachment: attachment, ref: ref);
    }
    return _FileChip(attachment: attachment, ref: ref);
  }
}

// ---------------------------------------------------------------------------
// Image chip
// ---------------------------------------------------------------------------

class _ImageChip extends StatelessWidget {
  const _ImageChip({required this.attachment, required this.ref});

  final AttachmentModel attachment;
  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    final localPath = attachment.localPath;
    final thumb = localPath != null
        ? Image.file(
            File(localPath),
            width: 64,
            height: 64,
            fit: BoxFit.cover,
            errorBuilder: (_, __, ___) =>
                const Icon(Icons.broken_image, size: 48),
          )
        : const Icon(Icons.image, size: 48);

    return GestureDetector(
      key: Key('attachment_image_${attachment.id}'),
      onTap: () => _showFullScreen(context, localPath),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: SizedBox(width: 64, height: 64, child: thumb),
      ),
    );
  }

  void _showFullScreen(BuildContext context, String? localPath) {
    if (localPath == null) return;
    showDialog<void>(
      context: context,
      builder: (_) => Dialog(
        child: Image.file(File(localPath)),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Audio chip
// ---------------------------------------------------------------------------

class _AudioChip extends StatefulWidget {
  const _AudioChip({required this.attachment, required this.ref});

  final AttachmentModel attachment;
  final WidgetRef ref;

  @override
  State<_AudioChip> createState() => _AudioChipState();
}

class _AudioChipState extends State<_AudioChip> {
  final AudioPlayer _player = AudioPlayer();
  bool _playing = false;

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  Future<void> _toggle() async {
    final localPath = widget.attachment.localPath;
    if (localPath == null) return;
    if (_playing) {
      await _player.pause();
      setState(() => _playing = false);
    } else {
      await _player.setFilePath(localPath);
      await _player.play();
      setState(() => _playing = true);
      _player.playerStateStream.listen((state) {
        if (state.processingState == ProcessingState.completed) {
          if (mounted) setState(() => _playing = false);
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return ActionChip(
      key: Key('attachment_audio_${widget.attachment.id}'),
      avatar: Icon(_playing ? Icons.pause : Icons.play_arrow),
      label: Text(
        widget.attachment.filename,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      onPressed: _toggle,
    );
  }
}

// ---------------------------------------------------------------------------
// File chip
// ---------------------------------------------------------------------------

class _FileChip extends StatelessWidget {
  const _FileChip({required this.attachment, required this.ref});

  final AttachmentModel attachment;
  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    return Chip(
      key: Key('attachment_file_${attachment.id}'),
      avatar: const Icon(Icons.insert_drive_file),
      label: Text(
        attachment.filename,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      deleteIcon: const Icon(Icons.close, size: 14),
      onDeleted: () =>
          ref.read(attachmentRepositoryProvider).deleteAttachment(attachment.id),
    );
  }
}
