import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

/// A lightweight inline Markdown tokenizer.
///
/// Supported syntax:
/// - `**bold**`
/// - `*italic*`
/// - `` `code` ``
/// - `[text](url)`
/// - `#tag` (word boundary, tag chars: letters, digits, hyphens, underscores)
///
/// Raw syntax is shown when the cursor is inside a token span;
/// formatted output is shown otherwise.
library markdown_parser;

// ---------------------------------------------------------------------------
// Token types
// ---------------------------------------------------------------------------

enum TokenType { plain, bold, italic, code, link, tag }

class MarkdownToken {
  const MarkdownToken({
    required this.type,
    required this.rawText,
    required this.displayText,
    required this.start,
    required this.end,
    this.url,
  });

  final TokenType type;

  /// The raw source text (including delimiters).
  final String rawText;

  /// The formatted display text (without delimiters).
  final String displayText;

  /// Character offset in the original string where this token starts.
  final int start;

  /// Character offset (exclusive) where this token ends.
  final int end;

  /// Only set for [TokenType.link].
  final String? url;
}

// ---------------------------------------------------------------------------
// Parser
// ---------------------------------------------------------------------------

class MarkdownParser {
  MarkdownParser._();

  static final RegExp _bold = RegExp(r'\*\*(.+?)\*\*');
  static final RegExp _italic = RegExp(r'(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)');
  static final RegExp _code = RegExp(r'`([^`]+)`');
  static final RegExp _link = RegExp(r'\[([^\]]+)\]\(([^)]+)\)');
  static final RegExp _tag = RegExp(r'(?<![&\w])#([\w-]+)');

  /// Parse [text] into tokens.  Tokens are non-overlapping and cover the
  /// entire input (plain tokens fill gaps).
  static List<MarkdownToken> parse(String text) {
    // Collect all matches with their positions.
    final matches = <_RawMatch>[];

    for (final m in _bold.allMatches(text)) {
      matches.add(_RawMatch(
        type: TokenType.bold,
        raw: m.group(0)!,
        display: m.group(1)!,
        start: m.start,
        end: m.end,
      ));
    }
    for (final m in _italic.allMatches(text)) {
      matches.add(_RawMatch(
        type: TokenType.italic,
        raw: m.group(0)!,
        display: m.group(1)!,
        start: m.start,
        end: m.end,
      ));
    }
    for (final m in _code.allMatches(text)) {
      matches.add(_RawMatch(
        type: TokenType.code,
        raw: m.group(0)!,
        display: m.group(1)!,
        start: m.start,
        end: m.end,
      ));
    }
    for (final m in _link.allMatches(text)) {
      matches.add(_RawMatch(
        type: TokenType.link,
        raw: m.group(0)!,
        display: m.group(1)!,
        url: m.group(2)!,
        start: m.start,
        end: m.end,
      ));
    }
    for (final m in _tag.allMatches(text)) {
      matches.add(_RawMatch(
        type: TokenType.tag,
        raw: m.group(0)!,
        display: m.group(0)!,
        start: m.start,
        end: m.end,
      ));
    }

    // Sort by start position and resolve overlaps (earlier + longer wins).
    matches.sort((a, b) {
      final cmp = a.start.compareTo(b.start);
      if (cmp != 0) return cmp;
      return b.end.compareTo(a.end); // longer span first
    });

    final resolved = <_RawMatch>[];
    int cursor = 0;
    for (final m in matches) {
      if (m.start < cursor) continue; // overlaps previous — skip
      resolved.add(m);
      cursor = m.end;
    }

    // Build final token list, inserting plain tokens for gaps.
    final tokens = <MarkdownToken>[];
    int pos = 0;

    for (final m in resolved) {
      if (m.start > pos) {
        // Gap — plain text.
        tokens.add(MarkdownToken(
          type: TokenType.plain,
          rawText: text.substring(pos, m.start),
          displayText: text.substring(pos, m.start),
          start: pos,
          end: m.start,
        ));
      }
      tokens.add(MarkdownToken(
        type: m.type,
        rawText: m.raw,
        displayText: m.display,
        start: m.start,
        end: m.end,
        url: m.url,
      ));
      pos = m.end;
    }

    if (pos < text.length) {
      tokens.add(MarkdownToken(
        type: TokenType.plain,
        rawText: text.substring(pos),
        displayText: text.substring(pos),
        start: pos,
        end: text.length,
      ));
    }

    return tokens;
  }

  /// Build a [TextSpan] list for displaying [text] with formatting.
  ///
  /// If [cursorPosition] is provided, tokens that contain the cursor position
  /// are shown as raw syntax instead of formatted output.
  static List<InlineSpan> buildSpans(
    String text, {
    int? cursorPosition,
    TextStyle? baseStyle,
    VoidCallback? onTagTap,
  }) {
    final tokens = parse(text);
    final spans = <InlineSpan>[];

    for (final token in tokens) {
      final cursorInside = cursorPosition != null &&
          cursorPosition >= token.start &&
          cursorPosition <= token.end;

      // If cursor is inside this token, show raw syntax.
      final showRaw = cursorInside && token.type != TokenType.plain;

      if (showRaw) {
        spans.add(TextSpan(
          text: token.rawText,
          style: baseStyle,
        ));
        continue;
      }

      switch (token.type) {
        case TokenType.plain:
          spans.add(TextSpan(text: token.displayText, style: baseStyle));
        case TokenType.bold:
          spans.add(TextSpan(
            text: token.displayText,
            style: (baseStyle ?? const TextStyle()).copyWith(
              fontWeight: FontWeight.bold,
            ),
          ));
        case TokenType.italic:
          spans.add(TextSpan(
            text: token.displayText,
            style: (baseStyle ?? const TextStyle()).copyWith(
              fontStyle: FontStyle.italic,
            ),
          ));
        case TokenType.code:
          spans.add(TextSpan(
            text: token.displayText,
            style: (baseStyle ?? const TextStyle()).copyWith(
              fontFamily: 'monospace',
              backgroundColor: Colors.grey.shade200,
            ),
          ));
        case TokenType.link:
          spans.add(TextSpan(
            text: token.displayText,
            style: (baseStyle ?? const TextStyle()).copyWith(
              color: Colors.blue,
              decoration: TextDecoration.underline,
            ),
          ));
        case TokenType.tag:
          spans.add(TextSpan(
            text: token.displayText,
            style: (baseStyle ?? const TextStyle()).copyWith(
              color: Colors.deepPurple,
            ),
            recognizer: onTagTap != null
                ? (TapGestureRecognizer()..onTap = onTagTap)
                : null,
          ));
      }
    }

    return spans;
  }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

class _RawMatch {
  const _RawMatch({
    required this.type,
    required this.raw,
    required this.display,
    required this.start,
    required this.end,
    this.url,
  });

  final TokenType type;
  final String raw;
  final String display;
  final int start;
  final int end;
  final String? url;
}
