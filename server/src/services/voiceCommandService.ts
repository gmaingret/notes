import Anthropic from '@anthropic-ai/sdk';
import { db } from '../../db/index.js';
import { documents, bullets } from '../../db/schema.js';
import { eq, asc, and } from 'drizzle-orm';
import { createBullet, getDocumentBullets, softDeleteBullet, markComplete } from './bulletService.js';
import { searchBullets } from './searchService.js';

const anthropic = new Anthropic(); // reads ANTHROPIC_API_KEY from env

export type VoiceCommandResult = {
  success: boolean;
  action: string;
  message: string;
};

const SYSTEM_PROMPT = `You are a voice command parser for a notes/outliner app. Parse the user's spoken command into a structured JSON action.

Available actions:
- add_bullet: Add a new bullet point to a document. Triggered by phrases like "add X to Y", "put X in Y", "X to my Y list". Can optionally specify a parent bullet to nest under (e.g. "in my Perso document, in France bullet, add duplicate proxmox" → adds "duplicate proxmox" as a child of "France" in "Perso").
- mark_complete: Mark a bullet as complete/done. Triggered by "mark X as done", "complete X", "check off X".
- replace_bullet: Replace/rename a bullet's content. Triggered by "replace X by Y", "rename X to Y", "change X to Y". Set "content" to the old text to find and "new_content" to the replacement text.
- delete_bullet: Delete a bullet. Triggered by "delete X from Y", "remove X from Y".
- create_document: Create a new document. Triggered by "create a document called X", "new list X".
- read_document: Read back contents. Triggered by "read my Y", "what's in my Y", "show me Y". ONLY use this when the user explicitly asks to read/show/list contents.
- search: Search across all notes. Triggered by "search for X", "find X".
- unknown: Command not understood.

IMPORTANT: "add X to Y" or "ajoute X à Y" ALWAYS means add_bullet, never read_document.

The user speaks BOTH FRENCH AND ENGLISH. Parse commands in either language. Examples:
- "add meatballs to groceries" → add_bullet, document: "Groceries", content: "Meatballs"
- "ajoute du beurre à ma liste de courses" → add_bullet, document: "Groceries", content: "beurre"
- "dans mon document Perso, dans France, ajoute duplicate proxmox" → add_bullet, document: "Perso", content: "duplicate proxmox", parent: "France"
- "replace butter by cheese" or "remplace beurre par fromage" → replace_bullet
- "delete bread" or "supprime pain" → delete_bullet
- "mark bread as done" or "marque pain comme fait" → mark_complete
- "read my grocery list" or "lis ma liste de courses" → read_document

Match document names flexibly (e.g. "liste de courses"/"groceries"/"grocery list" all match "Groceries").

Respond with ONLY valid JSON (no markdown, no backticks). The "message" field should be in English:
{"action":"add_bullet","document":"Groceries","content":"butter","parent":null,"message":"Added butter to Groceries."}

When the user specifies a parent bullet, set "parent" to the bullet name to nest under:
{"action":"add_bullet","document":"Perso","content":"duplicate proxmox","parent":"France","message":"Added duplicate proxmox under France in Perso."}`;

async function parseCommand(
  text: string,
  documentTitles: string[]
): Promise<{ action: string; document?: string; content?: string; new_content?: string; parent?: string | null; message: string }> {
  const response = await anthropic.messages.create({
    model: 'claude-haiku-4-5-20251001',
    max_tokens: 256,
    system: SYSTEM_PROMPT,
    messages: [
      {
        role: 'user',
        content: `User's documents: ${JSON.stringify(documentTitles)}\n\nVoice command: "${text}"`,
      },
    ],
  });

  const raw = response.content[0].type === 'text' ? response.content[0].text : '';
  const cleaned = raw.replace(/```json\s*/g, '').replace(/```\s*/g, '').trim();
  console.log(`Voice command input: "${text}" → parsed: ${cleaned}`);
  return JSON.parse(cleaned);
}

function levenshtein(a: string, b: string): number {
  const m = a.length, n = b.length;
  const dp: number[][] = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0));
  for (let i = 0; i <= m; i++) dp[i][0] = i;
  for (let j = 0; j <= n; j++) dp[0][j] = j;
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = Math.min(
        dp[i - 1][j] + 1,
        dp[i][j - 1] + 1,
        dp[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1)
      );
    }
  }
  return dp[m][n];
}

function fuzzyMatchBullet(
  target: string,
  bulletList: Array<{ id: string; content: string }>
): { id: string; content: string } | undefined {
  const t = target.toLowerCase();
  // Exact match
  const exact = bulletList.find(b => b.content.toLowerCase() === t);
  if (exact) return exact;
  // Contains match (either direction)
  const contains = bulletList.find(b =>
    b.content.toLowerCase().includes(t) || t.includes(b.content.toLowerCase())
  );
  if (contains) return contains;
  // Starts-with match
  const prefix = bulletList.find(b =>
    b.content.toLowerCase().startsWith(t) || t.startsWith(b.content.toLowerCase())
  );
  if (prefix) return prefix;
  // Levenshtein distance: allow up to 40% edit distance
  let bestMatch: { id: string; content: string } | undefined;
  let bestDist = Infinity;
  for (const b of bulletList) {
    const bc = b.content.toLowerCase();
    const dist = levenshtein(t, bc);
    const maxLen = Math.max(t.length, bc.length);
    if (dist / maxLen <= 0.4 && dist < bestDist) {
      bestDist = dist;
      bestMatch = b;
    }
  }
  return bestMatch;
}

function fuzzyMatchDocument(
  target: string,
  docs: Array<{ id: string; title: string }>
): { id: string; title: string } | null {
  const normalized = target.toLowerCase().replace(/\b(my|the|a|list)\b/g, '').trim();

  // Exact match first
  const exact = docs.find(d => d.title.toLowerCase() === target.toLowerCase());
  if (exact) return exact;

  // Contains match
  const contains = docs.find(
    d =>
      d.title.toLowerCase().includes(normalized) ||
      normalized.includes(d.title.toLowerCase())
  );
  if (contains) return contains;

  // Prefix match
  const prefix = docs.find(d =>
    d.title.toLowerCase().startsWith(normalized) ||
    normalized.startsWith(d.title.toLowerCase())
  );
  if (prefix) return prefix;

  return null;
}

export async function executeVoiceCommand(
  userId: string,
  text: string
): Promise<VoiceCommandResult> {
  // Fetch user's documents
  const userDocs = await db
    .select({ id: documents.id, title: documents.title })
    .from(documents)
    .where(eq(documents.userId, userId))
    .orderBy(asc(documents.position));

  const documentTitles = userDocs.map(d => d.title);

  // Parse the command using Claude
  const parsed = await parseCommand(text, documentTitles);

  switch (parsed.action) {
    case 'add_bullet': {
      if (!parsed.document || !parsed.content) {
        return { success: false, action: 'add_bullet', message: parsed.message || 'I need a document and content to add.' };
      }
      const doc = fuzzyMatchDocument(parsed.document, userDocs);
      if (!doc) {
        return { success: false, action: 'add_bullet', message: parsed.message || `I couldn't find a document called "${parsed.document}".` };
      }

      let parentId: string | null = null;
      if (parsed.parent) {
        const bullets = await getDocumentBullets(userId, doc.id);
        const parentMatch = bullets.find(b =>
          b.content.toLowerCase().includes(parsed.parent!.toLowerCase()) ||
          parsed.parent!.toLowerCase().includes(b.content.toLowerCase())
        );
        if (!parentMatch) {
          return { success: false, action: 'add_bullet', message: parsed.message || `I couldn't find "${parsed.parent}" in ${doc.title}.` };
        }
        parentId = parentMatch.id;
      }

      await createBullet(userId, {
        documentId: doc.id,
        parentId,
        afterId: null,
        content: parsed.content,
      });

      return { success: true, action: 'add_bullet', message: parsed.message };
    }

    case 'mark_complete': {
      if (!parsed.content) {
        return { success: false, action: 'mark_complete', message: parsed.message || 'I need content to mark as complete.' };
      }
      let mcMatch: { id: string; content: string } | undefined;
      if (parsed.document) {
        const doc = fuzzyMatchDocument(parsed.document, userDocs);
        if (doc) {
          const docBullets = await getDocumentBullets(userId, doc.id);
          mcMatch = fuzzyMatchBullet(parsed.content!, docBullets);
        }
      }
      if (!mcMatch) {
        for (const doc of userDocs) {
          const docBullets = await getDocumentBullets(userId, doc.id);
          mcMatch = fuzzyMatchBullet(parsed.content!, docBullets);
          if (mcMatch) break;
        }
      }
      if (!mcMatch) {
        return { success: false, action: 'mark_complete', message: parsed.message || `I couldn't find "${parsed.content}".` };
      }
      await markComplete(userId, mcMatch.id, true);
      return { success: true, action: 'mark_complete', message: parsed.message };
    }

    case 'replace_bullet': {
      if (!parsed.content || !parsed.new_content) {
        return { success: false, action: 'replace_bullet', message: parsed.message || 'I need the text to replace and the new text.' };
      }

      // If document specified, search there; otherwise search all documents
      let match: { id: string; content: string } | undefined;
      let docTitle = '';
      if (parsed.document) {
        const doc = fuzzyMatchDocument(parsed.document, userDocs);
        if (doc) {
          const docBullets = await getDocumentBullets(userId, doc.id);
          match = docBullets.find(b => b.content.toLowerCase().includes(parsed.content!.toLowerCase()));
          docTitle = doc.title;
        }
      }
      if (!match) {
        // Search across all documents
        for (const doc of userDocs) {
          const allBullets = await getDocumentBullets(userId, doc.id);
          match = fuzzyMatchBullet(parsed.content!, allBullets);
          if (match) { docTitle = doc.title; break; }
        }
      }
      if (!match) {
        return { success: false, action: 'replace_bullet', message: parsed.message || `I couldn't find "${parsed.content}".` };
      }
      await db.update(bullets).set({ content: parsed.new_content, updatedAt: new Date() })
        .where(and(eq(bullets.id, match.id), eq(bullets.userId, userId)));
      return { success: true, action: 'replace_bullet', message: parsed.message || `Replaced "${match.content}" with "${parsed.new_content}" in ${docTitle}.` };
    }

    case 'delete_bullet': {
      if (!parsed.content) {
        return { success: false, action: 'delete_bullet', message: parsed.message || 'I need content to delete.' };
      }
      let delMatch: { id: string; content: string } | undefined;
      if (parsed.document) {
        const doc = fuzzyMatchDocument(parsed.document, userDocs);
        if (doc) {
          const docBullets = await getDocumentBullets(userId, doc.id);
          delMatch = fuzzyMatchBullet(parsed.content!, docBullets);
        }
      }
      if (!delMatch) {
        for (const doc of userDocs) {
          const docBullets = await getDocumentBullets(userId, doc.id);
          delMatch = fuzzyMatchBullet(parsed.content!, docBullets);
          if (delMatch) break;
        }
      }
      if (!delMatch) {
        return { success: false, action: 'delete_bullet', message: parsed.message || `I couldn't find "${parsed.content}".` };
      }
      await softDeleteBullet(userId, delMatch.id);
      return { success: true, action: 'delete_bullet', message: parsed.message };
    }

    case 'create_document': {
      if (!parsed.content) {
        return { success: false, action: 'create_document', message: parsed.message || 'What should I name the new document?' };
      }
      const existing = await db
        .select({ position: documents.position })
        .from(documents)
        .where(eq(documents.userId, userId))
        .orderBy(asc(documents.position));
      const pos = existing.length === 0 ? 1.0 : (existing[existing.length - 1] as { position: number }).position + 1.0;

      await db.insert(documents).values({
        userId,
        title: parsed.content,
        position: pos,
      });
      return { success: true, action: 'create_document', message: parsed.message };
    }

    case 'read_document': {
      if (!parsed.document) {
        return { success: false, action: 'read_document', message: parsed.message || 'Which document should I read?' };
      }
      const doc = fuzzyMatchDocument(parsed.document, userDocs);
      if (!doc) {
        return { success: false, action: 'read_document', message: parsed.message || `I couldn't find a document called "${parsed.document}".` };
      }
      const docBullets2 = await getDocumentBullets(userId, doc.id);
      const rootBullets = docBullets2.filter(b => b.parentId === null);
      if (rootBullets.length === 0) {
        return { success: true, action: 'read_document', message: `${doc.title} is empty.` };
      }
      const items = rootBullets.map(b => b.content).join(', ');
      return { success: true, action: 'read_document', message: `${doc.title} contains: ${items}.` };
    }

    case 'search': {
      if (!parsed.content) {
        return { success: false, action: 'search', message: parsed.message || 'What should I search for?' };
      }
      const results = await searchBullets(userId, parsed.content);
      if (results.length === 0) {
        return { success: true, action: 'search', message: `No results found for "${parsed.content}".` };
      }
      const summary = results
        .slice(0, 5)
        .map(r => `"${r.content}" in ${r.documentTitle}`)
        .join(', ');
      return { success: true, action: 'search', message: `Found ${results.length} results. ${summary}.` };
    }

    default:
      return { success: false, action: 'unknown', message: parsed.message || "I didn't understand that. Try something like 'add milk to groceries'." };
  }
}
