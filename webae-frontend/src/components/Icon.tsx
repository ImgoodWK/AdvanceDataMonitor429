import { memo, useEffect, useMemo, useRef, useState, type CSSProperties, type MouseEvent } from 'react';



import { useAppContext } from '@/context/AppContext';

import { useI18n } from '@/i18n';



import {

  buildIconUrl,
  bumpIconVersion,
  iconAbbrev,
  iconLookupIds,
  iconModeFallbackChain,
  iconReadyMatchesId,
  iconIsMarkedFailed,
  itemAbbrev,
  type IconReadyDetail,

  FLUID_ID_PREFIX,

} from '@/utils/icon';

import { getLocalIconBlobUrlForCandidates } from '@/utils/localIconPack';

import { debugLog } from '@/utils/debugLog';

import { openGtnhWikiSearch } from '@/utils/wiki';



interface IconProps {

  id?: string;

  item?: { itemId?: string; registryName?: string; displayName?: string; meta?: number };

  size?: number;

  className?: string;

  style?: CSSProperties;

  alt?: string;

  /** 点击图标打开 GTNH 中文 Wiki 搜索（默认开启） */

  linkToWiki?: boolean;

  /** Custom click handler when Wiki link is disabled or clickMode is 'custom'. */
  onIconClick?: (e: React.MouseEvent) => void;

}



/** Resolution: local pack → server pack (candidate chain) → abbreviation fallback. */

export const Icon = memo(function Icon({

  id,

  item,

  size = 32,

  className,

  style,

  alt,

  linkToWiki = true,

  onIconClick,

}: IconProps) {

  const { token, iconPack, iconRenderMode, localIconPack, iconCacheEnabled, failedIcons, markIconFailed, iconWikiEnabled } =
    useAppContext();

  const { t } = useI18n();



  const candidates = useMemo(() => iconLookupIds(item, id), [item, id]);
  const modeChain = useMemo(() => iconModeFallbackChain(iconRenderMode), [iconRenderMode]);
  const [candidateIndex, setCandidateIndex] = useState(0);
  const [modeIndex, setModeIndex] = useState(0);
  const [localUrl, setLocalUrl] = useState<string | null>(null);
  const [errored, setErrored] = useState(false);

  const [retryGen, setRetryGen] = useState(0);
  const retryAttemptRef = useRef(0);
  const retryTimerRef = useRef<number | null>(null);

  const activeId = candidates[candidateIndex] || candidates[0] || '';
  const activeMode = modeChain[modeIndex] || modeChain[0] || 'hybrid';

  const isFluid = activeId.startsWith(FLUID_ID_PREFIX);



  useEffect(() => {

    setCandidateIndex(0);
    setModeIndex(0);

    setErrored(false);
    setLocalUrl(null);
    retryAttemptRef.current = 0;
    if (retryTimerRef.current != null) {
      window.clearTimeout(retryTimerRef.current);
      retryTimerRef.current = null;
    }

  }, [candidates, iconRenderMode]);



  useEffect(() => {
    const onReady = (ev: Event) => {
      const detail = (ev as CustomEvent<IconReadyDetail>).detail;
      const matches = candidates.some((candidate) => iconReadyMatchesId(detail, candidate));
      if (!matches) return;
      retryAttemptRef.current = 0;
      setErrored(false);
      setCandidateIndex(0);
      setModeIndex(0);
      setRetryGen((g) => g + 1);
    };
    window.addEventListener('webae-icon-ready', onReady);
    return () => window.removeEventListener('webae-icon-ready', onReady);
  }, [candidates]);



  useEffect(() => {
    return () => {
      if (retryTimerRef.current != null) window.clearTimeout(retryTimerRef.current);
    };
  }, []);



  useEffect(() => {

    let cancelled = false;

    setLocalUrl(null);

    if (!localIconPack || candidates.length === 0) return;

    getLocalIconBlobUrlForCandidates(localIconPack, candidates).then((url) => {

      if (!cancelled) setLocalUrl(url);

    });

    return () => {

      cancelled = true;

    };

  }, [localIconPack, candidates]);



  const serverUrl = activeId
    && !iconIsMarkedFailed(failedIcons, activeId)
    ? buildIconUrl(activeId, iconPack, token, iconCacheEnabled, activeMode) + (retryGen ? `&r=${retryGen}` : '')
    : '';

  const url = localUrl || serverUrl;



  const wikiEnabled = linkToWiki && iconWikiEnabled && !!(item || id || alt) && !onIconClick;

  const wikiTitle = wikiEnabled ? t('iconWikiHint') : undefined;

  const handleClick = (e: MouseEvent) => {
    if (onIconClick) {
      e.preventDefault();
      e.stopPropagation();
      onIconClick(e);
      return;
    }
    if (!wikiEnabled) return;
    e.preventDefault();
    e.stopPropagation();
    openGtnhWikiSearch({
      displayName: item?.displayName,
      registryName: item?.registryName || (id && !id.startsWith(FLUID_ID_PREFIX) ? id : undefined),
      itemId: item?.itemId || id,
      fluidName: id?.startsWith(FLUID_ID_PREFIX) ? id.slice(FLUID_ID_PREFIX.length) : undefined,
      alt,
    });
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (onIconClick) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        e.stopPropagation();
        onIconClick(e as unknown as MouseEvent);
      }
      return;
    }
    if (!wikiEnabled) return;
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      e.stopPropagation();
      openGtnhWikiSearch({
        displayName: item?.displayName,
        registryName: item?.registryName || (id && !id.startsWith(FLUID_ID_PREFIX) ? id : undefined),
        itemId: item?.itemId || id,
        fluidName: id?.startsWith(FLUID_ID_PREFIX) ? id.slice(FLUID_ID_PREFIX.length) : undefined,
        alt,
      });
    }
  };



  const boxClass =

    'webae-icon-box' +

    (isFluid ? ' webae-icon-box-fluid' : '') +

    (wikiEnabled || onIconClick ? ' webae-icon-wiki-link' : '');

  const imgClass =

    'webae-icon' + (isFluid ? ' webae-icon-fluid' : '') + (className ? ' ' + className : '');



  if (!url || !activeId || errored) {

    const abbrev = id ? iconAbbrev(id) : itemAbbrev(item);

    return (

      <span

        className={'icon-fallback' + (wikiEnabled || onIconClick ? ' webae-icon-wiki-link' : '') + (className ? ' ' + className : '')}

        style={{ width: size, height: size, fontSize: size * 0.32, ...style }}

        role={wikiEnabled || onIconClick ? 'button' : 'img'}

        aria-label={alt || abbrev}

        title={wikiTitle || alt || activeId}

        onClick={wikiEnabled || onIconClick ? handleClick : undefined}

        onKeyDown={wikiEnabled || onIconClick ? handleKeyDown : undefined}

        tabIndex={wikiEnabled || onIconClick ? 0 : undefined}

      >

        {abbrev}

      </span>

    );

  }



  return (

    <span

      className={boxClass}

      style={{ width: size, height: size, flexShrink: 0, ...style }}

      title={wikiTitle || alt || activeId}

      role={wikiEnabled || onIconClick ? 'button' : undefined}

      onClick={wikiEnabled || onIconClick ? handleClick : undefined}

      onKeyDown={wikiEnabled || onIconClick ? handleKeyDown : undefined}

      tabIndex={wikiEnabled || onIconClick ? 0 : undefined}

    >

      <img

        src={url}

        width={size}

        height={size}

        alt={alt || activeId}

        className={imgClass}

        loading="lazy"

        draggable={false}

        onError={() => {

          debugLog(

            'icons',

            'warn',

            'icon load failed: activeId={} mode={} candidateIndex={}/{} modeIndex={}/{} serverUrl={} hasLocalUrl={}',

            activeId,

            activeMode,

            candidateIndex,

            candidates.length,

            modeIndex,

            modeChain.length,

            serverUrl?.substring(0, 120),

            !!localUrl

          );

          if (modeIndex + 1 < modeChain.length) {

            setModeIndex((i) => i + 1);

            return;

          }

          if (candidateIndex + 1 < candidates.length) {

            setCandidateIndex((i) => i + 1);

            setModeIndex(0);

            return;

          }

          setErrored(true);

          if (activeId && !localUrl && !failedIcons[activeId]) markIconFailed(activeId);

          if (iconIsMarkedFailed(failedIcons, activeId)) return;

          const delays = [2000, 5000, 10000];
          const attempt = retryAttemptRef.current;
          if (attempt < delays.length) {
            retryAttemptRef.current = attempt + 1;
            if (retryTimerRef.current != null) window.clearTimeout(retryTimerRef.current);
            retryTimerRef.current = window.setTimeout(() => {
              bumpIconVersion();
              setErrored(false);
              setCandidateIndex(0);
              setModeIndex(0);
              setRetryGen((g) => g + 1);
            }, delays[attempt]);
          }

        }}

      />

    </span>

  );

});

