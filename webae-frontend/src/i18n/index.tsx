import { createContext, useContext, useCallback, type ReactNode } from 'react';
import { zh } from './zh';
import { en } from './en';

export type Lang = 'zh' | 'en';
export type I18nParams = string | number | Record<string, string | number>;

const DICTS: Record<Lang, Record<string, string>> = { zh, en };

interface I18nContextValue {
  lang: Lang;
  t: (key: string, params?: I18nParams) => string;
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
    (key: string, params?: I18nParams) => {
      const dict = DICTS[lang] || DICTS.en;
      let s = dict[key];
      if (s === undefined) {
        s = DICTS.en[key] ?? key;
      }
      if (params !== undefined && params !== null) {
        if (typeof params === 'object') {
          for (const [k, v] of Object.entries(params)) {
            s = s.replace(new RegExp(`\\{${k}\\}`, 'g'), String(v));
          }
        } else {
          s = s.replace(/\{n\}/g, String(params)).replace(/\{name\}/g, String(params));
        }
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
