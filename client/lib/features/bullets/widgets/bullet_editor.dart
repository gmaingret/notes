import 'dart:async';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/utils/markdown_parser.dart';
import '../providers/bullet_tree_provider.dart';
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
/// - Ctrl+Z → undo
/// - Ctrl+Y → redo
///
/// Web-only keyboard shortcuts:
/// - Ctrl+[ → collapse current node (callback)
/// - Ctrl+] → expand current node (callback)
/// - ArrowUp → move focus to previous bullet
/// - ArrowDown → move focus to next bullet
/// - Backspace on empty content → delete bullet (callback)
class BulletEditor extends ConsumerStatefulWidget {
  const BulletEditor({
    super.key,
    required this.bulletId,
    required this.documentId,
    required this.initialContent,
    this.isComplete = false,
    this.onEnter,
    this.onTab,
    this.onShiftTab,
    this.onCollapse,
    this.onExpand,
    this.onDeleteEmpty,
    this.focusNode,
  });

  final String bulletId;
  final String documentId;
  final String initialContent;

  /// When true the text is rendered with a strikethrough decoration.
  final bool isComplete;

  /// Called when Enter is pressed (create sibling).
  final VoidCallback? onEnter;

  /// Called when Tab is pressed (indent).
  final VoidCallback? onTab;

  /// Called when Shift+Tab is pressed (outdent).
  final VoidCallback? onShiftTab;

  /// Called when Ctrl+[ is pressed on web (collapse node).
  final VoidCallback? onCollapse;

  /// Called when Ctrl+] is pressed on web (expand node).
  final VoidCallback? onExpand;

  /// Called when Backspace is pressed on web with empty content (delete bullet).
  final VoidCallback? onDeleteEmpty;

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
    // skipTraversal: true so Tab/arrow traversal skips this wrapper node and
    // lands directly on the TextField's focus node (_focusNode).
    _keyboardListenerNode = FocusNode(
      debugLabel: 'BulletEditorKeyboard',
      skipTraversal: true,
    );
    _controller = _MarkdownTextController(widget.initialContent);
    _undoStack.add(widget.initialContent);

    // Case A: pendingFocusBulletId was set before this widget was built.
    // Check state after the first frame so the TextField is fully laid out.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      ref
          .read(bulletTreeNotifierProvider(widget.documentId))
          .whenData((treeState) {
        if (treeState.pendingFocusBulletId == widget.bulletId) {
          _focusNode.requestFocus();
          ref
              .read(bulletTreeNotifierProvider(widget.documentId).notifier)
              .clearPendingFocus();
        }
      });
    });
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
    // Case B: pendingFocusBulletId was set after this widget was already built.
    ref.listen(bulletTreeNotifierProvider(widget.documentId), (_, next) {
      next.whenData((treeState) {
        if (treeState.pendingFocusBulletId == widget.bulletId) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) {
              _focusNode.requestFocus();
              ref
                  .read(bulletTreeNotifierProvider(widget.documentId).notifier)
                  .clearPendingFocus();
            }
          });
        }
      });
    });

    return KeyboardListener(
      focusNode: _keyboardListenerNode,
      onKeyEvent: _handleKey,
      child: TextField(
        controller: _controller,
        focusNode: _focusNode,
        style: widget.isComplete
            ? const TextStyle(decoration: TextDecoration.lineThrough)
            : null,
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
    // Enter key inserts a newline into the text. Intercept it here so it works
    // reliably on all platforms (web, mobile, desktop) without relying solely
    // on KeyboardListener, which can miss events on Flutter web.
    if (value.contains('\n')) {
      final stripped = value.replaceAll('\n', '');
      _controller.value = _controller.value.copyWith(
        text: stripped,
        selection: TextSelection.collapsed(offset: stripped.length),
      );
      widget.onEnter?.call();
      return;
    }

    _undoStack.add(value);
    _redoStack.clear();

    // Write to local DB — fire-and-forget; SyncManager debounces server push.
    unawaited(
      ref.read(bulletRepositoryProvider).updateBullet(
            id: widget.bulletId,
            documentId: widget.documentId,
            content: value,
          ),
    );
  }

  void _handleKey(KeyEvent event) {
    if (event is! KeyDownEvent) return;

    final key = event.logicalKey;
    final shift = HardwareKeyboard.instance.isShiftPressed;
    final ctrl = HardwareKeyboard.instance.isControlPressed ||
        HardwareKeyboard.instance.isMetaPressed;

    // Enter is handled in _onChanged (strips the newline and fires onEnter).
    if (key == LogicalKeyboardKey.tab) {
      if (shift) {
        widget.onShiftTab?.call();
      } else {
        widget.onTab?.call();
      }
    } else if (ctrl && key == LogicalKeyboardKey.keyZ) {
      _undo();
    } else if (ctrl && key == LogicalKeyboardKey.keyY) {
      _redo();
    } else if (kIsWeb) {
      _handleWebKey(key, ctrl);
    }
  }

  void _handleWebKey(LogicalKeyboardKey key, bool ctrl) {
    if (ctrl && key == LogicalKeyboardKey.bracketLeft) {
      widget.onCollapse?.call();
    } else if (ctrl && key == LogicalKeyboardKey.bracketRight) {
      widget.onExpand?.call();
    } else if (key == LogicalKeyboardKey.arrowUp) {
      // Only move focus to the previous bullet when the cursor is already
      // at the very start of this bullet's text.
      if (_controller.selection.isValid &&
          _controller.selection.baseOffset == 0) {
        FocusScope.of(context).previousFocus();
      }
    } else if (key == LogicalKeyboardKey.arrowDown) {
      // Only move focus to the next bullet when the cursor is at the end.
      if (_controller.selection.isValid &&
          _controller.selection.baseOffset == _controller.text.length) {
        FocusScope.of(context).nextFocus();
      }
    } else if (key == LogicalKeyboardKey.backspace &&
        _controller.text.isEmpty) {
      widget.onDeleteEmpty?.call();
    }
  }

  void _undo() {
    if (_undoStack.length <= 1) return;
    final current = _undoStack.removeLast();
    _redoStack.add(current);
    final prev = _undoStack.last;
    _controller.text = prev;
    unawaited(
      ref.read(bulletRepositoryProvider).updateBullet(
            id: widget.bulletId,
            documentId: widget.documentId,
            content: prev,
          ),
    );
  }

  void _redo() {
    if (_redoStack.isEmpty) return;
    final next = _redoStack.removeLast();
    _undoStack.add(next);
    _controller.text = next;
    unawaited(
      ref.read(bulletRepositoryProvider).updateBullet(
            id: widget.bulletId,
            documentId: widget.documentId,
            content: next,
          ),
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
