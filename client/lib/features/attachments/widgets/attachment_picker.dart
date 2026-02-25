import 'dart:async';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

import '../providers/attachment_provider.dart';

/// Shows the attachment picker bottom sheet and handles upload.
Future<void> showAttachmentPicker(
  BuildContext context,
  WidgetRef ref, {
  required String bulletId,
}) {
  return showModalBottomSheet<void>(
    context: context,
    builder: (_) => _AttachmentPickerSheet(
      bulletId: bulletId,
      ref: ref,
    ),
  );
}

class _AttachmentPickerSheet extends StatefulWidget {
  const _AttachmentPickerSheet({
    required this.bulletId,
    required this.ref,
  });

  final String bulletId;
  final WidgetRef ref;

  @override
  State<_AttachmentPickerSheet> createState() => _AttachmentPickerSheetState();
}

class _AttachmentPickerSheetState extends State<_AttachmentPickerSheet> {
  bool _isRecording = false;
  AudioRecorder? _recorder;

  @override
  void dispose() {
    _recorder?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            key: const Key('attach_camera'),
            leading: const Icon(Icons.camera_alt),
            title: const Text('Camera'),
            onTap: () => _pickFromCamera(context),
          ),
          ListTile(
            key: const Key('attach_gallery'),
            leading: const Icon(Icons.photo_library),
            title: const Text('Gallery'),
            onTap: () => _pickFromGallery(context),
          ),
          ListTile(
            key: const Key('attach_file'),
            leading: const Icon(Icons.attach_file),
            title: const Text('File'),
            onTap: () => _pickFile(context),
          ),
          ListTile(
            key: const Key('attach_audio'),
            leading: Icon(_isRecording ? Icons.stop : Icons.mic),
            title: Text(_isRecording ? 'Stop recording' : 'Record audio'),
            onTap: () => _toggleAudioRecord(context),
          ),
        ],
      ),
    );
  }

  Future<void> _pickFromCamera(BuildContext context) async {
    final picker = ImagePicker();
    final XFile? file = await picker.pickImage(source: ImageSource.camera);
    if (file == null) return;
    if (!context.mounted) return;
    await _upload(
      context,
      path: file.path,
      filename: file.name,
      mimeType: 'image/jpeg',
    );
  }

  Future<void> _pickFromGallery(BuildContext context) async {
    final picker = ImagePicker();
    final XFile? file = await picker.pickImage(source: ImageSource.gallery);
    if (file == null) return;
    if (!context.mounted) return;
    await _upload(
      context,
      path: file.path,
      filename: file.name,
      mimeType: 'image/jpeg',
    );
  }

  Future<void> _pickFile(BuildContext context) async {
    final result = await FilePicker.platform.pickFiles();
    if (result == null || result.files.isEmpty) return;
    final f = result.files.first;
    if (f.path == null) return;
    if (!context.mounted) return;
    await _upload(
      context,
      path: f.path!,
      filename: f.name,
      mimeType: 'application/octet-stream',
    );
  }

  Future<void> _toggleAudioRecord(BuildContext context) async {
    if (_isRecording) {
      final path = await _recorder?.stop();
      _recorder = null;
      setState(() => _isRecording = false);
      if (path != null && context.mounted) {
        await _upload(
          context,
          path: path,
          filename: 'recording_${DateTime.now().millisecondsSinceEpoch}.aac',
          mimeType: 'audio/aac',
        );
      }
      return;
    }

    final recorder = AudioRecorder();
    _recorder = recorder;
    final hasPermission = await recorder.hasPermission();
    if (!hasPermission) {
      recorder.dispose();
      _recorder = null;
      return;
    }

    final dir = await getTemporaryDirectory();
    final path =
        '${dir.path}/rec_${DateTime.now().millisecondsSinceEpoch}.aac';

    await recorder.start(
      const RecordConfig(encoder: AudioEncoder.aacLc),
      path: path,
    );
    setState(() => _isRecording = true);
  }

  Future<void> _upload(
    BuildContext context, {
    required String path,
    required String filename,
    required String mimeType,
  }) async {
    Navigator.of(context).pop();
    await widget.ref.read(attachmentRepositoryProvider).uploadAttachment(
          bulletId: widget.bulletId,
          filePath: path,
          filename: filename,
          mimeType: mimeType,
        );
  }
}
