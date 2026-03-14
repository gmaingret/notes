import Anthropic from '@anthropic-ai/sdk';
import { db } from '../../db/index.js';
import { documents } from '../../db/schema.js';
import { eq, asc } from 'drizzle-orm';
import { createBullet, getDocumentBullets, softDeleteBullet, markComplete } from './bulletService.js';
import { searchBullets } from './searchService.js';

const anthropic = new Anthropic(); // reads ANTHROPIC_API_KEY from env

export type VoiceCommandResult = {
  success: boolean;
  action: string;
  message: string;
};

const SYSTEM_PROMPT = `You are a voice command parser for a notes/outliner app. The user speaks a command and you must parse it into a structured JSON action.

Available actions:
- add_bullet: Add a new bullet point to a document
- mark_complete: Mark a bullet as complete/done
- delete_bullet: Delete a bullet from a document
- create_document: Create a new document
- read_document: Read back the contents of a document
- search: Search across all notes
- unknown: Command not understood

Rules:
- Match document names case-insensitively and flexibly (e.g. "grocery list" matches "Groceries", "my groceries" matches "Groceries")
- For add_bullet, extract the content to add and the target document
- For mark_complete/delete_bullet, extract the bullet content to match and the target document
- For create_document, extract the document title
- For read_document, extract the document name
- For search, extract the search query

Respond with ONLY valid JSON, no markdown fences:
{
  "action": "add_bullet" | "mark_complete" | "delete_bullet" | "create_document" | "read_document" | "search" | "unknown",
  "document": "document name to match (if applicable)",
  "content": "bullet content or search query (if applicable)",
  "message": "confirmation message to speak back to the user"
}`;

async function parseCommand(
  text: string,
  documentTitles: string[]
): Promise<{ action: string; document?: string; content?: string; message: string }> {
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
  return JSON.parse(raw);
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
        return { success: false, action: 'add_bullet', message: parsed.message || 'I need both a document name and content to add.' };
      }
      const doc = fuzzyMatchDocument(parsed.document, userDocs);
      if (!doc) {
        return { success: false, action: 'add_bullet', message: `I couldn't find a document called "${parsed.document}".` };
      }

      // Get existing bullets to find the last root bullet for positioning
      const existingBullets = await getDocumentBullets(userId, doc.id);
      const rootBullets = existingBullets.filter(b => b.parentId === null);
      const lastRootBullet = rootBullets.length > 0 ? rootBullets[rootBullets.length - 1] : null;

      await createBullet(userId, {
        documentId: doc.id,
        parentId: null,
        afterId: lastRootBullet?.id ?? null,
        content: parsed.content,
      });

      return { success: true, action: 'add_bullet', message: `Added "${parsed.content}" to ${doc.title}.` };
    }

    case 'mark_complete': {
      if (!parsed.document || !parsed.content) {
        return { success: false, action: 'mark_complete', message: parsed.message || 'I need a document name and bullet content to mark complete.' };
      }
      const doc = fuzzyMatchDocument(parsed.document, userDocs);
      if (!doc) {
        return { success: false, action: 'mark_complete', message: `I couldn't find a document called "${parsed.document}".` };
      }
      const bullets = await getDocumentBullets(userId, doc.id);
      const match = bullets.find(b =>
        b.content.toLowerCase().includes(parsed.content!.toLowerCase())
      );
      if (!match) {
        return { success: false, action: 'mark_complete', message: `I couldn't find "${parsed.content}" in ${doc.title}.` };
      }
      await markComplete(userId, match.id, true);
      return { success: true, action: 'mark_complete', message: `Marked "${match.content}" as complete in ${doc.title}.` };
    }

    case 'delete_bullet': {
      if (!parsed.document || !parsed.content) {
        return { success: false, action: 'delete_bullet', message: parsed.message || 'I need a document name and bullet content to delete.' };
      }
      const doc = fuzzyMatchDocument(parsed.document, userDocs);
      if (!doc) {
        return { success: false, action: 'delete_bullet', message: `I couldn't find a document called "${parsed.document}".` };
      }
      const bullets = await getDocumentBullets(userId, doc.id);
      const match = bullets.find(b =>
        b.content.toLowerCase().includes(parsed.content!.toLowerCase())
      );
      if (!match) {
        return { success: false, action: 'delete_bullet', message: `I couldn't find "${parsed.content}" in ${doc.title}.` };
      }
      await softDeleteBullet(userId, match.id);
      return { success: true, action: 'delete_bullet', message: `Deleted "${match.content}" from ${doc.title}.` };
    }

    case 'create_document': {
      if (!parsed.content) {
        return { success: false, action: 'create_document', message: parsed.message || 'What should I name the new document?' };
      }
      // Compute position at end
      const lastDoc = userDocs[userDocs.length - 1];
      const position = lastDoc ? lastDoc.title.length + 1.0 : 1.0; // simple append
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
      return { success: true, action: 'create_document', message: `Created document "${parsed.content}".` };
    }

    case 'read_document': {
      if (!parsed.document) {
        return { success: false, action: 'read_document', message: parsed.message || 'Which document should I read?' };
      }
      const doc = fuzzyMatchDocument(parsed.document, userDocs);
      if (!doc) {
        return { success: false, action: 'read_document', message: `I couldn't find a document called "${parsed.document}".` };
      }
      const bullets = await getDocumentBullets(userId, doc.id);
      const rootBullets = bullets.filter(b => b.parentId === null);
      if (rootBullets.length === 0) {
        return { success: true, action: 'read_document', message: `${doc.title} is empty.` };
      }
      const items = rootBullets.map(b => b.content).join(', ');
      return { success: true, action: 'read_document', message: `${doc.title} has: ${items}.` };
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
      return { success: true, action: 'search', message: `Found ${results.length} results. Top matches: ${summary}.` };
    }

    default:
      return { success: false, action: 'unknown', message: parsed.message || "I didn't understand that command. Try something like 'add milk to groceries'." };
  }
}
