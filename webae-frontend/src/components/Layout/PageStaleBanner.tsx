import { useEffect, useState } from 'react';
import { Alert } from 'antd';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';

/**
 * Shows a banner when the active page's last fetch is older than 1.5× its poll interval.
 * Pages report fetch times via {@link AppContextValue.reportPageFetch}.
 */
export function PageStaleBanner() {
  const { activePage, pageFetchTimes } = useAppContext();
  const { t } = useI18n();
  const [staleSeconds, setStaleSeconds] = useState(0);

  const meta = pageFetchTimes[activePage];

  useEffect(() => {
    if (!meta) {
      setStaleSeconds(0);
      return;
    }
    const update = () => {
      const elapsed = Date.now() - meta.at;
      const threshold = meta.pollMs * 1.5;
      if (elapsed > threshold) {
        setStaleSeconds(Math.max(1, Math.floor(elapsed / 1000)));
      } else {
        setStaleSeconds(0);
      }
    };
    update();
    const id = window.setInterval(update, 1000);
    return () => window.clearInterval(id);
  }, [meta, activePage]);

  if (staleSeconds <= 0) return null;

  return (
    <Alert
      type="warning"
      showIcon
      banner
      message={t('pageDataStale', staleSeconds)}
      style={{ marginBottom: 12 }}
    />
  );
}
