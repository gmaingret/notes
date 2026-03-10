import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { undo, redo, getStatus } from '../services/undoService.js';
import { db } from '../../db/index.js';

export const undoRouter = Router();
undoRouter.use(requireAuth);

// UNDO-01: Undo the most recent action
// POST /api/undo
undoRouter.post('/undo', async (req, res) => {
  const user = req.user as { id: string };
  try {
    const result = await undo(db, user.id);
    return res.json(result);
  } catch (err) {
    throw err;
  }
});

// UNDO-02: Redo the most recently undone action
// POST /api/redo
undoRouter.post('/redo', async (req, res) => {
  const user = req.user as { id: string };
  try {
    const result = await redo(db, user.id);
    return res.json(result);
  } catch (err) {
    throw err;
  }
});

// UNDO-03: Get undo/redo availability status
// GET /api/undo/status
undoRouter.get('/undo/status', async (req, res) => {
  const user = req.user as { id: string };
  try {
    const status = await getStatus(db, user.id);
    return res.json(status);
  } catch (err) {
    throw err;
  }
});
