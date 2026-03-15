/**
 * Escape PostgreSQL ILIKE metacharacters in user-provided input.
 *
 * PostgreSQL ILIKE uses `%` (any string) and `_` (any single char) as wildcards.
 * Backslash is the default escape character, so `\%` and `\_` match literals.
 *
 * Apply this to any user-controlled value before interpolating it into an
 * ILIKE pattern, so that `%` and `_` in the input are treated as literals
 * rather than wildcards.
 */
export function escapeIlike(input: string): string {
  return input.replace(/%/g, '\\%').replace(/_/g, '\\_');
}
