import { useCallback } from 'react';
import { formatNumber, type NumberFormat } from '@/utils/format';
import { useAppContext } from '@/context/AppContext';

/**
 * Hook that returns a formatNumber function bound to the current global
 * number format setting.
 */
export function useNumberFormat() {
  const { numberFormat } = useAppContext();
  return useCallback(
    (value: number | undefined | null, override?: NumberFormat) =>
      formatNumber(value, override ?? numberFormat),
    [numberFormat]
  );
}
