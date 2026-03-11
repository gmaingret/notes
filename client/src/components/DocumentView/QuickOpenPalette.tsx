import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, FileText, AlignLeft, Bookmark } from 'lucide-react';
import { useDocuments } from '../../hooks/useDocuments';
import { useSearch } from '../../hooks/useSearch';
import { useBookmarks } from '../../hooks/useBookmarks';

interface QuickOpenPaletteProps {
  onClose: () => void;
}

interface Document {
  id: string;
  title: string;
  lastOpenedAt: string | null;
}

interface SearchResult {
  id: string;
  content: string;
  documentId: string;
  documentTitle: string;
}

interface BookmarkRow {
  id: string;
  content: string;
  documentId: string;
  documentTitle: string;
}

type PaletteResult =
  | { type: 'document'; doc: Document }
  | { type: 'bullet'; result: SearchResult }
  | { type: 'bookmark'; bookmark: BookmarkRow };

export function QuickOpenPalette({ onClose }: QuickOpenPaletteProps) {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const { data: allDocs } = useDocuments();
  const { data: searchResults } = useSearch(query);
  const { data: bookmarks } = useBookmarks();
  const inputRef = useRef<HTMLInputElement>(null);

  // Auto-focus input on mount
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  // Escape key handler
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  // Recent docs empty state (5 most recently opened, sorted by lastOpenedAt descending)
  const recentDocs = useMemo(() => {
    if (!allDocs) return [];
    return [...(allDocs as Document[])]
      .filter(d => d.lastOpenedAt !== null)
      .sort((a, b) => new Date(b.lastOpenedAt!).getTime() - new Date(a.lastOpenedAt!).getTime())
      .slice(0, 5);
  }, [allDocs]);

  // Search result sections (only when query.length >= 2)
  const docResults = useMemo(() => {
    if (query.length < 2 || !allDocs) return [];
    return (allDocs as Document[])
      .filter(d => d.title.toLowerCase().includes(query.toLowerCase()))
      .slice(0, 3);
  }, [allDocs, query]);

  const bulletResults = (searchResults ?? []).slice(0, 3) as SearchResult[];

  const bookmarkResults = useMemo(() => {
    if (query.length < 2 || !bookmarks) return [];
    return (bookmarks as BookmarkRow[])
      .filter(b => b.content.toLowerCase().includes(query.toLowerCase()))
      .slice(0, 3);
  }, [bookmarks, query]);

  // Flat result list for keyboard navigation
  const flatResults: PaletteResult[] = useMemo(() => {
    if (query.length < 2) {
      return recentDocs.map(doc => ({ type: 'document' as const, doc }));
    }
    return [
      ...docResults.map(doc => ({ type: 'document' as const, doc })),
      ...bulletResults.map(result => ({ type: 'bullet' as const, result })),
      ...bookmarkResults.map(bookmark => ({ type: 'bookmark' as const, bookmark })),
    ];
  }, [query, recentDocs, docResults, bulletResults, bookmarkResults]);

  // Reset selectedIndex when result list changes
  useEffect(() => {
    setSelectedIndex(0);
  }, [flatResults.length]);

  function openResult(item: PaletteResult) {
    if (item.type === 'document') {
      navigate(`/doc/${item.doc.id}`);
    } else if (item.type === 'bullet') {
      navigate(`/doc/${item.result.documentId}#bullet/${item.result.id}`);
    } else if (item.type === 'bookmark') {
      navigate(`/doc/${item.bookmark.documentId}#bullet/${item.bookmark.id}`);
    }
    onClose();
  }

  function handleInputKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex(i => Math.min(i + 1, flatResults.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex(i => Math.max(i - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (flatResults[selectedIndex]) openResult(flatResults[selectedIndex]);
    }
  }

  return (
    <>
      {/* Backdrop */}
      <div className="qop-backdrop" onClick={onClose} />
      {/* Palette box */}
      <div className="qop-box" onClick={(e) => e.stopPropagation()}>
        {/* Input row with search icon */}
        <div className="qop-input-row">
          <Search size={20} strokeWidth={1.5} className="qop-search-icon" />
          <input
            ref={inputRef}
            className="qop-input"
            value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={handleInputKeyDown}
            placeholder="Search documents and bullets..."
          />
        </div>
        {/* Results */}
        <div className="qop-results">
          {query.length < 2 ? (
            /* Empty state: recent docs */
            <>
              {recentDocs.length > 0 && (
                <div className="qop-section-header">Recent</div>
              )}
              {recentDocs.map((doc, i) => (
                <div
                  key={doc.id}
                  className={`qop-result-row${i === selectedIndex ? ' qop-result-row--selected' : ''}`}
                  onClick={() => openResult({ type: 'document', doc })}
                >
                  <FileText size={20} strokeWidth={1.5} />
                  <span className="qop-result-title">{doc.title || 'Untitled'}</span>
                </div>
              ))}
            </>
          ) : (
            /* Search results: Documents, Bullets, Bookmarks */
            <>
              {docResults.length > 0 && (
                <>
                  <div className="qop-section-header">Documents</div>
                  {docResults.map((doc) => {
                    const idx = flatResults.findIndex(r => r.type === 'document' && r.doc.id === doc.id);
                    return (
                      <div
                        key={doc.id}
                        className={`qop-result-row${idx === selectedIndex ? ' qop-result-row--selected' : ''}`}
                        onClick={() => openResult({ type: 'document', doc })}
                      >
                        <FileText size={20} strokeWidth={1.5} />
                        <span className="qop-result-title">{doc.title || 'Untitled'}</span>
                      </div>
                    );
                  })}
                </>
              )}
              {bulletResults.length > 0 && (
                <>
                  <div className="qop-section-header">Bullets</div>
                  {bulletResults.map((result) => {
                    const idx = flatResults.findIndex(r => r.type === 'bullet' && r.result.id === result.id);
                    return (
                      <div
                        key={result.id}
                        className={`qop-result-row${idx === selectedIndex ? ' qop-result-row--selected' : ''}`}
                        onClick={() => openResult({ type: 'bullet', result })}
                      >
                        <AlignLeft size={20} strokeWidth={1.5} />
                        <span className="qop-result-title">{result.content}</span>
                        <span className="qop-result-subtitle">{result.documentTitle}</span>
                      </div>
                    );
                  })}
                </>
              )}
              {bookmarkResults.length > 0 && (
                <>
                  <div className="qop-section-header">Bookmarks</div>
                  {bookmarkResults.map((bookmark) => {
                    const idx = flatResults.findIndex(r => r.type === 'bookmark' && r.bookmark.id === bookmark.id);
                    return (
                      <div
                        key={bookmark.id}
                        className={`qop-result-row${idx === selectedIndex ? ' qop-result-row--selected' : ''}`}
                        onClick={() => openResult({ type: 'bookmark', bookmark })}
                      >
                        <Bookmark size={20} strokeWidth={1.5} />
                        <div className="qop-result-content">
                          <span className="qop-result-title">{bookmark.content}</span>
                          <span className="qop-result-subtitle">{bookmark.documentTitle}</span>
                        </div>
                      </div>
                    );
                  })}
                </>
              )}
              {docResults.length === 0 && bulletResults.length === 0 && bookmarkResults.length === 0 && (
                <div className="qop-empty">No results for "{query}"</div>
              )}
            </>
          )}
        </div>
      </div>
    </>
  );
}
