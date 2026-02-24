import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/utils/markdown_parser.dart';
import '../repositories/bullet_repository.dart';

// ---------------------------------------------------------------------------
// BulletEditor
// ---------------------------------------------------------------------------

/// A rich text field that renders live Markdown formatting.
///
/// Keyboard behaviour:
/// - Enter → create sibling bullet below (callback)
/// - Tab → indent bullet (callback)
/// - Shift+Tab → outdent bullet (callback)
/// - Ctrl+Z → undo (web — via HardwareKeyboard)
/// - Ctrl+Y → redo (web — via HardwareKeyboard)
class BulletEditor extends ConsumerStatefulWidget {
  const BulletEditor({
    super.key,
    required this.bulletId,
    required this.documentId,
    required this.initialContent,
    this.onEnter,
    this.onTab,
    this.onShiftTab,
    this.focusNode,
  });

  final String bulletId;
  final String documentId;
  final String initialContent;

  /// Called when Enter is pressed (create sibling).
  final VoidCallback? onEnter;

  /// Called when Tab is pressed (indent).
  final VoidCallback? onTab;

  /// Called when Shift+Tab is pressed (outdent).
  final VoidCallback? onShiftTab;

  final FocusNode? focusNode;

  @override
  ConsumerState<BulletEditor> createState() => _BulletEditorState();
}

class _BulletEditorState extends ConsumerState<BulletEditor> {
  late final _MarkdownTextController _controller;
  late final FocusNode _focusNode;
  late final FocusNode _keyboardListenerNode;

  // Simple undo/redo stacks.
  final _undoStack = <String>[];
  final _redoStack = <String>[];

  @override
  void initState() {
    super.initState();
    _focusNode = widget.focusNode ?? FocusNode();
    _keyboardListenerNode = FocusNode(debugLabel: 'BulletEditorKeyboard');
    _controller = _MarkdownTextController(widget.initialContent);
    _undoStack.add(widget.initialContent);
  }

  @override
  void dispose() {
    _controller.dispose();
    _keyboardListenerNode.dispose();
    if (widget.focusNode == null) _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return KeyboardListener(
      focusNode: _keyboardListenerNode,
      onKeyEvent: _handleKey,
      child: TextField(
        controller: _controller,
        focusNode: _focusNode,
        decoration: const InputDecoration(
          border: InputBorder.none,
          isDense: true,
          contentPadding: EdgeInsets.symmetric(vertical: 10),
        ),
        maxLines: null,
        onChanged: _onChanged,
      ),
    );
  }

  void _onChanged(String value) {
    _undoStack.add(value);
    _redoStack.clear();

    // Write to local DB — SyncManager debounces the server push.
    ref.read(bulletRepositoryProvider).updateBullet(
          id: widget.bulletId,
          documentId: widget.documentId,
          content: value,
        );
  }

  void _handleKey(KeyEvent event) {
    if (event is! KeyDownEvent) return;

    final key = event.logicalKey;
    final shift = HardwareKeyboard.instance.isShiftPressed;
    final ctrl = HardwareKeyboard.instance.isControlPressed ||
        HardwareKeyboard.instance.isMetaPressed;

    if (key == LogicalKeyboardKey.enter && !shift) {
      widget.onEnter?.call();
    } else if (key == LogicalKeyboardKey.tab) {
      if (shift) {
        widget.onShiftTab?.call();
      } else {
        widget.onTab?.call();
      }
    } else if (ctrl && key == LogicalKeyboardKey.keyZ) {
      _undo();
    } else if (ctrl && key == LogicalKeyboardKey.keyY) {
      _redo();
    }
  }

  void _undo() {
    if (_undoStack.length <= 1) return;
    final current = _undoStack.removeLast();
    _redoStack.add(current);
    final prev = _undoStack.last;
    _controller.text = prev;
    ref.read(bulletRepositoryProvider).updateBullet(
          id: widget.bulletId,
          documentId: widget.documentId,
          content: prev,
        );
  }

  void _redo() {
    if (_redoStack.isEmpty) return;
    final next = _redoStack.removeLast();
    _undoStack.add(next);
    _controller.text = next;
    ref.read(bulletRepositoryProvider).updateBullet(
          id: widget.bulletId,
          documentId: widget.documentId,
          content: next,
        );
  }
}

// ---------------------------------------------------------------------------
// Custom TextEditingController with Markdown rendering
// ---------------------------------------------------------------------------

class _MarkdownTextController extends TextEditingController {
  _MarkdownTextController(String initialText) : super(text: initialText);

  @override
  TextSpan buildTextSpan({
    required BuildContext context,
    TextStyle? style,
    required bool withComposing,
  }) {
    final cursorPos = selection.isValid ? selection.baseOffset : null;

    final spans = MarkdownParser.buildSpans(
      text,
      cursorPosition: cursorPos,
      baseStyle: style,
    );

    return TextSpan(style: style, children: spans);
  }
}
