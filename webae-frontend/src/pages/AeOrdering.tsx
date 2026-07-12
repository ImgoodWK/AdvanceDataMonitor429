import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  Card,
  Button,
  Tag,
  Space,
  Progress,
  Tabs,
  Tooltip,
  Modal,
  message,
} from 'antd';
import {
  ShoppingCartOutlined,
} from '@ant-design/icons';

import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { getApiClient } from '@/api/client';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import { OrderCpuSelect } from '@/components/ordering/OrderCpuSelect';
import { OrderHistorySection } from '@/components/ordering/OrderHistorySection';
import { OrderPatternsTab } from '@/components/ordering/OrderPatternsTab';
import { OrderQueryTab } from '@/components/ordering/OrderQueryTab';
import {
  BROWSE_PAGE_SIZE,
  formatStorageBytes,
  isGridPattern,
  orderProgress,
  orderProgressTooltip,
  patternEntryKey,
  patternMatchesQuery,
  renderCpuTooltip,
  type OrderTab,
  type PatternViewMode,
  type QueryHit,
  type QueryScope,
} from '@/components/ordering/orderUtils';
import { patternEntryIconId } from '@/utils/icon';
import { PatternDetailModal } from '@/components/patterns/PatternDetailModal';
import { PatternProductModal } from '@/components/patterns/PatternProductModal';
import { CraftTreePanel } from '@/components/patterns/CraftTreePanel';
import { PageShell } from '@/components/Layout/PageShell';
import { groupByPatternOutput, type PatternProductGroup } from '@/utils/patternGroup';
import { formatTime } from '@/utils/format';
import type {
  OrderResult,
  OrderStatus,
  OrderListResponse,
  OrderRequest,
  StorageCpu,
  PatternListEntryDto,
  PatternBrowseResponse,
} from '@/types/dto';
import type { ColumnsType } from 'antd/es/table';

export function AeOrderingPage() {
  const { selectedNetworks, notify, refreshTick, orderNavigation, setOrderNavigation, pauseRefreshWhenHidden } = useAppContext();
  const { t } = useI18n();
  const { storageMap } = useSnapshotData();
  const [api, contextHolder] = message.useMessage();

  const [activeTab, setActiveTab] = useState<OrderTab>('query');
  const [queryScope, setQueryScope] = useState<QueryScope>('output');
  const [search, setSearch] = useState('');
  const [amount, setAmount] = useState(1);
  const [submitting, setSubmitting] = useState(false);

  const [patterns, setPatterns] = useState<PatternListEntryDto[]>([]);
  const [browseTotal, setBrowseTotal] = useState(0);
  const [browseSources, setBrowseSources] = useState({ grid: 0, interface: 0 });
  const [browseCached, setBrowseCached] = useState(false);
  const [browseTimestamp, setBrowseTimestamp] = useState(0);
  const [loadingPatterns, setLoadingPatterns] = useState(false);
  const [loadingMorePatterns, setLoadingMorePatterns] = useState(false);
  const patternSearchDebounced = useRef('');
  const browseOffsetRef = useRef(0);
  const browseLoadingRef = useRef(false);
  const [patternSearch, setPatternSearch] = useState('');
  const [submittingPatterns, setSubmittingPatterns] = useState(false);
  const [detailPattern, setDetailPattern] = useState<PatternListEntryDto | null>(null);
  const [patternViewMode, setPatternViewMode] = useState<PatternViewMode>('byProduct');
  const [detailProductGroup, setDetailProductGroup] = useState<PatternProductGroup | null>(null);

  useEffect(() => {
    if (!orderNavigation) return;
    setActiveTab(orderNavigation.tab);
    if (orderNavigation.view) {
      setPatternViewMode(orderNavigation.view);
    }
    if (orderNavigation.search) {
      if (orderNavigation.tab === 'patterns') {
        setPatternSearch(orderNavigation.search);
      } else {
        setSearch(orderNavigation.search);
      }
    }
    setOrderNavigation(null);
  }, [orderNavigation, setOrderNavigation]);

  const [selectedCpu, setSelectedCpu] = useState<string | undefined>(undefined);
  const [activeOrders, setActiveOrders] = useState<OrderStatus[]>([]);
  const [orderHistory, setOrderHistory] = useState<OrderStatus[]>([]);
  const [autoRefreshOrders, setAutoRefreshOrders] = useState(true);
  const [reordering, setReordering] = useState(false);

  const currentNet = selectedNetworks[0] ?? 0;
  const storage = storageMap[currentNet];
  const cpus: StorageCpu[] = storage?.cpus || [];

  const cpuOptions = useMemo(() => {
    const opts = cpus.map((cpu, idx) => {
      const name = cpu.name || `CPU#${idx + 1}`;
      const cap = cpu.maxItems > 0 ? `${cpu.storedItems}/${cpu.maxItems}` : formatStorageBytes(cpu.availableStorage);
      const parallel = Math.max(1, (cpu.coProcessors || 0) + 1);
      const statusLabel = cpu.isBusy ? t('busy') : t('orderCpuIdle');
      return {
        value: name,
        label: `${name} · ${cap} · ×${parallel} · ${statusLabel}`,
        cpu,
      };
    });
    return [{ value: '', label: t('orderCpuAuto') }, ...opts];
  }, [cpus, t]);

  const resolvedCpuName = selectedCpu && selectedCpu.length > 0 ? selectedCpu : undefined;

  const fetchPatternsPage = useCallback(
    async (reset: boolean, searchQuery?: string) => {
      const q = searchQuery !== undefined ? searchQuery : patternSearchDebounced.current;
      if (browseLoadingRef.current) return;
      browseLoadingRef.current = true;
      if (reset) {
        setLoadingPatterns(true);
        browseOffsetRef.current = 0;
      } else {
        setLoadingMorePatterns(true);
      }
      try {
        const offset = reset ? 0 : browseOffsetRef.current;
        const data = await getApiClient().get<PatternBrowseResponse>(
          `/api/patterns/browse?network=${currentNet}&offset=${offset}&limit=${BROWSE_PAGE_SIZE}&source=both&q=${encodeURIComponent(q)}`
        );
        if (data.success) {
          const entries = data.entries || [];
          setPatterns((prev) => (reset ? entries : [...prev, ...entries]));
          setBrowseTotal(data.total ?? entries.length);
          setBrowseSources(data.sources || { grid: 0, interface: 0 });
          setBrowseCached(!!data.cached);
          setBrowseTimestamp(data.timestamp ?? 0);
          browseOffsetRef.current = offset + entries.length;
        }
      } catch (e) {
        notify((e as Error).message, 'error');
      } finally {
        browseLoadingRef.current = false;
        setLoadingPatterns(false);
        setLoadingMorePatterns(false);
      }
    },
    [currentNet, notify]
  );

  const fetchPatterns = useCallback(() => {
    fetchPatternsPage(true, patternSearchDebounced.current);
  }, [fetchPatternsPage]);

  const forceRefreshPatterns = useCallback(async () => {
    try {
      await getApiClient().post(`/api/patterns/browse/refresh?network=${currentNet}`);
      api.success(t('refreshSuccess'));
      fetchPatternsPage(true, patternSearchDebounced.current);
    } catch (e) {
      const msg = (e as Error).message || t('refreshAdminOnly');
      api.error(msg);
    }
  }, [currentNet, api, t, fetchPatternsPage]);

  useEffect(() => {
    const timer = setTimeout(() => {
      patternSearchDebounced.current = patternSearch.trim();
      fetchPatternsPage(true, patternSearchDebounced.current);
    }, 300);
    return () => clearTimeout(timer);
  }, [patternSearch, currentNet, fetchPatternsPage]);

  const filteredPatterns = useMemo(() => patterns, [patterns]);

  const productGroups = useMemo(() => groupByPatternOutput(filteredPatterns), [filteredPatterns]);

  const queryHits = useMemo((): QueryHit[] => {
    const q = search.trim().toLowerCase();
    if (!q) return [];

    const hits: QueryHit[] = [];
    const seen = new Set<string>();

    const push = (hit: QueryHit) => {
      if (seen.has(hit.key)) return;
      seen.add(hit.key);
      hits.push(hit);
    };

    if (queryScope === 'output') {
      for (const item of storage?.items || []) {
        const label = item.displayName || item.registryName;
        if (
          label.toLowerCase().includes(q) ||
          item.registryName.toLowerCase().includes(q)
        ) {
          push({
            key: `item:${item.itemId || item.registryName}`,
            label,
            subLabel: t('orderHitStorageItem'),
            iconId: item.itemId || item.registryName,
            item,
            orderName: label,
            kind: 'item',
          });
        }
      }
      for (const fluid of storage?.fluids || []) {
        if (fluid.fluidName.toLowerCase().includes(q)) {
          push({
            key: `fluid:${fluid.fluidName}`,
            label: fluid.fluidName,
            subLabel: t('orderHitStorageFluid'),
            iconId: `fluid:${fluid.fluidName}`,
            orderName: fluid.fluidName,
            kind: 'fluid',
          });
        }
      }
    }

    for (const p of patterns) {
      if (!patternMatchesQuery(p, q, queryScope)) continue;
      const out = p.outputs[0];
      const orderName = out?.displayName || out?.registryName || '';
      if (!orderName) continue;
      push({
        key: `pattern:${p.patternId}`,
        label: orderName,
        subLabel:
          queryScope === 'output'
            ? `${t('orderHitPatternOutput')} · ${p.sourceInterfaceName || p.sourceInterface}`
            : `${t('orderHitPatternInput')} · ${p.sourceInterfaceName || p.sourceInterface}`,
        iconId: patternEntryIconId(out),
        orderName,
        patternId: p.patternId,
        kind: 'pattern',
      });
    }

    return hits.slice(0, 24);
  }, [search, queryScope, storage, patterns, t]);

  const fetchOrderList = useCallback(async () => {
    try {
      const data = await getApiClient().get<OrderListResponse>('/api/order/list');
      if (data.success) {
        setActiveOrders(data.orders || []);
        setOrderHistory(data.history || []);
      }
    } catch {
      /* ignore */
    }
  }, []);

  const submitSingleOrder = useCallback(
    async (opts: {
      itemName: string;
      amount: number;
      patternId?: string;
      cpuName?: string;
    }) => {
      const body: OrderRequest = {
        networkId: currentNet,
        itemName: opts.itemName.trim(),
        amount: opts.amount,
        rawText: opts.itemName.trim(),
        locale: 'en_US',
        cpuName: opts.cpuName ?? resolvedCpuName,
        patternId: opts.patternId,
      };
      return getApiClient().post<OrderResult>('/api/order', body);
    },
    [currentNet, resolvedCpuName]
  );

  const placeOrder = useCallback(
    async (hit: QueryHit, qty: number) => {
      if (qty <= 0) return;
      setSubmitting(true);
      try {
        const result = await submitSingleOrder({
          itemName: hit.orderName.trim(),
          amount: qty,
          patternId: hit.patternId,
        });
        if (result.success) {
          notify(t('orderPlaced'), 'success');
          fetchOrderList();
        } else {
          notify(result.message || t('orderFailed'), 'error');
        }
      } catch (e) {
        notify((e as Error).message || t('orderFailed'), 'error');
      } finally {
        setSubmitting(false);
      }
    },
    [notify, t, fetchOrderList, submitSingleOrder]
  );

  const orderSinglePattern = useCallback(
    async (patternId: string, orderAmount: number, itemName?: string) => {
      if (orderAmount <= 0) return;
      setSubmittingPatterns(true);
      try {
        const result = await submitSingleOrder({
          itemName: itemName || '',
          amount: orderAmount,
          patternId,
        });
        if (result.success) {
          api.success(t('orderPlaced'));
          fetchOrderList();
        } else {
          api.error(result.message || t('orderFailed'));
        }
      } catch (e) {
        api.error((e as Error).message || t('orderFailed'));
      } finally {
        setSubmittingPatterns(false);
      }
    },
    [api, t, fetchOrderList, submitSingleOrder]
  );

  const orderPatternFromCard = useCallback(
    (p: PatternListEntryDto) => {
      const out = p.outputs[0];
      const orderName = out?.displayName || out?.registryName || '';
      if (isGridPattern(p)) {
        void placeOrder(
          {
            key: patternEntryKey(p),
            label: orderName,
            orderName,
            kind: 'item',
          },
          1
        );
        return;
      }
      void orderSinglePattern(p.patternId, 1, orderName);
    },
    [orderSinglePattern, placeOrder]
  );

  const cancelAllOrders = useCallback(async () => {
    try {
      await getApiClient().post('/api/order/cancel');
      setActiveOrders([]);
      fetchOrderList();
      notify(t('cancelAllDone'), 'info');
    } catch (e) {
      notify((e as Error).message, 'error');
    }
  }, [notify, t, fetchOrderList]);

  const reorderFromHistory = useCallback(
    (row: OrderStatus) => {
      const itemName = row.itemName || row.message?.replace(/\s+x\d+$/, '') || '';
      const qty = Math.max(1, Number(row.amount) || 1);
      if (!itemName && !row.patternId) {
        api.warning(t('orderReorderMissingData'));
        return;
      }
      Modal.confirm({
        title: t('orderReorderConfirmTitle'),
        content: t('orderReorderConfirmBody')
          .replace('{item}', itemName || row.patternId || '')
          .replace('{amount}', String(qty)),
        okText: t('orderReorder'),
        cancelText: t('cancel'),
        onOk: async () => {
          setReordering(true);
          try {
            const result = await submitSingleOrder({
              itemName,
              amount: qty,
              patternId: row.patternId,
              cpuName: row.cpuName || resolvedCpuName,
            });
            if (result.success) {
              api.success(t('orderPlaced'));
              fetchOrderList();
            } else {
              api.error(result.message || t('orderFailed'));
            }
          } catch (e) {
            api.error((e as Error).message || t('orderFailed'));
          } finally {
            setReordering(false);
          }
        },
      });
    },
    [api, t, submitSingleOrder, resolvedCpuName, fetchOrderList]
  );

  useEffect(() => {
    if (!autoRefreshOrders || refreshTick === 0) return;
    fetchOrderList();
  }, [refreshTick, autoRefreshOrders, fetchOrderList]);

  useVisibilityAwarePolling(
    fetchOrderList,
    autoRefreshOrders ? 3000 : null,
    pauseRefreshWhenHidden
  );

  const orderProgressPct = orderProgress;

  const orderColumns: ColumnsType<OrderStatus> = [
    {
      title: t('qty'),
      dataIndex: 'message',
      key: 'message',
      render: (v: string) => <span style={{ fontSize: '0.8rem' }}>{v}</span>,
    },
    {
      title: t('status'),
      dataIndex: 'status',
      key: 'status',
      render: (v: string) => {
        const color =
          v === 'completed' ? 'success' : v === 'crafting' ? 'processing' : v === 'cancelled' || v === 'failed' ? 'error' : 'default';
        const labelKey = `orderStatus_${v}`;
        const label = t(labelKey);
        return <Tag color={color}>{label !== labelKey ? label : v}</Tag>;
      },
    },
    {
      title: t('progress'),
      key: 'progressPercent',
      render: (_: unknown, row: OrderStatus) => {
        const pct = orderProgressPct(row);
        const tip = orderProgressTooltip(row, t);
        const barStatus =
          row.status === 'failed' || row.status === 'cancelled'
            ? 'exception'
            : row.status === 'pending' && pct === 0
              ? 'active'
              : undefined;
        return (
          <Tooltip title={<span style={{ whiteSpace: 'pre-line' }}>{tip}</span>}>
            <Progress percent={pct} size="small" status={barStatus} aria-valuenow={pct} />
          </Tooltip>
        );
      },
    },
    {
      title: t('orderColumnCpu'),
      dataIndex: 'cpuName',
      key: 'cpuName',
      render: (v: string | undefined, row: OrderStatus) => {
        const label = v || t('orderCpuAuto');
        const tip = renderCpuTooltip(row, t);
        return tip ? (
          <Tooltip title={tip}>
            <span style={{ fontSize: '0.75rem', cursor: 'help' }}>{label}</span>
          </Tooltip>
        ) : (
          <span style={{ fontSize: '0.75rem' }}>{label}</span>
        );
      },
    },
    {
      title: t('time'),
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      render: (v: number) => <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>{formatTime(v)}</span>,
    },
  ];

  const historyColumns: ColumnsType<OrderStatus> = [
    ...orderColumns,
    {
      title: t('actions'),
      key: 'actions',
      width: 120,
      render: (_: unknown, row: OrderStatus) => {
        if (row.status !== 'completed' && row.status !== 'failed') return null;
        return (
          <Button
            type="link"
            size="small"
            icon={<ShoppingCartOutlined />}
            loading={reordering}
            onClick={() => reorderFromHistory(row)}
          >
            {t('orderReorder')}
          </Button>
        );
      },
    },
  ];

  const quickAddProductGroup = useCallback(
    (group: PatternProductGroup, qty: number) => {
      if (qty <= 0 || group.patterns.length !== 1) return;
      const p = group.patterns[0];
      if (isGridPattern(p)) {
        const out = p.outputs[0];
        const orderName = out?.displayName || out?.registryName || group.primaryOutput.displayName || '';
        void placeOrder(
          {
            key: patternEntryKey(p),
            label: orderName,
            orderName,
            kind: 'item',
          },
          qty
        );
        return;
      }
      void orderSinglePattern(p.patternId, qty, group.primaryOutput.displayName || group.primaryOutput.registryName);
    },
    [orderSinglePattern, placeOrder]
  );

  const cpuSelectBlock = (
    <OrderCpuSelect
      selectedCpu={selectedCpu}
      onChange={setSelectedCpu}
      cpuOptions={cpuOptions}
    />
  );

  const queryTab = (
    <OrderQueryTab
      queryScope={queryScope}
      onQueryScopeChange={setQueryScope}
      search={search}
      onSearchChange={setSearch}
      queryHits={queryHits}
      amount={amount}
      onAmountChange={setAmount}
      submitting={submitting}
      onPlaceOrder={placeOrder}
    />
  );

  const patternsTab = (
    <OrderPatternsTab
      patternViewMode={patternViewMode}
      onPatternViewModeChange={setPatternViewMode}
      patternSearch={patternSearch}
      onPatternSearchChange={setPatternSearch}
      loadingPatterns={loadingPatterns}
      onRefreshPatterns={fetchPatterns}
      onForceRefreshPatterns={forceRefreshPatterns}
      browseCached={browseCached}
      browseTimestamp={browseTimestamp}
      productGroups={productGroups}
      filteredPatterns={filteredPatterns}
      browseTotal={browseTotal}
      browseSources={browseSources}
      loadingMorePatterns={loadingMorePatterns}
      onScrollEnd={() => fetchPatternsPage(false)}
      onSelectProductGroup={setDetailProductGroup}
      onQuickAddProduct={quickAddProductGroup}
      quickAddLoading={submitting || submittingPatterns}
      onPatternInfo={setDetailPattern}
      onOrderPattern={orderPatternFromCard}
    />
  );

  return (
    <PageShell title={t('aeOrdering')}>
      <Card>
      {contextHolder}
      {cpuSelectBlock}

      <Tabs
        activeKey={activeTab}
        onChange={(k) => setActiveTab(k as OrderTab)}
        style={{ marginTop: 16 }}
        items={[
          { key: 'query', label: t('orderByQuery'), children: queryTab },
          { key: 'patterns', label: t('orderByPattern'), children: patternsTab },
          { key: 'craftTree', label: t('craftTreeTab'), children: <CraftTreePanel networkId={currentNet} /> },
        ]}
      />

      <PatternDetailModal
        open={detailPattern != null}
        pattern={detailPattern}
        allPatterns={patterns}
        networkId={currentNet}
        onClose={() => setDetailPattern(null)}
        onSelectPattern={(p) => setDetailPattern(p)}
        t={t}
      />

      <PatternProductModal
        open={detailProductGroup != null}
        group={detailProductGroup}
        t={t}
        onClose={() => setDetailProductGroup(null)}
        onOrderSingle={orderSinglePattern}
      />

      <OrderHistorySection
        activeOrders={activeOrders}
        orderHistory={orderHistory}
        autoRefreshOrders={autoRefreshOrders}
        onAutoRefreshChange={setAutoRefreshOrders}
        onCancelAll={cancelAllOrders}
        columns={orderColumns}
        historyColumns={historyColumns}
      />
    </Card>
    </PageShell>
  );
}
