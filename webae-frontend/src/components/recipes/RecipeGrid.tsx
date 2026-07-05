import { Icon } from '@/components/Icon';
import type { RecipeDto, RecipeItemEntry } from '@/types/dto';

interface RecipeGridProps {
  recipe: RecipeDto;
  cellSize?: number;
  onItemClick?: (item: RecipeItemEntry) => void;
}

export function RecipeGrid({ recipe, cellSize = 36, onItemClick }: RecipeGridProps) {
  const width = recipe.gridWidth && recipe.gridWidth > 0 ? recipe.gridWidth : 0;
  const height = recipe.gridHeight && recipe.gridHeight > 0 ? recipe.gridHeight : 0;
  const slots = recipe.gridSlots || [];

  if (width <= 0 || height <= 0 || slots.length === 0) {
    return null;
  }

  const slotMap = new Map<string, RecipeItemEntry>();
  for (const slot of slots) {
    slotMap.set(`${slot.col}_${slot.row}`, slot.item);
  }

  const rows: JSX.Element[] = [];
  for (let row = 0; row < height; row++) {
    const cells: JSX.Element[] = [];
    for (let col = 0; col < width; col++) {
      const item = slotMap.get(`${col}_${row}`);
      cells.push(
        <div
          key={`${col}_${row}`}
          style={{
            width: cellSize,
            height: cellSize,
            border: '1px solid var(--border)',
            borderRadius: 4,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: item ? 'var(--bg-elevated)' : 'var(--bg-muted)',
            cursor: item && onItemClick ? 'pointer' : 'default',
          }}
          onClick={() => item && onItemClick?.(item)}
          role={item && onItemClick ? 'button' : undefined}
          tabIndex={item && onItemClick ? 0 : undefined}
          onKeyDown={(e) => {
            if (item && onItemClick && (e.key === 'Enter' || e.key === ' ')) {
              e.preventDefault();
              onItemClick(item);
            }
          }}
        >
          {item ? <Icon item={item} size={cellSize - 8} alt={item.displayName} /> : null}
        </div>
      );
    }
    rows.push(
      <div key={row} style={{ display: 'flex', gap: 4 }}>
        {cells}
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 8 }}>
      {rows}
    </div>
  );
}
