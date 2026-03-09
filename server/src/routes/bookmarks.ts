import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { addBookmark, removeBookmark, getUserBookmarks } from '../services/bookmarkService.js';

export const bookmarksRouter = Router();

bookmarksRouter.post('/', requireAuth, async (req, res) => {
  try {
    const { bulletId } = req.body;
    if (!bulletId) return res.status(400).json({ error: 'bulletId required' });
    const bookmark = await addBookmark(req.user!.id, bulletId);
    res.status(201).json(bookmark);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

bookmarksRouter.delete('/:bulletId', requireAuth, async (req, res) => {
  try {
    await removeBookmark(req.user!.id, req.params.bulletId);
    res.status(204).send();
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

bookmarksRouter.get('/', requireAuth, async (req, res) => {
  try {
    const bookmarks = await getUserBookmarks(req.user!.id);
    res.json(bookmarks);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});
