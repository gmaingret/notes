import { v4 as uuidv4 } from 'uuid';

export type TestBullet = {
  id: string;
  parentId: string | null;
  content: string;
  position: number;
  isComplete: boolean;
  isCollapsed: boolean;
  deletedAt: string | null;
  documentId: string;
  userId: string;
};

export function makeBullet(overrides?: Partial<TestBullet>): TestBullet {
  return {
    id: uuidv4(),
    parentId: null,
    content: 'test content',
    position: 1.0,
    isComplete: false,
    isCollapsed: false,
    deletedAt: null,
    documentId: 'doc-1',
    userId: 'user-1',
    ...overrides,
  };
}

export function makeBulletTree(count: number, parentId: string | null = null): TestBullet[] {
  return Array.from({ length: count }, (_, i) =>
    makeBullet({
      id: uuidv4(),
      parentId,
      position: (i + 1) * 1.0,
      content: `bullet ${i + 1}`,
    })
  );
}
