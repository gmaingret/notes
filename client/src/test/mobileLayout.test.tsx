import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

// jsdom does not implement window.matchMedia — provide a desktop-default mock
if (!window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false, // desktop default: no mobile match
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

// pdfjs-dist is imported transitively via DocumentView → BulletNode chain.
// Mock it at module level to prevent DOMMatrix crash in jsdom.
vi.mock('pdfjs-dist', () => ({
  getDocument: vi.fn(),
  GlobalWorkerOptions: { workerSrc: '' },
  version: '4.0.0',
}));

// Mock react-router-dom — AppPage uses useParams and useNavigate
vi.mock('react-router-dom', () => ({
  useParams: vi.fn().mockReturnValue({ docId: 'doc-1' }),
  useNavigate: vi.fn().mockReturnValue(vi.fn()),
  useLocation: vi.fn().mockReturnValue({ pathname: '/doc/doc-1', hash: '' }),
}));

// Mock useDocuments so AppPage/Sidebar don't call real API
vi.mock('../hooks/useDocuments', () => ({
  useDocuments: vi.fn().mockReturnValue({ data: [], isLoading: false }),
  useCreateDocument: vi.fn().mockReturnValue({ mutate: vi.fn() }),
  useExportAllDocuments: vi.fn().mockReturnValue({ mutate: vi.fn() }),
  useOpenDocument: vi.fn().mockReturnValue({ mutate: vi.fn() }),
  useRenameDocument: vi.fn().mockReturnValue({ mutate: vi.fn() }),
  useDeleteDocument: vi.fn().mockReturnValue({ mutate: vi.fn() }),
  useReorderDocument: vi.fn().mockReturnValue({ mutate: vi.fn() }),
}));

// Mock AuthContext — Sidebar calls useAuth for logout
vi.mock('../contexts/AuthContext', () => ({
  useAuth: vi.fn().mockReturnValue({
    logout: vi.fn(),
    user: { id: 'u1', email: 'test@example.com' },
    accessToken: 'token',
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
  }),
}));

// uiStore mock — controllable sidebarOpen per test
const mockSetSidebarOpen = vi.fn();
let mockSidebarOpen = false;

vi.mock('../store/uiStore', () => ({
  useUiStore: vi.fn().mockImplementation(() => ({
    sidebarOpen: mockSidebarOpen,
    setSidebarOpen: mockSetSidebarOpen,
    sidebarTab: 'docs',
    setSidebarTab: vi.fn(),
    lastOpenedDocId: null,
    setLastOpenedDocId: vi.fn(),
    canvasView: { type: 'document' },
    setCanvasView: vi.fn(),
    searchOpen: false,
    setSearchOpen: vi.fn(),
    focusedBulletId: null,
    setFocusedBulletId: vi.fn(),
  })),
}));

// Mock DocumentView and its transitive dependencies
vi.mock('../components/DocumentView/DocumentView', () => ({
  DocumentView: () => <div data-testid="document-view" />,
}));

// Mock DocumentList, TagBrowser, BookmarkBrowser (Sidebar sub-components)
vi.mock('../components/Sidebar/DocumentList', () => ({
  DocumentList: () => <div data-testid="document-list" />,
}));

vi.mock('../components/Sidebar/TagBrowser', () => ({
  TagBrowser: () => <div data-testid="tag-browser" />,
}));

vi.mock('../components/Sidebar/BookmarkBrowser', () => ({
  BookmarkBrowser: () => <div data-testid="bookmark-browser" />,
}));

import { AppPage } from '../pages/AppPage';
import { Sidebar } from '../components/Sidebar/Sidebar';

// ---------------------------------------------------------------------------
// MOBL-01: Hamburger button renders when sidebar closed and opens sidebar on click
// ---------------------------------------------------------------------------
describe('Mobile Layout — MOBL-01: hamburger opens sidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSidebarOpen = false;
  });

  it('hamburger button is visible when sidebar is closed', () => {
    render(<AppPage />);
    // MOBL-01: When sidebarOpen=false, a hamburger button must be present in AppPage
    const hamburger = screen.getByRole('button', { name: /menu|hamburger|open sidebar/i });
    expect(hamburger).toBeDefined();
  });

  it('clicking hamburger calls setSidebarOpen(true)', () => {
    render(<AppPage />);
    const hamburger = screen.getByRole('button', { name: /menu|hamburger|open sidebar/i });
    fireEvent.click(hamburger);
    expect(mockSetSidebarOpen).toHaveBeenCalledWith(true);
  });
});

// ---------------------------------------------------------------------------
// MOBL-02: Backdrop div always renders (not conditional) and calls setSidebarOpen(false) on click
// Phase 5: backdrop uses .sidebar-backdrop class and is always in DOM (never conditional)
// ---------------------------------------------------------------------------
describe('Mobile Layout — MOBL-02: backdrop closes sidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSidebarOpen = true;
  });

  it('backdrop overlay is always present (even when sidebar is open)', () => {
    render(<Sidebar activeDocId={null} />);
    // MOBL-02: sidebar-backdrop is always rendered (not conditional) so fade-out transition plays
    const backdrop = document.querySelector('.sidebar-backdrop');
    expect(backdrop).not.toBeNull();
  });

  it('backdrop overlay is always present even when sidebar is closed', () => {
    mockSidebarOpen = false;
    render(<Sidebar activeDocId={null} />);
    const backdrop = document.querySelector('.sidebar-backdrop');
    expect(backdrop).not.toBeNull();
  });

  it('clicking backdrop calls setSidebarOpen(false)', () => {
    render(<Sidebar activeDocId={null} />);
    const backdrop = document.querySelector('.sidebar-backdrop');
    expect(backdrop).not.toBeNull();
    fireEvent.click(backdrop!);
    expect(mockSetSidebarOpen).toHaveBeenCalledWith(false);
  });
});

// ---------------------------------------------------------------------------
// MOBL-03: X close button renders inside sidebar header and calls setSidebarOpen(false)
// Phase 5: X button has className "mobile-only" and aria-label "Close sidebar"
// ---------------------------------------------------------------------------
describe('Mobile Layout — MOBL-03: X button closes sidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSidebarOpen = true;
  });

  it('X close button is present in sidebar header with mobile-only class', () => {
    render(<Sidebar activeDocId={null} />);
    // MOBL-03: A close button with className "mobile-only" must be in the sidebar header
    const { container } = render(<Sidebar activeDocId={null} />);
    const closeBtn = container.querySelector('button.mobile-only');
    expect(closeBtn).not.toBeNull();
  });

  it('clicking X button calls setSidebarOpen(false)', () => {
    const { container } = render(<Sidebar activeDocId={null} />);
    const closeBtn = container.querySelector('button.mobile-only') as HTMLElement;
    expect(closeBtn).not.toBeNull();
    fireEvent.click(closeBtn);
    expect(mockSetSidebarOpen).toHaveBeenCalledWith(false);
  });
});

// ---------------------------------------------------------------------------
// MOBL-04: Sidebar aside element has CSS class "sidebar-open" when sidebarOpen is true
// ---------------------------------------------------------------------------
describe('Mobile Layout — MOBL-04: sidebar CSS class reflects open state', () => {
  it('aside has class "sidebar-open" when sidebarOpen is true', () => {
    mockSidebarOpen = true;
    vi.clearAllMocks();
    render(<Sidebar activeDocId={null} />);
    const aside = document.querySelector('aside');
    expect(aside).not.toBeNull();
    expect(aside!.classList.contains('sidebar-open')).toBe(true);
  });

  it('aside does NOT have class "sidebar-open" when sidebarOpen is false', () => {
    mockSidebarOpen = false;
    vi.clearAllMocks();
    render(<Sidebar activeDocId={null} />);
    const aside = document.querySelector('aside');
    expect(aside).not.toBeNull();
    expect(aside!.classList.contains('sidebar-open')).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// MOBL-06: AppPage root div has style height "100dvh" (not "100vh")
// ---------------------------------------------------------------------------
describe('Mobile Layout — MOBL-06: AppPage uses 100dvh', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSidebarOpen = false;
  });

  it('AppPage root div has height 100dvh', () => {
    render(<AppPage />);
    // MOBL-06: The root layout div must use 100dvh instead of 100vh
    // to prevent the address bar from clipping content on mobile browsers
    const root = document.querySelector('div[style*="100dvh"]');
    expect(root).not.toBeNull();
  });

  it('AppPage root div does NOT use 100vh', () => {
    render(<AppPage />);
    const oldRoot = document.querySelector('div[style*="100vh"]');
    // Should not find any div using the legacy 100vh value
    expect(oldRoot).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// MOBL-07: Ctrl+E keydown on document calls setSidebarOpen with toggled value
// ---------------------------------------------------------------------------
describe('Mobile Layout — MOBL-07: Ctrl+E toggles sidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('Ctrl+E when sidebar is closed calls setSidebarOpen(true)', () => {
    mockSidebarOpen = false;
    render(<AppPage />);
    fireEvent.keyDown(document, { key: 'e', ctrlKey: true, bubbles: true });
    expect(mockSetSidebarOpen).toHaveBeenCalledWith(true);
  });

  it('Ctrl+E when sidebar is open calls setSidebarOpen(false)', () => {
    mockSidebarOpen = true;
    render(<AppPage />);
    fireEvent.keyDown(document, { key: 'e', ctrlKey: true, bubbles: true });
    expect(mockSetSidebarOpen).toHaveBeenCalledWith(false);
  });
});
