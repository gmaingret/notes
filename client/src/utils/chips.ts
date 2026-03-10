export function renderWithChips(html: string): string {
  return html
    .replace(
      /(?<![="\/&])#([a-zA-Z0-9_]+)/g,
      '<span class="chip chip-tag" data-chip-type="tag" data-chip-value="$1">#$1</span>'
    )
    .replace(
      /(?<![="/])@([a-zA-Z0-9_]+)/g,
      '<span class="chip chip-mention" data-chip-type="mention" data-chip-value="$1">@$1</span>'
    )
    .replace(
      /!!\[(\d{4}-\d{2}-\d{2})\]/g,
      '<span class="chip chip-date" data-chip-type="date" data-chip-value="$1">\uD83D\uDCC5 $1</span>'
    );
}
