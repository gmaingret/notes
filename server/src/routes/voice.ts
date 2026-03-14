import { Router } from 'express';
import { z } from 'zod';
import rateLimit from 'express-rate-limit';
import { requireAuth } from '../middleware/auth.js';
import { executeVoiceCommand } from '../services/voiceCommandService.js';

export const voiceRouter = Router();

const voiceLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 30,
  message: { error: 'Too many voice commands, please try again later.' },
});

voiceRouter.use(requireAuth);
voiceRouter.use(voiceLimiter);

const commandSchema = z.object({
  text: z.string().min(1).max(500),
});

voiceRouter.post('/command', async (req, res) => {
  const user = req.user as { id: string };
  const parsed = commandSchema.safeParse(req.body);

  if (!parsed.success) {
    return res.status(400).json({ error: 'Invalid request. Provide a "text" field.' });
  }

  try {
    const result = await executeVoiceCommand(user.id, parsed.data.text);
    return res.json(result);
  } catch (err) {
    console.error('Voice command error:', err);
    return res.status(500).json({
      success: false,
      action: 'error',
      message: 'Something went wrong processing your command.',
    });
  }
});
