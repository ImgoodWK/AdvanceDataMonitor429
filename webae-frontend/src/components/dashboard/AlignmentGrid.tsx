import { Tooltip } from 'antd';
import {
  AppstoreOutlined,
} from '@ant-design/icons';
import { useI18n } from '@/i18n';
import { ALL_ALIGNMENTS, type Alignment } from '@/utils/presets';

interface AlignmentGridProps {
  value: Alignment;
  onChange: (v: Alignment) => void;
  ariaLabel?: string;
}

// 9-grid alignment picker. Renders a 3×3 grid of cells; the active cell is
// highlighted. Each cell shows a small dot in the corresponding corner/edge.
export function AlignmentGrid({ value, onChange, ariaLabel }: AlignmentGridProps) {
  const { t } = useI18n();
  return (
    <div
      className="align-grid"
      role="radiogroup"
      aria-label={ariaLabel ?? t('alignment')}
    >
      {ALL_ALIGNMENTS.map((a) => {
        const isActive = a === value;
        // Position a small dot inside the cell to mirror the alignment slot.
        const [v, h] = a.split('-');
        const vertical = v === 'top' ? 'flex-start' : v === 'bottom' ? 'flex-end' : 'center';
        const horizontal = h === 'left' ? 'flex-start' : h === 'right' ? 'flex-end' : 'center';
        return (
          <Tooltip key={a} title={t('alignment_' + a)}>
            <button
              type="button"
              role="radio"
              aria-checked={isActive}
              aria-label={t('alignment_' + a)}
              className={'align-grid-cell' + (isActive ? ' is-active' : '')}
              onClick={() => onChange(a)}
            >
              <span
                style={{
                  display: 'inline-flex',
                  width: '100%',
                  height: '100%',
                  alignItems: vertical,
                  justifyContent: horizontal,
                }}
              >
                <AppstoreOutlined style={{ fontSize: '0.75rem', opacity: isActive ? 1 : 0.5 }} />
              </span>
            </button>
          </Tooltip>
        );
      })}
    </div>
  );
}
