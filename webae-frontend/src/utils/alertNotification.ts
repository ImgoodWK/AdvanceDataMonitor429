import { notification } from 'antd';
import type { WebAlertDto } from '@/types/dto';

const STORAGE_KEY = 'webae-alert-notified-ids';
const MAX_STORED_IDS = 1000;

export function alertOccurrenceKey(a: WebAlertDto): string {
  if (a.id) return a.id;
  return `${a.type}:${a.sourceKey ?? ''}`;
}

function loadNotifiedIds(): Set<string> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return new Set();
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return new Set();
    return new Set(parsed.filter((v): v is string => typeof v === 'string'));
  } catch {
    return new Set();
  }
}

function saveNotifiedIds(ids: Set<string>): void {
  const arr = Array.from(ids);
  const trimmed = arr.length > MAX_STORED_IDS ? arr.slice(arr.length - MAX_STORED_IDS) : arr;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
  } catch {
    /* quota or private mode */
  }
}

/** Mark alert occurrence ids as already notified (no popup). */
export function markAlertsNotified(alerts: WebAlertDto[]): void {
  if (alerts.length === 0) return;
  const ids = loadNotifiedIds();
  for (const alert of alerts) {
    ids.add(alertOccurrenceKey(alert));
  }
  saveNotifiedIds(ids);
}

export function wasAlertNotified(alert: WebAlertDto): boolean {
  return loadNotifiedIds().has(alertOccurrenceKey(alert));
}

type NotifyFn = (message: string, type: 'success' | 'error' | 'warning' | 'info') => void;

/**
 * Show toast / browser notification once per alert occurrence id.
 * Returns true if a popup was shown.
 */
export function notifyAlertOnce(alert: WebAlertDto, notify: NotifyFn): boolean {
  if (alert.browserNotify === false) return false;
  const key = alertOccurrenceKey(alert);
  const ids = loadNotifiedIds();
  if (ids.has(key)) return false;
  ids.add(key);
  saveNotifiedIds(ids);

  const severity =
    alert.severity === 'error' ? 'error' : alert.severity === 'info' ? 'info' : 'warning';
  notify(alert.message || alert.title, severity);
  notification[severity === 'error' ? 'error' : severity === 'info' ? 'info' : 'warning']({
    message: alert.title || 'WebAE Alert',
    description: alert.message,
    placement: 'topRight',
    duration: alert.type === 'order_complete' ? 6 : 4.5,
    key,
  });
  if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
    try {
      new Notification(alert.title || 'WebAE', { body: alert.message, tag: key });
    } catch {
      /* ignore */
    }
  }
  return true;
}
