import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { addBookmark, removeBookmark, getUserBookmarks } from '../services/bookmarkService.js';

export const bookmarksRouter = Router();

bookmarksRouter.post('/', requireAuth, async (req, res) => {
  const user = req.user as { id: string };
  try {
    const { bulletId } = req.body;
    if (!bulletId) return res.status(400).json({ error: 'bulletId required' });
    const bookmark = await addBookmark(user.id, bulletId);
    res.status(201).json(bookmark);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

bookmarksRouter.delete('/:bulletId', requireAuth, async (req, res) => {
  const user = req.user as { id: string };
  try {
    await removeBookmark(user.id, req.params.bulletId as string);
    res.status(204).send();
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

bookmarksRouter.get('/', requireAuth, async (req, res) => {
  const user = req.user as { id: string };
  try {
    const bookmarks = await getUserBookmarks(user.id);
    res.json(bookmarks);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});
