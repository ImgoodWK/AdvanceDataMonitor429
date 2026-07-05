import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  Card,
  Input,
  InputNumber,
  Button,
  Table,
  Tag,
  Space,
  Empty,
  Progress,
  Checkbox,
  Tabs,
  Tooltip,
  Spin,
  Typography,
  Select,
  Segmented,
  message,
} from 'antd';
import {
  SearchOutlined,
  PlusOutlined,
  DeleteOutlined,
  ShoppingCartOutlined,
  ReloadOutlined,
} from '@ant-design/icons';

import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { getApiClient } from '@/api/client';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import { Icon } from '@/components/Icon';
import { VirtualPatternGrid } from '@/components/patterns/VirtualPatternGrid';
import { VirtualProductGrid } from '@/components/patterns/VirtualProductGrid';
import { PatternDetailModal } from '@/components/patterns/PatternDetailModal';
import { PatternProductModal } from '@/components/patterns/PatternProductModal';
import { OrderBatchPanel, type OrderBatchRow } from '@/components/patterns/OrderBatchPanel';
import { OrderTemplatesModal } from '@/components/patterns/OrderTemplatesModal';
import { CraftTreePanel } from '@/components/patterns/CraftTreePanel';
import {
  computeStorageGaps,
  fetchOrderTemplates,
  newTemplateId,
  saveOrderTemplates,
} from '@/utils/orderTemplates';
import { groupByPatternOutput, type PatternProductGroup } from '@/utils/patternGroup';
import { formatTime } from '@/utils/format';
import type {
  OrderResult,
  OrderStatus,
  OrderListResponse,
  OrderBatchRequest,
  OrderTemplate,
  StorageItem,
  PatternListEntryDto,
  PatternBrowseResponse,
  StorageCpu,
} from '@/types/dto';

const BROWSE_PAGE_SIZE = 80;

function patternEntryKey(p: PatternListEntryDto): string {
  return p.patternId || p.gridKey || '';
}

function isGridPattern(p: PatternListEntryDto): boolean {
  return p.source === 'grid';
}

const { Text } = Typography;

type OrderTab = 'query' | 'patterns' | 'craftTree';
type QueryScope = 'output' | 'input';
type PatternViewMode = 'byProduct' | 'byPattern';

interface BatchRow extends OrderBatchRow {}

interface QueryHit {
  key: string;
  label: string;
  subLabel?: string;
  iconId?: string;
  item?: StorageItem;
  orderName: string;
  patternId?: string;
  kind: 'item' | 'fluid' | 'pattern';
}

function formatStorageBytes(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

function renderCpuTooltip(status: OrderStatus, t: (k: string) => string): string | undefined {
  if (!status.cpuInfo) return undefined;
  return t('orderCpuTooltip')
    .replace('{co}', String(status.cpuInfo.coProcessors))
    .replace('{storage}', formatStorageBytes(status.cpuInfo.storage))
    .replace('{parallel}', String(status.cpuInfo.parallelism));
}

function patternEntryIconId(entry: { registryName: string; meta?: number; isFluid?: boolean } | null | undefined): string | undefined {
  if (!entry?.registryName) return undefined;
  if (entry.isFluid) return 'fluid:' + entry.registryName;
  return entry.meta && entry.meta > 0 ? `${entry.registryName}:${entry.meta}` : entry.registryName;
}

function patternMatchesQuery(p: PatternListEntryDto, q: string, scope: QueryScope): boolean {
  const entries = scope === 'output' ? p.outputs : (p.inputs || []).filter(Boolean);
  return entries.some((e) => {
    if (!e) return false;
    const name = `${e.displayName || ''} ${e.registryName || ''}`.toLowerCase();
    return name.includes(q);
  });
}

export function AeOrderingPage() {
  const { selectedNetworks, setSelectedNetworks, notify, refreshTick, orderNavigation, setOrderNavigation, pauseRefreshWhenHidden } = useAppContext();
  const { t } = useI18n();
  const { storageMap } = useSnapshotData();
  const [api, contextHolder] = message.useMessage();

  const [activeTab, setActiveTab] = useState<OrderTab>('query');
  const [queryScope, setQueryScope] = useState<QueryScope>('output');
  const [search, setSearch] = useState('');
  const [amount, setAmount] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [batchRows, setBatchRows] = useState<BatchRow[]>([{ key: '1', itemName: '', amount: 1 }]);

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
  const [selectedPatternIds, setSelectedPatternIds] = useState<Set<string>>(new Set());
  const [patternAmount, setPatternAmount] = useState(1);
  const [submittingPatterns, setSubmittingPatterns] = useState(false);
  const [detailPattern, setDetailPattern] = useState<PatternListEntryDto | null>(null);
  const [patternViewMode, setPatternViewMode] = useState<PatternViewMode>('byProduct');
  const [detailProductGroup, setDetailProductGroup] = useState<PatternProductGroup | null>(null);
  const batchKeySeq = useRef(1);

  const [orderTemplates, setOrderTemplates] = useState<OrderTemplate[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [templatesSaving, setTemplatesSaving] = useState(false);
  const [templateModalMode, setTemplateModalMode] = useState<'save' | 'manage' | null>(null);

  const loadOrderTemplates = useCallback(async () => {
    setTemplatesLoading(true);
    try {
      const list = await fetchOrderTemplates();
      setOrderTemplates(list);
    } catch (e) {
      notify((e as Error).message || t('orderTemplateLoadFailed'), 'error');
    } finally {
      setTemplatesLoading(false);
    }
  }, [notify, t]);

  useEffect(() => {
    void loadOrderTemplates();
  }, [loadOrderTemplates]);

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

  const currentNet = selectedNetworks[0] ?? 0;
  const storage = storageMap[currentNet];
  const cpus: StorageCpu[] = storage?.cpus || [];

  const cpuOptions = useMemo(() => {
    const opts = cpus.map((cpu, idx) => {
      const name = cpu.name || `CPU#${idx + 1}`;
      const cap = cpu.maxItems > 0 ? `${cpu.storedItems}/${cpu.maxItems}` : formatStorageBytes(cpu.availableStorage);
      const parallel = Math.max(1, (cpu.coProcessors || 0) + 1);
      const statusLabel = cpu.isBusy ? t('orderCpuBusy') : t('orderCpuIdle');
      return {
        value: name,
        label: `${name} · ${cap} · ×${parallel} · ${statusLabel}`,
        cpu,
      };
    });
    return [{ value: '', label: t('orderCpuAuto') }, ...opts];
  }, [cpus, t]);

  const resolvedCpuName = selectedCpu && selectedCpu.length > 0 ? selectedCpu : undefined;

  const patternById = useMemo(() => {
    const map = new Map<string, PatternListEntryDto>();
    for (const p of patterns) {
      const key = patternEntryKey(p);
      if (key) map.set(key, p);
    }
    return map;
  }, [patterns]);

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
      const primary =
        queryScope === 'output'
          ? p.outputs[0]
          : (p.inputs || []).find((e) => e && `${e.displayName || ''} ${e.registryName || ''}`.toLowerCase().includes(q));
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

  const togglePattern = (id: string) => {
    setSelectedPatternIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const selectAllPatterns = () =>
    setSelectedPatternIds(new Set(filteredPatterns.map((p) => patternEntryKey(p)).filter(Boolean)));
  const clearPatternSelection = () => setSelectedPatternIds(new Set());
  const selectAllProducts = () =>
    setSelectedPatternIds(
      new Set(productGroups.flatMap((g) => g.patterns.map((p) => patternEntryKey(p)).filter(Boolean)))
    );

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

  const placeOrder = useCallback(
    async (hit: QueryHit, qty: number) => {
      if (qty <= 0) return;
      setSubmitting(true);
      try {
        if (hit.patternId) {
          const results = await getApiClient().post<OrderResult[]>('/api/order/batch', {
            networkId: currentNet,
            cpuName: resolvedCpuName,
            items: [{ itemName: '', amount: qty, patternId: hit.patternId }],
          } as OrderBatchRequest);
          const ok = Array.isArray(results) && results[0]?.success;
          if (ok) {
            notify(t('orderPlaced'), 'success');
            fetchOrderList();
          } else {
            notify((Array.isArray(results) && results[0]?.message) || t('orderFailed'), 'error');
          }
          return;
        }

        const result = await getApiClient().post<OrderResult>('/api/order', {
          networkId: currentNet,
          itemName: hit.orderName.trim(),
          amount: qty,
          rawText: hit.orderName.trim(),
          locale: 'en_US',
          cpuName: resolvedCpuName,
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
    [currentNet, notify, t, resolvedCpuName, fetchOrderList]
  );

  const placeBatch = useCallback(async () => {
    const validRows = batchRows.filter((r) => (r.itemName.trim() || r.patternId) && r.amount > 0);
    if (validRows.length === 0) return;
    setSubmitting(true);
    try {
      const results = await getApiClient().post<OrderResult[]>('/api/order/batch', {
        networkId: currentNet,
        cpuName: resolvedCpuName,
        items: validRows.map((r) => ({
          itemName: r.patternId ? '' : r.itemName.trim(),
          amount: r.amount,
          patternId: r.patternId,
        })),
      } as OrderBatchRequest);
      const ok = Array.isArray(results) ? results.filter((r) => r.success).length : 0;
      const total = Array.isArray(results) ? results.length : 0;
      notify(`${ok}/${total} ${t('orderPlaced')}`, ok > 0 ? 'success' : 'error');
      if (ok > 0) fetchOrderList();
    } catch (e) {
      notify((e as Error).message || t('orderFailed'), 'error');
    } finally {
      setSubmitting(false);
    }
  }, [batchRows, currentNet, notify, t, resolvedCpuName, fetchOrderList]);

  const applyTemplateToBatch = useCallback(
    (template: OrderTemplate) => {
      if (template.networkId !== currentNet) {
        setSelectedNetworks([template.networkId]);
      }
      if (template.cpuName && template.cpuName.trim()) {
        setSelectedCpu(template.cpuName.trim());
      } else {
        setSelectedCpu(undefined);
      }
      const rows: BatchRow[] = template.items.map((item, idx) => ({
        key: String(idx + 1),
        itemName: item.itemName || '',
        amount: item.amount,
        patternId: item.patternId || undefined,
      }));
      if (rows.length === 0) {
        rows.push({ key: '1', itemName: '', amount: 1 });
      }
      batchKeySeq.current = rows.length;
      setBatchRows(rows);
    },
    [currentNet, setSelectedNetworks]
  );

  const handleSaveOrderTemplate = useCallback(
    async (name: string) => {
      const validRows = batchRows.filter((r) => (r.itemName.trim() || r.patternId) && r.amount > 0);
      if (validRows.length === 0) {
        api.warning(t('orderTemplateNoRows'));
        return;
      }
      setTemplatesSaving(true);
      try {
        const now = Date.now();
        const template: OrderTemplate = {
          id: newTemplateId(),
          name: name.trim(),
          cpuName: resolvedCpuName || '',
          networkId: currentNet,
          updatedAt: now,
          items: validRows.map((r) => ({
            itemName: r.itemName.trim(),
            amount: r.amount,
            patternId: r.patternId || null,
          })),
        };
        const next = [...orderTemplates.filter((x) => x.name !== template.name), template];
        const saved = await saveOrderTemplates(next);
        setOrderTemplates(saved);
        api.success(t('orderTemplateSaved'));
        setTemplateModalMode(null);
      } catch (e) {
        api.error((e as Error).message || t('orderTemplateSaveFailed'));
      } finally {
        setTemplatesSaving(false);
      }
    },
    [batchRows, resolvedCpuName, currentNet, orderTemplates, api, t]
  );

  const handleLoadOrderTemplate = useCallback(
    (template: OrderTemplate) => {
      applyTemplateToBatch(template);
      api.success(t('orderTemplateLoaded').replace('{name}', template.name));
      setTemplateModalMode(null);
    },
    [applyTemplateToBatch, api, t]
  );

  const handleRenameOrderTemplate = useCallback(
    async (id: string, name: string) => {
      const trimmed = name.trim();
      if (!trimmed) return;
      setTemplatesSaving(true);
      try {
        const next = orderTemplates.map((tpl) =>
          tpl.id === id ? { ...tpl, name: trimmed, updatedAt: Date.now() } : tpl
        );
        const saved = await saveOrderTemplates(next);
        setOrderTemplates(saved);
        api.success(t('orderTemplateRenamed'));
      } catch (e) {
        api.error((e as Error).message || t('orderTemplateSaveFailed'));
      } finally {
        setTemplatesSaving(false);
      }
    },
    [orderTemplates, api, t]
  );

  const handleDeleteOrderTemplate = useCallback(
    async (id: string) => {
      setTemplatesSaving(true);
      try {
        const next = orderTemplates.filter((tpl) => tpl.id !== id);
        const saved = await saveOrderTemplates(next);
        setOrderTemplates(saved);
        api.success(t('orderTemplateDeleted'));
      } catch (e) {
        api.error((e as Error).message || t('orderTemplateSaveFailed'));
      } finally {
        setTemplatesSaving(false);
      }
    },
    [orderTemplates, api, t]
  );

  const handleFillGapsFromTemplate = useCallback(
    (template: OrderTemplate) => {
      const net = template.networkId;
      const storageItems = storageMap[net]?.items;
      const gaps = computeStorageGaps(template.items, storageItems, false);
      if (gaps.length === 0) {
        api.info(t('orderTemplateNoGaps'));
        return;
      }
      if (net !== currentNet) {
        setSelectedNetworks([net]);
      }
      if (template.cpuName?.trim()) {
        setSelectedCpu(template.cpuName.trim());
      }
      const rows: BatchRow[] = gaps.map((g, idx) => ({
        key: String(idx + 1),
        itemName: g.itemName,
        amount: g.amount,
        patternId: g.patternId,
      }));
      batchKeySeq.current = rows.length;
      setBatchRows(rows);
      api.success(t('orderTemplateGapsFilled').replace('{n}', String(rows.length)));
      setTemplateModalMode(null);
    },
    [storageMap, currentNet, setSelectedNetworks, api, t]
  );

  const addPatternToBatch = (p: PatternListEntryDto) => {
    const out = p.outputs[0];
    const label = out?.displayName || out?.registryName || patternEntryKey(p);
    batchKeySeq.current += 1;
    setBatchRows((rows) => [
      ...rows,
      {
        key: String(batchKeySeq.current),
        itemName: label,
        amount: 1,
        patternId: isGridPattern(p) ? undefined : p.patternId,
      },
    ]);
    api.success(t('orderPatternAddedToBatch'));
  };

  const addPatternToBatchById = (patternId: string, label: string) => {
    batchKeySeq.current += 1;
    setBatchRows((rows) => [
      ...rows,
      { key: String(batchKeySeq.current), itemName: label, amount: 1, patternId },
    ]);
    api.success(t('orderPatternAddedToBatch'));
  };

  const orderSinglePattern = useCallback(
    async (patternId: string, amount: number) => {
      if (amount <= 0) return;
      setSubmittingPatterns(true);
      try {
        const results = await getApiClient().post<OrderResult[]>('/api/order/batch', {
          networkId: currentNet,
          cpuName: resolvedCpuName,
          items: [{ itemName: '', amount, patternId }],
        } as OrderBatchRequest);
        const ok = Array.isArray(results) && results[0]?.success;
        if (ok) {
          api.success(t('orderPlaced'));
          fetchOrderList();
        } else {
          api.error((Array.isArray(results) && results[0]?.message) || t('orderFailed'));
        }
      } catch (e) {
        api.error((e as Error).message || t('orderFailed'));
      } finally {
        setSubmittingPatterns(false);
      }
    },
    [currentNet, api, t, resolvedCpuName, fetchOrderList]
  );

  const placePatternBatch = useCallback(async () => {
    if (selectedPatternIds.size === 0) {
      api.warning(t('orderPatternNoSelection'));
      return;
    }
    if (patternAmount <= 0) {
      api.warning(t('orderPatternAmount'));
      return;
    }
    setSubmittingPatterns(true);
    try {
      const items = Array.from(selectedPatternIds).map((id) => {
        const p = patternById.get(id);
        if (p && isGridPattern(p)) {
          const out = p.outputs[0];
          return {
            itemName: out?.displayName || out?.registryName || p.displayName || '',
            amount: patternAmount,
          };
        }
        return { itemName: '', amount: patternAmount, patternId: id };
      });
      const results = await getApiClient().post<OrderResult[]>('/api/order/batch', {
        networkId: currentNet,
        cpuName: resolvedCpuName,
        items,
      } as OrderBatchRequest);
      const arr = Array.isArray(results) ? results : [];
      const ok = arr.filter((r) => r.success).length;
      if (ok > 0) {
        api.success(t('orderPatternSubmitSuccess').replace('{n}', String(ok)));
        clearPatternSelection();
        fetchOrderList();
      } else {
        api.error(t('orderPatternSubmitFailed'));
      }
      if (arr.some((r) => !r.success)) {
        const failed = arr.find((r) => !r.success);
        if (failed?.message) api.error(failed.message);
      }
    } catch (e) {
      api.error((e as Error).message || t('orderPatternSubmitFailed'));
    } finally {
      setSubmittingPatterns(false);
    }
  }, [selectedPatternIds, patternAmount, currentNet, api, t, resolvedCpuName, fetchOrderList, patternById]);

  const cancelAllOrders = useCallback(async () => {
    try {
      await getApiClient().post('/api/order/cancel');
      setActiveOrders([]);
      fetchOrderList();
      notify(t('cancelAll'), 'info');
    } catch (e) {
      notify((e as Error).message, 'error');
    }
  }, [notify, t, fetchOrderList]);

  useEffect(() => {
    if (!autoRefreshOrders || refreshTick === 0) return;
    fetchOrderList();
  }, [refreshTick, autoRefreshOrders, fetchOrderList]);

  useVisibilityAwarePolling(
    fetchOrderList,
    autoRefreshOrders ? 3000 : null,
    pauseRefreshWhenHidden
  );

  const orderProgress = (row: OrderStatus) =>
    row.status === 'completed' ? row.finalProgress ?? 100 : row.progressPercent;

  const orderColumns = [
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
        return <Tag color={color}>{v}</Tag>;
      },
    },
    {
      title: t('progress'),
      key: 'progressPercent',
      render: (_: unknown, row: OrderStatus) => (
        <Progress percent={orderProgress(row)} size="small" aria-valuenow={orderProgress(row)} />
      ),
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
      void orderSinglePattern(p.patternId, qty);
    },
    [orderSinglePattern, placeOrder]
  );

  const batchPanel = (
    <>
      <OrderBatchPanel
        batchRows={batchRows}
        setBatchRows={setBatchRows}
        batchKeySeq={batchKeySeq}
        submitting={submitting}
        onPlaceBatch={placeBatch}
        onSaveTemplate={() => setTemplateModalMode('save')}
        onManageTemplates={() => {
          void loadOrderTemplates();
          setTemplateModalMode('manage');
        }}
        t={t}
      />
      <OrderTemplatesModal
        open={templateModalMode !== null}
        mode={templateModalMode === 'save' ? 'save' : 'manage'}
        templates={orderTemplates}
        loading={templatesLoading}
        saving={templatesSaving}
        onClose={() => setTemplateModalMode(null)}
        onSave={handleSaveOrderTemplate}
        onLoad={handleLoadOrderTemplate}
        onRename={handleRenameOrderTemplate}
        onDelete={handleDeleteOrderTemplate}
        onFillGaps={handleFillGapsFromTemplate}
        t={t}
      />
    </>
  );

  const cpuSelectBlock = (
    <Space wrap align="start">
      <label htmlFor="order-cpu-select" style={{ fontSize: '0.85rem' }}>
        {t('orderCpuSelect')}:
      </label>
      <Select
        id="order-cpu-select"
        style={{ minWidth: 320 }}
        value={selectedCpu ?? ''}
        onChange={(v) => setSelectedCpu(v || undefined)}
        options={cpuOptions}
        optionRender={(opt) => {
          const cpu = (opt.data as { cpu?: StorageCpu }).cpu;
          if (!cpu) return opt.label;
          return (
            <Space direction="vertical" size={0} style={{ padding: '2px 0' }}>
              <Text strong style={{ fontSize: '0.85rem' }}>{cpu.name}</Text>
              <Text type="secondary" style={{ fontSize: '0.7rem' }}>
                {t('orderCpuStorage')}: {formatStorageBytes(cpu.availableStorage)} ·{' '}
                {t('orderCpuCoProcessors')}: {cpu.coProcessors} ·{' '}
                {t('orderCpuParallelism')}: {Math.max(1, cpu.coProcessors + 1)} ·{' '}
                {cpu.isBusy ? t('orderCpuBusy') : t('orderCpuIdle')}
              </Text>
            </Space>
          );
        }}
      />
    </Space>
  );

  const queryTab = (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Segmented
        value={queryScope}
        onChange={(v) => setQueryScope(v as QueryScope)}
        options={[
          { label: t('orderQueryByOutput'), value: 'output' },
          { label: t('orderQueryByInput'), value: 'input' },
        ]}
        aria-label={t('orderQueryScope')}
      />
      <Input
        placeholder={t('orderQueryPlaceholder')}
        prefix={<SearchOutlined />}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        allowClear
        style={{ width: '100%' }}
      />
      {search.trim() && queryHits.length > 0 && (
        <div style={{ maxHeight: 280, overflow: 'auto', border: '1px solid var(--border)', borderRadius: 4 }}>
          {queryHits.map((hit) => (
            <div
              key={hit.key}
              onClick={() => setSearch(hit.orderName)}
              style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 10px', cursor: 'pointer' }}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-hover)')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
            >
              {hit.iconId ? (
                <Icon id={hit.iconId} item={hit.item} size={28} alt={hit.label} />
              ) : (
                <span style={{ width: 28, height: 28 }} aria-hidden />
              )}
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: '0.85rem' }}>{hit.label}</div>
                {hit.subLabel && (
                  <Text type="secondary" style={{ fontSize: '0.7rem' }} ellipsis>
                    {hit.subLabel}
                  </Text>
                )}
              </div>
              <Button
                type="link"
                size="small"
                icon={<ShoppingCartOutlined />}
                loading={submitting}
                onClick={(e) => {
                  e.stopPropagation();
                  placeOrder(hit, amount);
                }}
              >
                {t('placeOrder')}
              </Button>
            </div>
          ))}
        </div>
      )}
      {search.trim() && queryHits.length === 0 && (
        <Empty description={t('orderQueryNoMatch')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}
      <Space>
        <InputNumber min={1} value={amount} onChange={(v) => setAmount(v || 1)} aria-label={t('qty')} />
        <Button
          type="primary"
          icon={<ShoppingCartOutlined />}
          loading={submitting}
          onClick={() => {
            const hit = queryHits.find((h) => h.orderName === search || h.label === search);
            if (hit) placeOrder(hit, amount);
            else if (search.trim()) {
              placeOrder({ key: 'manual', label: search, orderName: search, kind: 'item' }, amount);
            }
          }}
          disabled={!search.trim()}
        >
          {submitting ? t('submitting') : t('placeOrder')}
        </Button>
      </Space>

      {batchPanel}
    </Space>
  );

  const patternsTab = (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Space style={{ width: '100%' }} wrap>
        <Segmented
          value={patternViewMode}
          onChange={(v) => setPatternViewMode(v as PatternViewMode)}
          options={[
            { label: t('orderViewByProduct'), value: 'byProduct' },
            { label: t('orderViewByPattern'), value: 'byPattern' },
          ]}
          aria-label={t('orderViewMode')}
        />
        <Input
          placeholder={t('orderPatternSearchPlaceholder')}
          prefix={<SearchOutlined />}
          value={patternSearch}
          onChange={(e) => setPatternSearch(e.target.value)}
          allowClear
          style={{ width: 280 }}
        />
        <Button icon={<ReloadOutlined />} size="small" onClick={fetchPatterns} loading={loadingPatterns}>
          {t('orderPatternRefresh')}
        </Button>
        <Tooltip title={t('orderPatternForceRefreshHint')}>
          <Button size="small" onClick={() => void forceRefreshPatterns()}>
            {t('orderPatternForceRefresh')}
          </Button>
        </Tooltip>
        {browseCached ? (
          <Tag color="blue">{t('cached')}</Tag>
        ) : browseTimestamp > 0 ? (
          <Tag color="orange">{t('dataFreshness_stale')}</Tag>
        ) : null}
        <Button
          size="small"
          onClick={patternViewMode === 'byProduct' ? selectAllProducts : selectAllPatterns}
          disabled={patternViewMode === 'byProduct' ? productGroups.length === 0 : filteredPatterns.length === 0}
        >
          {t('orderPatternSelectAll')}
        </Button>
        <Button size="small" onClick={clearPatternSelection} disabled={selectedPatternIds.size === 0}>
          {t('orderPatternClearSelection')}
        </Button>
        <Text type="secondary" style={{ fontSize: '0.75rem' }}>
          {patternViewMode === 'byProduct'
            ? `${t('orderPatternSelected').replace('{n}', String(selectedPatternIds.size))} · ${productGroups.length} ${t('orderProductUnit')}`
            : `${t('orderPatternSelected').replace('{n}', String(selectedPatternIds.size))} · ${t('orderPatternLoaded').replace('{loaded}', String(filteredPatterns.length)).replace('{total}', String(browseTotal))} (Grid ${browseSources.grid} / IF ${browseSources.interface})`}
        </Text>
      </Space>
      <Space wrap>
        <span>{t('orderPatternAmount')}:</span>
        <InputNumber min={1} value={patternAmount} onChange={(v) => setPatternAmount(v || 1)} />
        <Button
          type="primary"
          icon={<ShoppingCartOutlined />}
          loading={submittingPatterns}
          onClick={placePatternBatch}
          disabled={selectedPatternIds.size === 0}
        >
          {t('orderPatternSubmit')}
        </Button>
      </Space>
      {loadingPatterns ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin tip={t('orderPatternLoading')} />
        </div>
      ) : filteredPatterns.length === 0 ? (
        <Empty description={t('orderPatternListEmpty')} />
      ) : patternViewMode === 'byProduct' ? (
        <VirtualProductGrid
          groups={productGroups}
          t={t}
          hasMore={filteredPatterns.length < browseTotal}
          loadingMore={loadingMorePatterns}
          onScrollEnd={() => fetchPatternsPage(false)}
          onSelectGroup={setDetailProductGroup}
          onQuickAdd={quickAddProductGroup}
          quickAddLoading={submitting || submittingPatterns}
        />
      ) : (
        <VirtualPatternGrid
          patterns={filteredPatterns}
          selectedIds={selectedPatternIds}
          t={t}
          hasMore={filteredPatterns.length < browseTotal}
          loadingMore={loadingMorePatterns}
          onScrollEnd={() => fetchPatternsPage(false)}
          onToggle={togglePattern}
          onInfo={setDetailPattern}
          onAddToBatch={addPatternToBatch}
        />
      )}
      {batchPanel}
    </Space>
  );

  return (
    <Card title={t('aeOrdering')}>
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
        onAddToBatch={addPatternToBatchById}
      />

      <div style={{ marginTop: 24 }} aria-live="polite">
        <Space style={{ marginBottom: 12 }}>
          <strong>{t('activeOrders')}</strong>
          <Checkbox checked={autoRefreshOrders} onChange={(e) => setAutoRefreshOrders(e.target.checked)}>
            {t('autoRefresh')}
          </Checkbox>
          <Button size="small" danger onClick={cancelAllOrders} disabled={activeOrders.length === 0}>
            {t('cancelAll')}
          </Button>
        </Space>
        {activeOrders.length > 0 ? (
          <Table dataSource={activeOrders} columns={orderColumns} rowKey="craftJobId" size="small" pagination={false} />
        ) : (
          <Empty description={t('noActiveOrders')} />
        )}
      </div>

      <div style={{ marginTop: 24 }}>
        <strong style={{ display: 'block', marginBottom: 12 }}>{t('orderHistory')}</strong>
        {orderHistory.length > 0 ? (
          <Table dataSource={orderHistory} columns={orderColumns} rowKey="craftJobId" size="small" pagination={{ pageSize: 10 }} />
        ) : (
          <Empty description={t('noOrderHistory')} />
        )}
      </div>
    </Card>
  );
}
