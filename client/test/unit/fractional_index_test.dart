import 'package:flutter_test/flutter_test.dart';

import 'package:notes/core/utils/fractional_index.dart';

void main() {
  group('FractionalIndex', () {
    // -----------------------------------------------------------------------
    // first()
    // -----------------------------------------------------------------------

    test('first() returns non-empty string', () {
      expect(FractionalIndex.first(), isNotEmpty);
    });

    // -----------------------------------------------------------------------
    // after()
    // -----------------------------------------------------------------------

    test('after(a) sorts after a', () {
      const a = 'n';
      final result = FractionalIndex.after(a);
      expect(result.compareTo(a), greaterThan(0));
    });

    test('after() on empty string throws', () {
      expect(() => FractionalIndex.after(''), throwsArgumentError);
    });

    test('chained after() maintains order', () {
      var pos = FractionalIndex.first();
      String? prev;
      for (int i = 0; i < 50; i++) {
        if (prev != null) {
          expect(pos.compareTo(prev), greaterThan(0));
        }
        prev = pos;
        pos = FractionalIndex.after(pos);
      }
    });

    // -----------------------------------------------------------------------
    // before()
    // -----------------------------------------------------------------------

    test('before(a) sorts before a', () {
      const a = 'n';
      final result = FractionalIndex.before(a);
      expect(result.compareTo(a), lessThan(0));
    });

    test('before() on empty string throws', () {
      expect(() => FractionalIndex.before(''), throwsArgumentError);
    });

    test('chained before() maintains order', () {
      var pos = FractionalIndex.first();
      String? next;
      for (int i = 0; i < 10; i++) {
        if (next != null) {
          expect(pos.compareTo(next), lessThan(0));
        }
        next = pos;
        pos = FractionalIndex.before(pos);
      }
    });

    // -----------------------------------------------------------------------
    // between()
    // -----------------------------------------------------------------------

    test('between(a, b) sorts strictly between a and b', () {
      const a = 'a';
      const b = 'z';
      final mid = FractionalIndex.between(a, b);
      expect(mid.compareTo(a), greaterThan(0));
      expect(mid.compareTo(b), lessThan(0));
    });

    test('between adjacent chars produces longer string', () {
      // 'a' and 'b' are adjacent in alphabet — must go deeper.
      const a = 'a';
      const b = 'b';
      final mid = FractionalIndex.between(a, b);
      expect(mid.compareTo(a), greaterThan(0));
      expect(mid.compareTo(b), lessThan(0));
    });

    test('between() throws when a >= b', () {
      expect(
        () => FractionalIndex.between('b', 'a'),
        throwsArgumentError,
      );
    });

    test('between() throws on empty strings', () {
      expect(() => FractionalIndex.between('', 'z'), throwsArgumentError);
      expect(() => FractionalIndex.between('a', ''), throwsArgumentError);
    });

    // -----------------------------------------------------------------------
    // 1000-insert ordering test
    // -----------------------------------------------------------------------

    test('1000 sequential after() inserts maintain strict order', () {
      final positions = <String>[];
      var pos = FractionalIndex.first();
      positions.add(pos);

      for (int i = 0; i < 999; i++) {
        pos = FractionalIndex.after(pos);
        positions.add(pos);
      }

      for (int i = 0; i < positions.length - 1; i++) {
        expect(
          positions[i].compareTo(positions[i + 1]),
          lessThan(0),
          reason:
              'positions[$i]="${positions[i]}" should be < positions[${i + 1}]="${positions[i + 1]}"',
        );
      }
    });

    test('1000 between() inserts maintain strict order', () {
      // Insert 1000 items between 'a' and 'z' recursively.
      final positions = <String>['a', 'z'];

      for (int i = 0; i < 998; i++) {
        final idx = i % (positions.length - 1);
        final mid =
            FractionalIndex.between(positions[idx], positions[idx + 1]);
        positions.insert(idx + 1, mid);
      }

      for (int i = 0; i < positions.length - 1; i++) {
        expect(
          positions[i].compareTo(positions[i + 1]),
          lessThan(0),
          reason:
              'positions[$i]="${positions[i]}" should be < positions[${i + 1}]="${positions[i + 1]}"',
        );
      }
    });

    // -----------------------------------------------------------------------
    // Mirror server output
    // -----------------------------------------------------------------------

    test('first() matches server: "n"', () {
      expect(FractionalIndex.first(), 'n');
    });

    test('after("n") matches server: "ni"', () {
      // Python: after('n') = 'n' + _MID_CHAR ('i') = 'ni'
      expect(FractionalIndex.after('n'), 'ni');
    });

    test('between("a", "z") matches server midpoint', () {
      // 'a' is at index 10, 'z' at 35 in the base-36 alphabet.
      // Python mid = (10 + 35) // 2 = 22 = _chars[22] = 'm'
      expect(FractionalIndex.between('a', 'z'), 'm');
    });

    test('before("n") matches server', () {
      // 'n' chars: ['n'], index 23. Decrement → index 22 = 'm'.
      expect(FractionalIndex.before('n'), 'm');
    });
  });
}
