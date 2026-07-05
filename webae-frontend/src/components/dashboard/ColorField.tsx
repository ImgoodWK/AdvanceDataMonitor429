import { useState } from 'react';
import { ColorPicker, Input, Tooltip, Button, Space } from 'antd';
import { BgColorsOutlined } from '@ant-design/icons';
import type { Color } from 'antd/es/color-picker';
import { useI18n } from '@/i18n';

interface ColorFieldProps {
  label: string;
  value: string;
  onChange: (v: string) => void;
  disabled?: boolean;
}

declare global {
  interface Window {
    EyeDropper?: new () => { open: () => Promise<{ sRGBHex: string }> };
  }
}

async function pickColorFromScreen(): Promise<string | null> {
  if (typeof window === 'undefined' || !window.EyeDropper) return null;
  try {
    const dropper = new window.EyeDropper();
    const result = await dropper.open();
    return result.sRGBHex;
  } catch {
    return null;
  }
}

function toHex(color: Color | string): string {
  if (typeof color === 'string') return color;
  const hex = color.toHexString();
  return hex.length === 9 ? hex.slice(0, 7) : hex;
}

// Hex color input with antd ColorPicker, live swatch, eyedropper, and clear support.
export function ColorField({ label, value, onChange, disabled }: ColorFieldProps) {
  const { t } = useI18n();
  const [pickerOpen, setPickerOpen] = useState(false);

  const swatchStyle: React.CSSProperties = {
    width: 22,
    height: 22,
    borderRadius: 4,
    border: '1px solid var(--border)',
    flexShrink: 0,
    background: value && value.trim() !== '' ? value : 'transparent',
    backgroundImage:
      value && value.trim() !== ''
        ? undefined
        : 'repeating-conic-gradient(var(--border-light) 0% 25%, transparent 0% 50%) 50% / 8px 8px',
    cursor: disabled ? 'not-allowed' : 'pointer',
  };

  const handleEyedropper = async () => {
    if (disabled) return;
    const picked = await pickColorFromScreen();
    if (picked) onChange(picked);
  };

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%' }}>
      <ColorPicker
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        value={value && value.trim() !== '' ? value : undefined}
        disabled={disabled}
        onChange={(c) => onChange(toHex(c))}
        showText={false}
        size="small"
        trigger="click"
      >
        <Tooltip title={label}>
          <span style={swatchStyle} aria-hidden onClick={() => !disabled && setPickerOpen(true)} />
        </Tooltip>
      </ColorPicker>
      <Input
        style={{ flex: 1 }}
        size="small"
        placeholder={t('dashCfgColorHexPlaceholder')}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        allowClear
        disabled={disabled}
        aria-label={label}
      />
      <Space size={4}>
        <Tooltip title={t('dashCfgEyedropper')}>
          <Button
            type="text"
            size="small"
            icon={<BgColorsOutlined />}
            disabled={disabled || typeof window === 'undefined' || !window.EyeDropper}
            onClick={handleEyedropper}
            aria-label={t('dashCfgEyedropper')}
          />
        </Tooltip>
      </Space>
    </div>
  );
}
