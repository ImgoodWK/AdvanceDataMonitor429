import { useEffect, useState } from 'react';

function readVisible(): boolean {
  if (typeof document === 'undefined') return true;
  return !document.hidden;
}

/**
 * Tracks whether the browser tab/window is visible to the user.
 * Listens to Page Visibility API plus focus/blur as a fallback.
 */
export function usePageVisibility(): boolean {
  const [visible, setVisible] = useState(readVisible);

  useEffect(() => {
    const sync = () => setVisible(readVisible());
    document.addEventListener('visibilitychange', sync);
    window.addEventListener('focus', sync);
    window.addEventListener('blur', sync);
    return () => {
      document.removeEventListener('visibilitychange', sync);
      window.removeEventListener('focus', sync);
      window.removeEventListener('blur', sync);
    };
  }, []);

  return visible;
}
