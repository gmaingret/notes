import { useDocuments, useReorderDocument } from '../../hooks/useDocuments';
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { DocumentRow } from './DocumentRow';

type Props = { activeDocId: string | null };

export function DocumentList({ activeDocId }: Props) {
  const { data: docs = [], isLoading } = useDocuments();
  const { mutate: reorder } = useReorderDocument();

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 5 }, // Prevent accidental drags on click
    })
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;

    const overIdx = docs.findIndex(d => d.id === over.id);
    const activeIdx = docs.findIndex(d => d.id === active.id);

    // Recompute new order after drag
    const newDocs = [...docs];
    const [moved] = newDocs.splice(activeIdx, 1);
    newDocs.splice(overIdx, 0, moved);

    // afterId = item before moved in the new order (null if first)
    const newIdx = newDocs.findIndex(d => d.id === moved.id);
    const afterId = newIdx > 0 ? newDocs[newIdx - 1].id : null;

    reorder({ id: String(active.id), afterId });
  };

  if (isLoading) return <div style={{ padding: '1rem', fontSize: '1rem' }} className="doc-list-empty">Loading...</div>;
  if (docs.length === 0) return <div style={{ padding: '1rem', fontSize: '1rem' }} className="doc-list-empty">No documents</div>;

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <SortableContext items={docs.map(d => d.id)} strategy={verticalListSortingStrategy}>
        {docs.map(doc => (
          <DocumentRow key={doc.id} document={doc} isActive={doc.id === activeDocId} />
        ))}
      </SortableContext>
    </DndContext>
  );
}
