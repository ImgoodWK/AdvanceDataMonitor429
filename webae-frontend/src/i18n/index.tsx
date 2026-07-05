import { createContext, useContext, useCallback, type ReactNode } from 'react';
import { zh } from './zh';
import { en } from './en';

export type Lang = 'zh' | 'en';

const DICTS: Record<Lang, Record<string, string>> = { zh, en };

interface I18nContextValue {
  lang: Lang;
  t: (key: string, arg?: string | number) => string;
}

const I18nContext = createContext<I18nContextValue>({
  lang: 'en',
  t: (key) => key,
});

export function I18nProvider({
  lang,
  children,
}: {
  lang: Lang;
  children: ReactNode;
}) {
  const t = useCallback(
    (key: string, arg?: string | number) => {
      const dict = DICTS[lang] || DICTS.en;
      let s = dict[key];
      if (s === undefined) {
        // Fall back to English, then to the key itself
        s = DICTS.en[key] ?? key;
      }
      if (arg !== undefined && arg !== null) {
        s = s.replace(/\{n\}/g, String(arg)).replace(/\{name\}/g, String(arg));
      }
      return s;
    },
    [lang]
  );

  return (
    <I18nContext.Provider value={{ lang, t }}>{children}</I18nContext.Provider>
  );
}

export function useI18n() {
  return useContext(I18nContext);
}
