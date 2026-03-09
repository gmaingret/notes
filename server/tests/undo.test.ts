import { describe, it } from 'vitest';
import {
  recordUndoEvent,
  undo,
  redo,
  getStatus,
} from '../src/services/undoService.js';

describe('undoService.recordUndoEvent', () => {
  it('inserts a new undo_event row with forward_op and inverse_op', () => {
    throw new Error('not yet implemented');
  });

  it('advances the undo_cursor seq for the user', () => {
    throw new Error('not yet implemented');
  });

  it('truncates redo stack (events with seq > current cursor) before recording', () => {
    throw new Error('not yet implemented');
  });

  it('drops the oldest event when 51st event is recorded (50-step cap)', () => {
    throw new Error('not yet implemented');
  });
});

describe('undoService.undo', () => {
  it('applies the inverse_op of the current event and decrements cursor', () => {
    throw new Error('not yet implemented');
  });

  it('returns can_undo: false when cursor is at 0', () => {
    throw new Error('not yet implemented');
  });
});

describe('undoService.redo', () => {
  it('applies the forward_op of the next event and increments cursor', () => {
    throw new Error('not yet implemented');
  });

  it('returns can_redo: false when cursor is at latest seq', () => {
    throw new Error('not yet implemented');
  });
});

describe('undoService.getStatus', () => {
  it('returns { canUndo: false, canRedo: false } for user with no events', () => {
    throw new Error('not yet implemented');
  });

  it('returns { canUndo: true, canRedo: false } after one event recorded', () => {
    throw new Error('not yet implemented');
  });
});
