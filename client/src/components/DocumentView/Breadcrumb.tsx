import { useNavigate } from 'react-router-dom';
import type { BulletMap } from './BulletTree';
import type { Bullet } from '../../hooks/useBullets';

type Props = {
  documentTitle: string;
  zoomedBulletId: string;
  bulletMap: BulletMap;
};

function getAncestorChain(bulletMap: BulletMap, bulletId: string): Bullet[] {
  const chain: Bullet[] = [];
  let current = bulletMap[bulletId];
  while (current) {
    chain.unshift(current); // prepend so doc→...→current
    if (!current.parentId) break;
    current = bulletMap[current.parentId];
  }
  return chain; // [root-ancestor, ..., current]
}

function truncate(text: string, maxLen = 20): string {
  if (text.length <= maxLen) return text;
  return text.slice(0, maxLen) + '...';
}

export function Breadcrumb({ documentTitle, zoomedBulletId, bulletMap }: Props) {
  const navigate = useNavigate();

  const chain = getAncestorChain(bulletMap, zoomedBulletId);
  // chain = [root-ancestor, ..., current-bullet]
  // current bullet is the last element
  const currentBullet = chain[chain.length - 1];
  // ancestors are all elements before the last
  const ancestors = chain.slice(0, chain.length - 1);

  // Truncation rule: if more than 3 ancestors, show first 1, '…', then last 1 before current
  let displayedAncestors: Array<{ bullet: Bullet | null; isEllipsis: boolean }>;
  if (ancestors.length > 3) {
    displayedAncestors = [
      { bullet: ancestors[0], isEllipsis: false },
      { bullet: null, isEllipsis: true },
      { bullet: ancestors[ancestors.length - 1], isEllipsis: false },
    ];
  } else {
    displayedAncestors = ancestors.map(b => ({ bullet: b, isEllipsis: false }));
  }

  const separatorStyle: React.CSSProperties = {
    margin: '0 0.4rem',
    userSelect: 'none',
  };

  const clickableStyle: React.CSSProperties = {
    cursor: 'pointer',
    maxWidth: '15ch',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    display: 'inline-flex',
    alignItems: 'center',
    verticalAlign: 'middle',
    minHeight: 44,
  };

  const clickableHoverStyle = {
    textDecoration: 'underline',
  };

  const currentStyle: React.CSSProperties = {
    maxWidth: '20ch',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    display: 'inline-block',
    verticalAlign: 'middle',
  };

  return (
    <nav
      style={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        fontSize: '0.9rem',
        margin: '0 0 1.5rem',
        lineHeight: 1.4,
      }}
      aria-label="Breadcrumb"
    >
      {/* Document title — always clickable, goes to doc root */}
      <span
        role="button"
        tabIndex={0}
        className="breadcrumb-ancestor"
        style={clickableStyle}
        title={documentTitle}
        onClick={() => navigate('')}
        onKeyDown={(e) => e.key === 'Enter' && navigate('')}
        onMouseEnter={e => Object.assign((e.target as HTMLElement).style, clickableHoverStyle)}
        onMouseLeave={e => Object.assign((e.target as HTMLElement).style, { textDecoration: 'none' })}
      >
        🏠
      </span>

      {/* Ancestors */}
      {displayedAncestors.map((item, idx) => (
        <span key={item.isEllipsis ? `ellipsis-${idx}` : item.bullet!.id} style={{ display: 'flex', alignItems: 'center' }}>
          <span className="breadcrumb-separator" style={separatorStyle} aria-hidden="true">›</span>
          {item.isEllipsis ? (
            <span className="breadcrumb-ellipsis" style={{ userSelect: 'none' }}>…</span>
          ) : (
            <span
              role="button"
              tabIndex={0}
              className="breadcrumb-ancestor"
              style={clickableStyle}
              title={item.bullet!.content}
              onClick={() => navigate(`#bullet/${item.bullet!.id}`)}
              onKeyDown={(e) => e.key === 'Enter' && navigate(`#bullet/${item.bullet!.id}`)}
              onMouseEnter={e => Object.assign((e.target as HTMLElement).style, clickableHoverStyle)}
              onMouseLeave={e => Object.assign((e.target as HTMLElement).style, { textDecoration: 'none' })}
            >
              {truncate(item.bullet!.content)}
            </span>
          )}
        </span>
      ))}

      {/* Current bullet — not clickable */}
      {currentBullet && (
        <>
          <span className="breadcrumb-separator" style={separatorStyle} aria-hidden="true">›</span>
          <span className="breadcrumb-current" style={currentStyle} title={currentBullet.content}>
            {truncate(currentBullet.content)}
          </span>
        </>
      )}
    </nav>
  );
}
