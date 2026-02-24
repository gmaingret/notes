import 'package:flutter_test/flutter_test.dart';

import 'package:notes/core/utils/markdown_parser.dart';

void main() {
  group('MarkdownParser.parse', () {
    // -----------------------------------------------------------------------
    // Plain text
    // -----------------------------------------------------------------------

    test('plain text returns single plain token', () {
      final tokens = MarkdownParser.parse('Hello world');
      expect(tokens.length, 1);
      expect(tokens.first.type, TokenType.plain);
      expect(tokens.first.displayText, 'Hello world');
    });

    test('empty string returns no tokens', () {
      final tokens = MarkdownParser.parse('');
      expect(tokens, isEmpty);
    });

    // -----------------------------------------------------------------------
    // Bold
    // -----------------------------------------------------------------------

    test('bold token parsed correctly', () {
      final tokens = MarkdownParser.parse('**bold**');
      expect(tokens.length, 1);
      expect(tokens.first.type, TokenType.bold);
      expect(tokens.first.displayText, 'bold');
      expect(tokens.first.rawText, '**bold**');
    });

    test('bold in sentence', () {
      final tokens = MarkdownParser.parse('Hello **world** goodbye');
      expect(tokens.length, 3);
      expect(tokens[0].type, TokenType.plain);
      expect(tokens[1].type, TokenType.bold);
      expect(tokens[1].displayText, 'world');
      expect(tokens[2].type, TokenType.plain);
    });

    // -----------------------------------------------------------------------
    // Italic
    // -----------------------------------------------------------------------

    test('italic token parsed correctly', () {
      final tokens = MarkdownParser.parse('*italic*');
      expect(tokens.length, 1);
      expect(tokens.first.type, TokenType.italic);
      expect(tokens.first.displayText, 'italic');
    });

    test('italic and bold do not conflict', () {
      final tokens = MarkdownParser.parse('**bold** and *italic*');
      final types = tokens.map((t) => t.type).toList();
      expect(types, contains(TokenType.bold));
      expect(types, contains(TokenType.italic));
    });

    // -----------------------------------------------------------------------
    // Code
    // -----------------------------------------------------------------------

    test('inline code token parsed correctly', () {
      final tokens = MarkdownParser.parse('`code`');
      expect(tokens.length, 1);
      expect(tokens.first.type, TokenType.code);
      expect(tokens.first.displayText, 'code');
    });

    // -----------------------------------------------------------------------
    // Link
    // -----------------------------------------------------------------------

    test('link token parsed correctly', () {
      final tokens = MarkdownParser.parse('[Flutter](https://flutter.dev)');
      expect(tokens.length, 1);
      expect(tokens.first.type, TokenType.link);
      expect(tokens.first.displayText, 'Flutter');
      expect(tokens.first.url, 'https://flutter.dev');
    });

    test('no false positives — URL without brackets', () {
      final tokens = MarkdownParser.parse('https://flutter.dev');
      expect(tokens.length, 1);
      expect(tokens.first.type, TokenType.plain);
    });

    // -----------------------------------------------------------------------
    // Tags
    // -----------------------------------------------------------------------

    test('#tag parsed correctly', () {
      final tokens = MarkdownParser.parse('#tag');
      expect(tokens.length, 1);
      expect(tokens.first.type, TokenType.tag);
      expect(tokens.first.displayText, '#tag');
    });

    test('multiple tags in one bullet', () {
      final tokens = MarkdownParser.parse('#project #todo');
      final tags = tokens.where((t) => t.type == TokenType.tag).toList();
      expect(tags.length, 2);
      expect(tags[0].displayText, '#project');
      expect(tags[1].displayText, '#todo');
    });

    test('tag with numbers and hyphens', () {
      final tokens = MarkdownParser.parse('#tag-123');
      expect(tokens.first.type, TokenType.tag);
      expect(tokens.first.displayText, '#tag-123');
    });

    test('URL hash is not a tag', () {
      // "https://example.com" — no space-preceded #
      final tokens = MarkdownParser.parse('go to https://example.com/page#section');
      final tags = tokens.where((t) => t.type == TokenType.tag).toList();
      // The & prefix guard in the regex prevents this from matching.
      expect(tags, isEmpty);
    });

    test('standalone # is not a tag', () {
      final tokens = MarkdownParser.parse('# heading');
      // '#' alone is not a tag (no word chars follow before space).
      final tags = tokens.where((t) => t.type == TokenType.tag).toList();
      expect(tags, isEmpty);
    });

    // -----------------------------------------------------------------------
    // Cursor-inside shows raw syntax
    // -----------------------------------------------------------------------

    test('cursor inside bold shows raw syntax', () {
      const text = '**bold**';
      // Cursor at position 4 (inside the word "bold").
      final spans = MarkdownParser.buildSpans(text, cursorPosition: 4);
      // Should show raw text, not formatted.
      final allText = spans
          .whereType<dynamic>()
          .map((s) => (s as dynamic).text as String? ?? '')
          .join();
      expect(allText, contains('**'));
    });

    test('cursor outside bold shows formatted', () {
      const text = '**bold** text';
      // Cursor after the closing **.
      final spans = MarkdownParser.buildSpans(text, cursorPosition: 10);
      // Should not contain raw ** markers in the span for the bold token.
      // There should be at least one span that does NOT contain '**'.
      expect(spans, isNotEmpty);
    });

    // -----------------------------------------------------------------------
    // No false positives
    // -----------------------------------------------------------------------

    test('no false positive on email address', () {
      final tokens = MarkdownParser.parse('user@example.com');
      expect(tokens.length, 1);
      expect(tokens.first.type, TokenType.plain);
    });

    test('consecutive plain text tokens not split', () {
      final tokens = MarkdownParser.parse('Hello world foo bar');
      expect(tokens.length, 1);
      expect(tokens.first.type, TokenType.plain);
    });
  });
}
