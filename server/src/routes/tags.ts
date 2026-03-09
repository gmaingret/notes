import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { getTagCounts, getBulletsForTag, type ChipType } from '../services/tagService.js';

export const tagsRouter = Router();

tagsRouter.get('/', requireAuth, async (req, res) => {
  const user = req.user as { id: string };
  try {
    const tags = await getTagCounts(user.id);
    res.json(tags);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

tagsRouter.get('/:type/:value/bullets', requireAuth, async (req, res) => {
  const user = req.user as { id: string };
  try {
    const { type, value } = req.params;
    const bullets = await getBulletsForTag(user.id, type as ChipType, value as string);
    res.json(bullets);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});
