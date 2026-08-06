import type { ReactNode } from 'react';
import { Typography } from 'antd';

const { Text } = Typography;

export interface SettingRowProps {
  label: string;
  hint?: string;
  children: ReactNode;
}

/** Label + optional hint + control — shared by settings drawers. */
export function SettingRow({ label, hint, children }: SettingRowProps) {
  return (
    <div className="webae-setting-row">
      <Text strong className="webae-setting-row-label">
        {label}
      </Text>
      {hint ? (
        <Text type="secondary" className="webae-setting-row-hint">
          {hint}
        </Text>
      ) : null}
      {children}
    </div>
  );
}
