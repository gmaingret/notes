import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { searchBullets } from '../services/searchService.js';

export const searchRouter = Router();

searchRouter.get('/', requireAuth, async (req, res) => {
  try {
    const q = req.query.q as string | undefined;
    if (!q) return res.status(400).json({ error: 'q parameter required' });
    const results = await searchBullets(req.user!.id, q);
    res.json(results);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});
