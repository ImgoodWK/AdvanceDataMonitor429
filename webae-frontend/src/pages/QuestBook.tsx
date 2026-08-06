import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {

  Alert,

  Button,

  Empty,

  Space,

  Spin,

  Switch,

  Typography,

} from 'antd';

import {

  CompressOutlined,

  ReloadOutlined,

  UnorderedListOutlined,

  ZoomInOutlined,

  ZoomOutOutlined,

  SettingOutlined,

} from '@ant-design/icons';

import { getApiClient } from '@/api/client';

import { PageShell } from '@/components/Layout/PageShell';

import { QuestCytoscapeGraph, type QuestGraphHandle } from '@/components/quest/QuestCytoscapeGraph';

import { QuestDetailDrawer } from '@/components/quest/QuestDetailDrawer';

import { QuestDetailPanel } from '@/components/quest/QuestDetailPanel';

import { QuestLinePanel } from '@/components/quest/QuestLinePanel';

import { QuestListDrawer } from '@/components/quest/QuestListDrawer';

import { QuestSettingsDrawer } from '@/components/quest/QuestSettingsDrawer';

import {

  QUEST_PREVIEW_MODE_KEY,

  QUEST_REFRESH_CD_MS,

  computePreviewVisibleNodes,

  orderQuestNodes,

} from '@/components/quest/questUtils';

import { useQuestDisplay } from '@/hooks/useQuestDisplay';

import { QUEST_LAYOUT_SETTING_KEYS } from '@/types/questDisplay';

import { useAppContext } from '@/context/AppContext';

import { useI18n } from '@/i18n';

import { stripMcFormatting } from '@/utils/mcFormatting';

import type {

  QuestLineGraphDto,

  QuestLineNodeDto,

  QuestLineSummaryDto,

  QuestMetaDto,

  QuestProgressEntryDto,

} from '@/types/dto';



const { Text } = Typography;



export function QuestBookPage() {

  const { t } = useI18n();

  const { serverConfig, consumePageSearchPrefill } = useAppContext();

  const graphRef = useRef<QuestGraphHandle>(null);

  const [meta, setMeta] = useState<QuestMetaDto | null>(null);

  const [lines, setLines] = useState<QuestLineSummaryDto[]>([]);

  const [activeLineId, setActiveLineId] = useState<string | null>(null);

  const [graph, setGraph] = useState<QuestLineGraphDto | null>(null);

  const [progressMap, setProgressMap] = useState<Record<string, QuestProgressEntryDto>>({});

  const [progressUpdatedAt, setProgressUpdatedAt] = useState<number | null>(null);
  const [lineSubmittableCounts, setLineSubmittableCounts] = useState<Record<string, number>>({});
  const [lineCompletedCounts, setLineCompletedCounts] = useState<Record<string, number>>({});

  const [selectedQuestId, setSelectedQuestId] = useState<string | null>(null);

  const [narrowScreen, setNarrowScreen] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(max-width: 768px)').matches
  );

  const [listDrawerOpen, setListDrawerOpen] = useState(false);

  const [loading, setLoading] = useState(true);

  const [search, setSearch] = useState('');

  const [filter, setFilter] = useState<'all' | 'submit' | 'active'>('all');

  const [linePanelCollapsed, setLinePanelCollapsed] = useState(false);

  const [settingsOpen, setSettingsOpen] = useState(false);

  const [displaySettings, setDisplaySettings, resetDisplaySettings] = useQuestDisplay();

  const [previewMode, setPreviewMode] = useState(() => {

    try {

      return localStorage.getItem(QUEST_PREVIEW_MODE_KEY) !== '0';

    } catch {

      return true;

    }

  });

  const [refreshCdLeft, setRefreshCdLeft] = useState(0);

  const lastManualRefreshRef = useRef(0);

  const pendingCenterQuestRef = useRef<string | null>(null);



  const questEnabled = serverConfig?.questEnabled !== false;



  const loadMeta = useCallback(async () => {

    const res = await getApiClient().get<{ success: boolean; meta: QuestMetaDto }>('/api/quests/meta');

    setMeta(res.meta ?? null);

  }, []);



  const loadLines = useCallback(async () => {

    const res = await getApiClient().get<{ success: boolean; lines: QuestLineSummaryDto[] }>(

      '/api/quests/lines'

    );

    const list = res.lines ?? [];

    const sorted = [...list].sort((a, b) => a.order - b.order || a.name.localeCompare(b.name));

    setLines(list);

    setActiveLineId((prev) => prev ?? (sorted[0]?.lineId ?? null));

  }, []);



  const loadProgress = useCallback(async () => {

    const res = await getApiClient().get<{

      success: boolean;

      progress: {
        entries: QuestProgressEntryDto[];
        updatedAt?: number;
        lineSubmittableCounts?: Record<string, number>;
        lineCompletedCounts?: Record<string, number>;
      };

    }>('/api/quests/progress');

    const map: Record<string, QuestProgressEntryDto> = {};

    for (const e of res.progress?.entries ?? []) {

      map[e.questId] = e;

    }

    setProgressMap(map);

    setProgressUpdatedAt(res.progress?.updatedAt ?? Date.now());

    setLineSubmittableCounts(res.progress?.lineSubmittableCounts ?? {});

    setLineCompletedCounts(res.progress?.lineCompletedCounts ?? {});

  }, []);



  const loadGraph = useCallback(async (lineId: string) => {

    setLoading(true);

    try {

      const res = await getApiClient().get<{ success: boolean; line: QuestLineGraphDto }>(

        `/api/quests/lines/${lineId}`

      );

      setGraph(res.line ?? null);

    } catch {

      setGraph(null);

    } finally {

      setLoading(false);

    }

  }, []);



  /** Force refresh used before submit (no CD). */

  const forceRefreshProgress = useCallback(async () => {

    await loadProgress();

  }, [loadProgress]);



  const manualRefresh = useCallback(async () => {

    const now = Date.now();

    const elapsed = now - lastManualRefreshRef.current;

    if (elapsed < QUEST_REFRESH_CD_MS && lastManualRefreshRef.current > 0) {

      setRefreshCdLeft(Math.ceil((QUEST_REFRESH_CD_MS - elapsed) / 1000));

      return;

    }

    lastManualRefreshRef.current = now;

    setRefreshCdLeft(Math.ceil(QUEST_REFRESH_CD_MS / 1000));

    await loadProgress();

    if (activeLineId) await loadGraph(activeLineId);

  }, [activeLineId, loadGraph, loadProgress]);



  useEffect(() => {

    if (refreshCdLeft <= 0) return;

    const id = window.setInterval(() => {

      const left = Math.ceil(

        (QUEST_REFRESH_CD_MS - (Date.now() - lastManualRefreshRef.current)) / 1000

      );

      setRefreshCdLeft(left > 0 ? left : 0);

    }, 500);

    return () => window.clearInterval(id);

  }, [refreshCdLeft]);



  useEffect(() => {

    const mq = window.matchMedia('(max-width: 768px)');

    const handler = () => setNarrowScreen(mq.matches);

    mq.addEventListener('change', handler);

    return () => mq.removeEventListener('change', handler);

  }, []);



  useEffect(() => {

    if (!loading && graph && pendingCenterQuestRef.current) {

      const id = pendingCenterQuestRef.current;

      pendingCenterQuestRef.current = null;

      requestAnimationFrame(() => graphRef.current?.centerOnQuest(id));

    }

  }, [loading, graph]);



  useEffect(() => {

    void (async () => {

      try {

        await loadMeta();

        await loadLines();

        await loadProgress();

      } catch {

        /* ignore bootstrap errors — Alert below */

      }

    })();

  }, [loadMeta, loadLines, loadProgress]);



  useEffect(() => {

    if (activeLineId) void loadGraph(activeLineId);

  }, [activeLineId, loadGraph]);



  useEffect(() => {

    const prefill = consumePageSearchPrefill('quests');

    if (prefill?.query) {

      setSearch(prefill.query);

      setListDrawerOpen(true);

    }

  }, [consumePageSearchPrefill]);



  useEffect(() => {

    try {

      localStorage.setItem(QUEST_PREVIEW_MODE_KEY, previewMode ? '1' : '0');

    } catch {

      /* ignore */

    }

  }, [previewMode]);



  const sortedLines = useMemo(
    () => [...lines].sort((a, b) => a.order - b.order || a.name.localeCompare(b.name)),
    [lines]
  );



  const enrichedNodes = useMemo(() => {

    const nodes = graph?.nodes ?? [];

    return nodes.map((n) => {

      const p = progressMap[n.questId];

      return {

        ...n,

        state: p?.state ?? n.state,

        canSubmit: p?.canSubmit ?? n.canSubmit,

      } as QuestLineNodeDto;

    });

  }, [graph, progressMap]);



  const filteredNodes = useMemo(() => {

    const q = search.trim().toLowerCase();

    let result = enrichedNodes;
    if (!displaySettings.showGhostNodes) {
      result = result.filter((n) => !n.ghost);
    }
    if (!previewMode) {
      const visibleIds = computePreviewVisibleNodes(enrichedNodes, graph?.edges ?? []);
      result = result.filter((n) => visibleIds.has(n.questId));
    }

    return result.filter((n) => {

      if (filter === 'submit') return !!n.canSubmit;

      if (filter === 'active') return n.state === 'UNLOCKED' || n.state === 'UNCLAIMED';

      if (q) {
        const plainName = stripMcFormatting(n.name || '').toLowerCase();
        if (!plainName.includes(q) && !n.questId.toLowerCase().includes(q)) {
          return false;
        }
      }

      return true;

    });

  }, [enrichedNodes, filter, search, previewMode, displaySettings.showGhostNodes, graph?.edges]);



  const handleDisplaySettingsChange = useCallback(

    (patch: Partial<typeof displaySettings>) => {

      setDisplaySettings(patch);

      const layoutKeys = Object.keys(patch) as (keyof typeof displaySettings)[];

      const layoutChanged = layoutKeys.some((k) => QUEST_LAYOUT_SETTING_KEYS.includes(k));

      const autoFit = patch.autoFitOnSettingsChange ?? displaySettings.autoFitOnSettingsChange;

      if (autoFit && layoutChanged) {

        requestAnimationFrame(() => graphRef.current?.fitView());

      }

    },

    [setDisplaySettings, displaySettings.autoFitOnSettingsChange]

  );



  const handleDisplaySettingsReset = useCallback(() => {

    resetDisplaySettings();

    requestAnimationFrame(() => graphRef.current?.fitView());

  }, [resetDisplaySettings]);



  const filteredEdges = useMemo(() => {

    const ids = new Set(filteredNodes.map((n) => n.questId));

    return (graph?.edges ?? []).filter((e) => ids.has(e.fromQuestId) && ids.has(e.toQuestId));

  }, [graph, filteredNodes]);



  const orderedListNodes = useMemo(

    () => orderQuestNodes(filteredNodes.filter((n) => !n.ghost), filteredEdges),

    [filteredNodes, filteredEdges]

  );



  const openQuest = useCallback((id: string | null) => {

    setSelectedQuestId(id);

    if (id) graphRef.current?.centerOnQuest(id);

  }, []);



  const handleJumpQuest = useCallback(

    (id: string, lineId?: string) => {

      if (lineId && lineId !== activeLineId) {

        pendingCenterQuestRef.current = id;

        setActiveLineId(lineId);

      } else {

        graphRef.current?.centerOnQuest(id);

      }

      setSelectedQuestId(id);

    },

    [activeLineId]

  );



  const questNamesMap = useMemo(

    () => Object.fromEntries(enrichedNodes.map((n) => [n.questId, n.name])),

    [enrichedNodes]

  );



  const detailPanelProps = {

    questId: selectedQuestId,

    progressMap,

    questNames: questNamesMap,

    onJumpQuest: handleJumpQuest,

    onBeforeSubmit: forceRefreshProgress,

    onSubmitted: () => void forceRefreshProgress(),

  } as const;



  const onListSelect = useCallback(

    (id: string) => {

      openQuest(id);

    },

    [openQuest]

  );



  if (!questEnabled || (meta && !meta.questsAvailable)) {

    return (

      <PageShell title={t('questsPage')}>

        <Alert type="info" showIcon message={t('quest.modNotInstalled')} />

      </PageShell>

    );

  }



  return (

    <PageShell

      title={t('questsPage')}

      actions={

        <Space>

          {progressUpdatedAt ? (

            <Text type="secondary" style={{ fontSize: 12 }}>

              {t('quest.lastRefresh')}: {new Date(progressUpdatedAt).toLocaleTimeString()}

            </Text>

          ) : null}

          <Button

            icon={<ReloadOutlined />}

            disabled={refreshCdLeft > 0}

            onClick={() => void manualRefresh()}

          >

            {refreshCdLeft > 0 ? `${t('quest.refresh')} (${refreshCdLeft}s)` : t('quest.refresh')}

          </Button>

        </Space>

      }

    >

      <Space direction="vertical" size="middle" style={{ width: '100%' }}>

        <Space wrap align="center">

          <Button icon={<UnorderedListOutlined />} onClick={() => setListDrawerOpen(true)}>

            {t('quest.openTaskList')}

          </Button>

          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>

            <Switch checked={previewMode} onChange={setPreviewMode} size="small" />

            <Text type="secondary">{t('quest.previewMode')}</Text>

          </span>

          <Button icon={<ZoomInOutlined />} onClick={() => graphRef.current?.zoomIn()} />

          <Button icon={<ZoomOutOutlined />} onClick={() => graphRef.current?.zoomOut()} />

          <Button icon={<CompressOutlined />} onClick={() => graphRef.current?.fitView()} />

          <Button

            icon={<SettingOutlined />}

            onClick={() => setSettingsOpen(true)}

            aria-label={t('quest.settingsTitle')}

          />

          <Text type="secondary">{meta?.modVersion ? `BQ ${meta.modVersion}` : null}</Text>

        </Space>

        <div

          style={{

            display: 'flex',

            gap: 0,

            height: 'calc(100vh - 220px)',

            minHeight: 420,

            borderRadius: 8,

            overflow: 'hidden',

            border: '1px solid var(--border, var(--border-color, rgba(128,128,128,0.25)))',

            background: 'var(--bg-card)',

          }}

        >

          <QuestLinePanel

            lines={sortedLines}

            activeLineId={activeLineId}

            onSelect={setActiveLineId}

            width={displaySettings.linePanelWidth}

            lineIconSize={displaySettings.linePanelIconSize}

            lineFontSize={displaySettings.linePanelFontSize}

            collapsed={linePanelCollapsed}

            onToggleCollapsed={() => setLinePanelCollapsed((v) => !v)}

            previewMode={previewMode}

            lineSubmittableCounts={lineSubmittableCounts}

            lineCompletedCounts={lineCompletedCounts}

          />

          <div style={{ flex: 1, minWidth: 0, position: 'relative' }}>

            {loading ? (

              <div style={{ padding: 48, textAlign: 'center' }}>

                <Spin />

              </div>

            ) : filteredNodes.length === 0 ? (

              <Empty description={t('quest.noNodes')} style={{ marginTop: 80 }} />

            ) : (

              <QuestCytoscapeGraph

                ref={graphRef}

                nodes={filteredNodes}

                edges={filteredEdges}

                layoutKey={activeLineId}

                displaySettings={displaySettings}

                selectedQuestId={selectedQuestId}

                onNodeSelect={openQuest}

                onGhostLineJump={(lineId) => setActiveLineId(lineId)}

                height="100%"

              />

            )}

          </div>

          {!narrowScreen && selectedQuestId ? (

            <QuestDetailPanel

              {...detailPanelProps}

              width={displaySettings.detailPanelWidth}

              onClose={() => setSelectedQuestId(null)}

            />

          ) : null}

        </div>

      </Space>

      <QuestListDrawer

        open={listDrawerOpen}

        onClose={() => setListDrawerOpen(false)}

        nodes={orderedListNodes}

        selectedQuestId={selectedQuestId}

        search={search}

        onSearchChange={setSearch}

        filter={filter}

        onFilterChange={setFilter}

        onSelect={onListSelect}

      />

      {narrowScreen ? (

        <QuestDetailDrawer

          {...detailPanelProps}

          open={!!selectedQuestId}

          onClose={() => setSelectedQuestId(null)}

        />

      ) : null}

      <QuestSettingsDrawer

        open={settingsOpen}

        onClose={() => setSettingsOpen(false)}

        settings={displaySettings}

        onChange={handleDisplaySettingsChange}

        onReset={handleDisplaySettingsReset}

      />

    </PageShell>

  );

}

