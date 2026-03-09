import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { getTagCounts, getBulletsForTag } from '../services/tagService.js';

export const tagsRouter = Router();

tagsRouter.get('/', requireAuth, async (req, res) => {
  try {
    const tags = await getTagCounts(req.user!.id);
    res.json(tags);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

tagsRouter.get('/:type/:value/bullets', requireAuth, async (req, res) => {
  try {
    const { type, value } = req.params;
    const bullets = await getBulletsForTag(req.user!.id, type, value);
    res.json(bullets);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});
