import { Button, ColorPicker, Input, Space, Tooltip } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import type { Color } from 'antd/es/color-picker';
import { useI18n } from '@/i18n';

interface ColorListFieldProps {
  /** Display label shown above the list. */
  label: string;
  /** Array of hex color strings (e.g. {@code ['#ff0000', '#00ff00']}). */
  value: string[];
  onChange: (v: string[]) => void;
  disabled?: boolean;
}

function toHex(color: Color | string): string {
  if (typeof color === 'string') return color;
  const hex = color.toHexString();
  return hex.length === 9 ? hex.slice(0, 7) : hex;
}

/**
 * Multi-color list editor for bar/pie segment colors. Each row is a single
 * color (ColorPicker + hex input + delete). An "add" button appends a new
 * empty entry. Values are stored as {@code string[]} in the widget config.
 */
export function ColorListField({ label, value, onChange, disabled }: ColorListFieldProps) {
  const { t } = useI18n();
  const entries = Array.isArray(value) ? value : [];

  const update = (index: number, v: string) => {
    const next = entries.slice();
    next[index] = v;
    onChange(next);
  };

  const remove = (index: number) => {
    const next = entries.slice();
    next.splice(index, 1);
    onChange(next);
  };

  const add = () => {
    onChange([...entries, '']);
  };

  return (
    <div style={{ width: '100%' }}>
      <Space align="center" style={{ width: '100%', justifyContent: 'space-between', marginBottom: 4 }}>
        <span style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>{label}</span>
        <Button
          type="text"
          size="small"
          icon={<PlusOutlined />}
          disabled={disabled}
          onClick={add}
          aria-label={t('dashCfgColorListAdd')}
        >
          {t('dashCfgColorListAdd')}
        </Button>
      </Space>
      <Space direction="vertical" style={{ width: '100%' }} size={4}>
        {entries.length === 0 && (
          <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)' }}>
            {t('dashCfgColorListEmpty')}
          </span>
        )}
        {entries.map((c, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%' }}>
            <ColorPicker
              value={c && c.trim() !== '' ? c : undefined}
              disabled={disabled}
              onChange={(col) => update(i, toHex(col))}
              showText={false}
              size="small"
              trigger="click"
            >
              <Tooltip title={label}>
                <span
                  aria-hidden
                  style={{
                    width: 22,
                    height: 22,
                    borderRadius: 4,
                    border: '1px solid var(--border)',
                    flexShrink: 0,
                    background: c && c.trim() !== '' ? c : 'transparent',
                    backgroundImage:
                      c && c.trim() !== ''
                        ? undefined
                        : 'repeating-conic-gradient(var(--border-light) 0% 25%, transparent 0% 50%) 50% / 8px 8px',
                    cursor: disabled ? 'not-allowed' : 'pointer',
                  }}
                />
              </Tooltip>
            </ColorPicker>
            <Input
              style={{ flex: 1 }}
              size="small"
              placeholder={t('dashCfgColorHexPlaceholder')}
              value={c}
              onChange={(e) => update(i, e.target.value)}
              allowClear
              disabled={disabled}
              aria-label={`${label} ${i + 1}`}
            />
            <Tooltip title={t('dashCfgColorListRemove')}>
              <Button
                type="text"
                size="small"
                icon={<DeleteOutlined />}
                disabled={disabled}
                onClick={() => remove(i)}
                aria-label={t('dashCfgColorListRemove')}
              />
            </Tooltip>
          </div>
        ))}
      </Space>
    </div>
  );
}
