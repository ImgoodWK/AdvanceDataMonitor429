import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Card,
  Input,
  InputNumber,
  Button,
  Select,
  Switch,
  Space,
  Empty,
  Tag,
  Tooltip,
  Checkbox,
  Row,
  Col,
  Divider,
  AutoComplete,
  Spin,
  Modal,
  Typography,
} from 'antd';
import {
  SearchOutlined,
  PlusOutlined,
  DeleteOutlined,
  ThunderboltOutlined,
  DownloadOutlined,
  RollbackOutlined,
  SaveOutlined,
  ReloadOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { getApiClient } from '@/api/client';
import { Icon } from '@/components/Icon';
import { RecipeDetailModal } from '@/components/recipes/RecipeDetailModal';
import { groupByPrimaryOutput, resolveIconItemId } from '@/utils/recipe';
import { VirtualPatternList } from '@/components/patterns/VirtualPatternList';
import type {
  PatternListEntryDto,
  PatternListResponse,
  PatternDetailResponse,
  PatternDeleteResponse,
  PatternUpdateResponse,
  PatternEncodeResponse,
  RecipeDto,
  RecipeSearchResponse,
  RecipeSuggestEntry,
  RecipeSuggestResponse,
  InterfaceDto,
  InterfacesResponse,
} from '@/types/dto';

const { Text } = Typography;

interface InputSlot {
  registryName: string;
  displayName: string;
  meta: number;
  stackSize: number;
  isFluid: boolean;
}

interface OutputRow {
  key: string;
  registryName: string;
  displayName: string;
  stackSize: number;
  /** Original recipe output amount — multiplier cannot go below this. */
  originalStackSize: number;
  isFluid: boolean;
}

const MULTIPLIERS = [2, 4, 8, 16, 32, 64] as const;

/** 由 PatternItemEntry 生成图标 id（兼容流体前缀）。 */
function entryIconId(entry: { registryName: string; meta?: number; isFluid?: boolean } | null): string | undefined {
  if (!entry || !entry.registryName) return undefined;
  if (entry.isFluid) return 'fluid:' + entry.registryName;
  return entry.meta && entry.meta > 0 ? `${entry.registryName}:${entry.meta}` : entry.registryName;
}

/** 从 patternId 解析坐标与槽位（与后端格式一致：`<x>:<y>:<z>:<dim>#<slot>`）。 */
function parsePatternId(id: string): { x: number; y: number; z: number; dim: number; slot: number } | null {
  const hashIdx = id.indexOf('#');
  if (hashIdx < 0) return null;
  const coords = id.substring(0, hashIdx);
  const slotStr = id.substring(hashIdx + 1);
  const parts = coords.split(':');
  if (parts.length !== 4) return null;
  const nums = [...parts, slotStr].map((p) => Number(p));
  if (nums.some((n) => Number.isNaN(n))) return null;
  return { x: nums[0], y: nums[1], z: nums[2], dim: nums[3], slot: nums[4] };
}

export function PatternEditorPage() {
  const { selectedNetworks, notify, consumePageSearchPrefill } = useAppContext();
  const { t } = useI18n();

  // ---- 样板列表 ----
  const [patterns, setPatterns] = useState<PatternListEntryDto[]>([]);
  const [loadingList, setLoadingList] = useState(false);
  const [search, setSearch] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    const prefill = consumePageSearchPrefill('pattern');
    if (prefill?.query) setSearch(prefill.query);
  }, [consumePageSearchPrefill]);
  const [currentPattern, setCurrentPattern] = useState<PatternListEntryDto | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);

  // ---- 详情编辑面板 ----
  const [crafting, setCrafting] = useState(true);
  const [substitute, setSubstitute] = useState(false);
  const [beSubstitute, setBeSubstitute] = useState(false);
  const [author, setAuthor] = useState('');
  const [inputs, setInputs] = useState<(InputSlot | null)[]>(new Array(27).fill(null));
  const [outputs, setOutputs] = useState<OutputRow[]>([
    {
      key: '1',
      registryName: '',
      displayName: '',
      stackSize: 1,
      originalStackSize: 1,
      isFluid: false,
    },
  ]);
  const [encodedNbt, setEncodedNbt] = useState('');
  const [blankConsumedOnEncode, setBlankConsumedOnEncode] = useState(false);
  const [currentMultiplier, setCurrentMultiplier] = useState(1);
  const [dirty, setDirty] = useState(false);

  // 物品搜索（用于往槽位填物品 / 加输出）
  const [itemSearch, setItemSearch] = useState('');
  const [suggestOptions, setSuggestOptions] = useState<
    Array<{ value: string; label: string; entry: RecipeSuggestEntry }>
  >([]);
  const [recipeResults, setRecipeResults] = useState<RecipeDto[]>([]);
  const [recipeModalRecipes, setRecipeModalRecipes] = useState<RecipeDto[]>([]);
  const [recipeModalOpen, setRecipeModalOpen] = useState(false);
  const [pickTarget, setPickTarget] = useState<{ kind: 'slot'; slot: number } | { kind: 'output' } | null>(null);

  // 注入相关（保留：把当前编辑的样板注入到指定接口槽位）
  const [interfaces, setInterfaces] = useState<InterfaceDto[]>([]);
  const [selectedInterface, setSelectedInterface] = useState<string>('');
  const [injectSlot, setInjectSlot] = useState<number>(0);

  const currentNet = selectedNetworks[0] ?? 0;
  const outputKeySeq = useRef(1);

  // ---- 拉取样板列表 ----
  const fetchPatterns = useCallback(async () => {
    setLoadingList(true);
    try {
      const data = await getApiClient().get<PatternListResponse>(
        `/api/patterns?network=${currentNet}`
      );
      if (data.success) {
        setPatterns(data.patterns || []);
      } else {
        notify(data.message || t('patternListEmpty'), 'error');
      }
    } catch (e) {
      notify((e as Error).message, 'error');
    } finally {
      setLoadingList(false);
    }
  }, [currentNet, notify, t]);

  useEffect(() => {
    fetchPatterns();
  }, [fetchPatterns]);

  // ---- 拉取接口列表（用于注入） ----
  const fetchInterfaces = useCallback(async () => {
    try {
      const data = await getApiClient().get<InterfacesResponse>(
        `/api/interfaces?network=${currentNet}`
      );
      if (data.success) setInterfaces(data.interfaces || []);
    } catch {
      /* ignore */
    }
  }, [currentNet]);

  useEffect(() => {
    fetchInterfaces();
  }, [fetchInterfaces]);

  // ---- 物品搜索联想 ----
  useEffect(() => {
    if (!itemSearch.trim()) {
      setSuggestOptions([]);
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const data = await getApiClient().get<RecipeSuggestResponse>(
          `/api/recipes/suggest?q=${encodeURIComponent(itemSearch.trim())}&limit=15`
        );
        if (data.success && data.suggestions) {
          setSuggestOptions(
            data.suggestions.map((s) => ({
              value: s.registryName,
              label: `${s.displayName || s.registryName} (${s.registryName})`,
              entry: s,
            }))
          );
        }
      } catch {
        setSuggestOptions([]);
      }
    }, 250);
    return () => clearTimeout(timer);
  }, [itemSearch]);

  const searchRecipes = useCallback(async (registryOverride?: string) => {
    const term = (registryOverride || itemSearch).trim();
    if (!term) return;
    try {
      let data = await getApiClient().get<RecipeSearchResponse>(
        `/api/recipes/search?output=${encodeURIComponent(term)}`
      );
      if (data.success && (!data.results || data.results.length === 0)) {
        data = await getApiClient().get<RecipeSearchResponse>(
          `/api/recipes/search?q=${encodeURIComponent(term)}&limit=30`
        );
      }
      if (data.success) setRecipeResults(data.results || []);
    } catch {
      setRecipeResults([]);
    }
  }, [itemSearch]);

  // ---- 加载详情 ----
  const loadDetail = useCallback(
    async (pattern: PatternListEntryDto) => {
      setLoadingDetail(true);
      try {
        const data = await getApiClient().get<PatternDetailResponse>(
          `/api/patterns/${encodeURIComponent(pattern.patternId)}`
        );
        if (data.success && data.pattern) {
          applyPatternToEditor(data.pattern);
          setCurrentPattern(data.pattern);
          setDirty(false);
        } else {
          notify(data.message || t('patternNoSelection'), 'error');
        }
      } catch (e) {
        notify((e as Error).message, 'error');
      } finally {
        setLoadingDetail(false);
      }
    },
    [notify, t]
  );

  const applyPatternToEditor = (p: PatternListEntryDto) => {
    setCrafting(p.crafting);
    setSubstitute(p.substitute);
    setBeSubstitute(p.beSubstitute);
    setAuthor(p.author || '');
    const newInputs = new Array(27).fill(null) as (InputSlot | null)[];
    (p.inputs || []).forEach((inp, i) => {
      if (i < 27 && inp && inp.registryName) {
        newInputs[i] = {
          registryName: inp.registryName,
          displayName: inp.displayName || inp.registryName,
          meta: inp.meta ?? 0,
          stackSize: inp.stackSize > 0 ? inp.stackSize : 1,
          isFluid: inp.isFluid,
        };
      }
    });
    setInputs(newInputs);
    outputKeySeq.current = 1;
    setOutputs(
      (p.outputs || []).map((o) => ({
        key: String(outputKeySeq.current++),
        registryName: o.registryName,
        displayName: o.displayName,
        stackSize: o.stackSize,
        originalStackSize: o.stackSize > 0 ? o.stackSize : 1,
        isFluid: o.isFluid,
      }))
    );
    setEncodedNbt(p.encodedNbt || '');
    setBlankConsumedOnEncode(false);
    setCurrentMultiplier(1);
  };

  // ---- 模糊过滤 ----
  const filteredPatterns = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return patterns;
    return patterns.filter((p) => {
      const out = p.outputs
        .map((o) => `${o.displayName || ''} ${o.registryName || ''}`)
        .join(' ')
        .toLowerCase();
      const src = `${p.sourceInterfaceName || ''} ${p.sourceInterface || ''}`.toLowerCase();
      const auth = (p.author || '').toLowerCase();
      return out.includes(q) || src.includes(q) || auth.includes(q);
    });
  }, [patterns, search]);

  // ---- 多选 ----
  const toggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const selectAll = () => setSelectedIds(new Set(filteredPatterns.map((p) => p.patternId)));
  const clearSelection = () => setSelectedIds(new Set());

  // ---- 批量删除 ----
  const batchDelete = useCallback(async () => {
    if (selectedIds.size === 0) return;
    Modal.confirm({
      title: t('patternDeleteConfirm').replace('{n}', String(selectedIds.size)),
      okType: 'danger',
      okText: t('patternBatchDelete'),
      cancelText: t('patternBackToList'),
      onOk: async () => {
        let ok = 0;
        let failed = 0;
        for (const id of selectedIds) {
          try {
            const data = await getApiClient().delete<PatternDeleteResponse>(
              `/api/patterns/${encodeURIComponent(id)}`
            );
            if (data.success) ok++;
            else failed++;
          } catch {
            failed++;
          }
        }
        notify(t('patternDeleteSuccess').replace('{n}', String(ok)), 'success');
        if (failed > 0) notify(`${t('patternDeleteFailed')} (${failed})`, 'error');
        setSelectedIds(new Set());
        if (currentPattern && selectedIds.has(currentPattern.patternId)) {
          setCurrentPattern(null);
          resetEditor();
        }
        fetchPatterns();
      },
    });
  }, [selectedIds, notify, t, currentPattern, fetchPatterns]);

  // ---- 批量导出 ----
  const batchExport = useCallback(() => {
    if (selectedIds.size === 0) return;
    const selected = patterns.filter((p) => selectedIds.has(p.patternId));
    const blob = new Blob([JSON.stringify(selected, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `patterns_${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
    notify(t('patternExportSuccess').replace('{n}', String(selected.length)), 'success');
  }, [selectedIds, patterns, notify, t]);

  // ---- 单个删除（详情面板内） ----
  const deleteCurrent = useCallback(async () => {
    if (!currentPattern) return;
    Modal.confirm({
      title: t('patternDeleteConfirm').replace('{n}', '1'),
      okType: 'danger',
      okText: t('patternBatchDelete'),
      cancelText: t('patternBackToList'),
      onOk: async () => {
        try {
          const data = await getApiClient().delete<PatternDeleteResponse>(
            `/api/patterns/${encodeURIComponent(currentPattern.patternId)}`
          );
          if (data.success) {
            notify(t('patternDeleteSuccess').replace('{n}', '1'), 'success');
            setCurrentPattern(null);
            resetEditor();
            fetchPatterns();
          } else {
            notify(data.message || t('patternDeleteFailed'), 'error');
          }
        } catch (e) {
          notify((e as Error).message || t('patternDeleteFailed'), 'error');
        }
      },
    });
  }, [currentPattern, notify, t, fetchPatterns]);

  // ---- 重置编辑器 ----
  const resetEditor = () => {
    setCrafting(true);
    setSubstitute(false);
    setBeSubstitute(false);
    setAuthor('');
    setInputs(new Array(27).fill(null));
    outputKeySeq.current = 1;
    setOutputs([
      {
        key: '1',
        registryName: '',
        displayName: '',
        stackSize: 1,
        originalStackSize: 1,
        isFluid: false,
      },
    ]);
    setEncodedNbt('');
    setBlankConsumedOnEncode(false);
    setCurrentMultiplier(1);
    setDirty(false);
  };

  // ---- 新建样板 ----
  const newPattern = () => {
    setCurrentPattern(null);
    resetEditor();
  };

  const buildPatternPayload = useCallback(() => {
    const validOutputs = outputs.filter((o) => o.registryName.trim());
    return {
      patternId: 'web-' + Date.now(),
      crafting,
      substitute,
      beSubstitute,
      author,
      inputs: inputs.map((slot) =>
        slot
          ? {
              registryName: slot.registryName,
              displayName: slot.displayName || slot.registryName,
              meta: slot.meta,
              stackSize: slot.stackSize > 0 ? slot.stackSize : 1,
              isFluid: slot.isFluid,
            }
          : null
      ),
      outputs: validOutputs.map((o) => ({
        registryName: o.registryName,
        displayName: o.displayName,
        meta: 0,
        stackSize: o.stackSize,
        isFluid: o.isFluid,
      })),
    };
  }, [outputs, inputs, crafting, substitute, beSubstitute, author]);

  const encode = useCallback(
    async (consumeBlank: boolean) => {
      const validOutputs = outputs.filter((o) => o.registryName.trim());
      if (validOutputs.length === 0) {
        notify(t('patternEncoded'), 'error');
        return;
      }
      try {
        const data = await getApiClient().post<PatternEncodeResponse>('/api/pattern/encode', {
          ...buildPatternPayload(),
          networkId: currentNet,
          consumeBlank,
        });
        if (data.code === 'NO_BLANK_PATTERN') {
          notify(t('patternNoBlankPattern'), 'error');
          return;
        }
        const nbt = data.data?.encodedNbt || data.encodedNbt;
        if (data.success && nbt) {
          setEncodedNbt(nbt);
          setBlankConsumedOnEncode(consumeBlank);
          notify(t('patternEncoded'), 'success');
        } else if (!data.success) {
          notify(data.message || t('patternEncoded'), 'error');
        }
      } catch (e) {
        notify((e as Error).message, 'error');
      }
    },
    [outputs, buildPatternPayload, currentNet, notify, t]
  );

  // ---- 保存（PUT 回写已存在样板） ----
  const savePattern = useCallback(async () => {
    if (!currentPattern) return;
    if (!encodedNbt) {
      notify(t('patternSaveFailed'), 'error');
      return;
    }
    const pid = parsePatternId(currentPattern.patternId);
    if (!pid) {
      notify(t('patternSaveFailed'), 'error');
      return;
    }
    try {
      const data = await getApiClient().put<PatternUpdateResponse>(
        `/api/patterns/${encodeURIComponent(currentPattern.patternId)}`,
        {
          encodedNbt,
        }
      );
      if (data.success) {
        notify(t('patternSaveSuccess'), 'success');
        setDirty(false);
        fetchPatterns();
      } else {
        notify(data.message || t('patternSaveFailed'), 'error');
      }
    } catch (e) {
      notify((e as Error).message || t('patternSaveFailed'), 'error');
    }
  }, [currentPattern, encodedNbt, notify, t, fetchPatterns]);

  // ---- 注入到接口槽位（新建到指定接口 / 移动样板） ----
  const injectPattern = useCallback(async () => {
    if (!encodedNbt || !selectedInterface) return;
    const iface = interfaces.find((i) => `${i.x}_${i.y}_${i.z}_${i.dim}` === selectedInterface);
    if (!iface) return;
    try {
      const data = await getApiClient().post<{ success: boolean; result?: { success: boolean; message?: string }; message?: string }>(
        '/api/pattern/inject',
        {
          encodedNbt,
          interfaceX: iface.x,
          interfaceY: iface.y,
          interfaceZ: iface.z,
          interfaceDim: iface.dim,
          slotIndex: injectSlot,
          networkId: currentNet,
          consumeBlank: !blankConsumedOnEncode,
        }
      );
      const result = data.result;
      if (data.success && result?.success) {
        notify(t('patternInjectSuccess'), 'success');
        setBlankConsumedOnEncode(false);
        fetchPatterns();
        fetchInterfaces();
      } else {
        const msg = result?.message || data.message || t('patternInjectFailed');
        if (msg.includes('NO_BLANK_PATTERN')) {
          notify(t('patternNoBlankPattern'), 'error');
        } else {
          notify(msg, 'error');
        }
      }
    } catch (e) {
      notify((e as Error).message || t('patternInjectFailed'), 'error');
    }
  }, [encodedNbt, selectedInterface, interfaces, injectSlot, currentNet, blankConsumedOnEncode, notify, t, fetchPatterns, fetchInterfaces]);

  // ---- 槽位/输出操作 ----
  const fillSlotFromEntry = (slot: number, entry: RecipeSuggestEntry) => {
    const next = [...inputs];
    next[slot] = {
      registryName: entry.registryName,
      displayName: entry.displayName || entry.registryName,
      meta: 0,
      stackSize: 1,
      isFluid: false,
    };
    setInputs(next);
    setDirty(true);
  };

  const setSlot = (slot: number) => {
    if (!itemSearch.trim()) return;
    fillSlotFromEntry(slot, {
      registryName: itemSearch.trim(),
      displayName: itemSearch.trim(),
    });
  };

  const useRecipe = (recipe: RecipeDto) => {
    const newInputs = new Array(27).fill(null) as (InputSlot | null)[];
    recipe.inputs.forEach((inp, i) => {
      if (i < 27 && inp.registryName) {
        newInputs[i] = {
          registryName: inp.registryName,
          displayName: inp.displayName || inp.registryName,
          meta: inp.meta ?? 0,
          stackSize: inp.stackSize > 0 ? inp.stackSize : 1,
          isFluid: Boolean(inp.registryName.startsWith('fluid:')),
        };
      }
    });
    setInputs(newInputs);
    outputKeySeq.current = 1;
    setOutputs(
      recipe.outputs.map((o) => ({
        key: String(outputKeySeq.current++),
        registryName: o.registryName,
        displayName: o.displayName,
        stackSize: o.stackSize > 0 ? o.stackSize : 1,
        originalStackSize: o.stackSize > 0 ? o.stackSize : 1,
        isFluid: Boolean(o.registryName?.startsWith('fluid:')),
      }))
    );
    setCurrentMultiplier(1);
    setBlankConsumedOnEncode(false);
    setDirty(true);
  };

  const applyMultiplier = (factor: number) => {
    setOutputs((prev) =>
      prev.map((o) => ({
        ...o,
        stackSize: Math.max(o.originalStackSize, Math.round(o.stackSize * factor)),
      }))
    );
    setCurrentMultiplier((m) => m * factor);
    setDirty(true);
  };

  const divideMultiplier = () => {
    setOutputs((prev) =>
      prev.map((o) => ({
        ...o,
        stackSize: Math.max(o.originalStackSize, Math.floor(o.stackSize / 2)),
      }))
    );
    setCurrentMultiplier((m) => Math.max(1, Math.floor(m / 2)));
    setDirty(true);
  };

  const mergedRecipeGroups = useMemo(() => groupByPrimaryOutput(recipeResults), [recipeResults]);

  const selectedIface = useMemo(
    () => interfaces.find((i) => `${i.x}_${i.y}_${i.z}_${i.dim}` === selectedInterface),
    [interfaces, selectedInterface]
  );

  const markDirty = () => setDirty(true);

  // ---- 渲染样板列表项 ----
  const renderPatternItem = (p: PatternListEntryDto) => {
    const isSelected = selectedIds.has(p.patternId);
    const isActive = currentPattern?.patternId === p.patternId;
    const primaryOutput = p.outputs[0];
    const iconId = entryIconId(primaryOutput);
    const inputCount = (p.inputs || []).filter((i) => i && i.registryName).length;
    return (
      <div
        onClick={() => loadDetail(p)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '6px 8px',
          cursor: 'pointer',
          border: isActive ? '1px solid var(--accent)' : '1px solid var(--border)',
          borderRadius: 4,
          background: isActive ? 'var(--accent-dim)' : 'var(--bg-secondary)',
          marginBottom: 4,
          height: '100%',
          boxSizing: 'border-box',
        }}
      >
        <Checkbox
          checked={isSelected}
          onClick={(e) => {
            e.stopPropagation();
            toggleSelect(p.patternId);
          }}
          onChange={() => {}}
        />
        {iconId && <Icon id={iconId} size={28} alt={primaryOutput?.displayName || ''} />}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: '0.85rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {primaryOutput?.displayName || primaryOutput?.registryName || `#${p.slotIndex}`}
            {primaryOutput && primaryOutput.stackSize > 1 ? ` ×${primaryOutput.stackSize}` : ''}
          </div>
          <div style={{ fontSize: '0.7rem', color: 'var(--text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {p.sourceInterfaceName} · {t('patternSlot')} {p.slotIndex}
            {p.author ? ` · ${p.author}` : ''}
          </div>
        </div>
        <Space size={4}>
          {p.crafting && <Tag color="blue" style={{ fontSize: '0.65rem' }}>{t('crafting')}</Tag>}
          {p.substitute && <Tag color="orange" style={{ fontSize: '0.65rem' }}>S</Tag>}
          {p.beSubstitute && <Tag color="purple" style={{ fontSize: '0.65rem' }}>B</Tag>}
        </Space>
      </div>
    );
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--layout-card-gap, 14px)' }}>
      <Card
        title={t('patternEditor')}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} size="small" onClick={fetchPatterns} loading={loadingList}>
              {t('patternRefresh')}
            </Button>
            <Button icon={<PlusOutlined />} size="small" type="primary" onClick={newPattern}>
              {t('patternNew')}
            </Button>
          </Space>
        }
      >
        <Row gutter={16}>
          {/* 左侧：样板总览列表 */}
          <Col xs={24} lg={9}>
            <Space style={{ marginBottom: 8, width: '100%' }} direction="vertical" size={8}>
              <Input
                prefix={<SearchOutlined />}
                placeholder={t('patternSearchPlaceholder')}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                allowClear
              />
              <Space size={4} wrap>
                <Button size="small" onClick={selectAll} disabled={filteredPatterns.length === 0}>
                  {t('patternSelectAll')}
                </Button>
                <Button size="small" onClick={clearSelection} disabled={selectedIds.size === 0}>
                  {t('patternClearSelection')}
                </Button>
                <Text type="secondary" style={{ fontSize: '0.75rem' }}>
                  {t('patternSelected').replace('{n}', String(selectedIds.size))}
                </Text>
              </Space>
              {selectedIds.size > 0 && (
                <Space size={4}>
                  <Button size="small" danger icon={<DeleteOutlined />} onClick={batchDelete}>
                    {t('patternBatchDelete')}
                  </Button>
                  <Button size="small" icon={<DownloadOutlined />} onClick={batchExport}>
                    {t('patternBatchExport')}
                  </Button>
                </Space>
              )}
            </Space>
            <div style={{ maxHeight: 'calc(100vh - 320px)', minHeight: 240, overflow: 'auto', paddingRight: 4 }}>
              {loadingList ? (
                <div style={{ textAlign: 'center', padding: 24 }}>
                  <Spin tip={t('patternLoadingList')} />
                </div>
              ) : filteredPatterns.length === 0 ? (
                <Empty description={t('patternListEmpty')} />
              ) : (
                <VirtualPatternList patterns={filteredPatterns} renderItem={renderPatternItem} />
              )}
            </div>
          </Col>

          {/* 右侧：详情/编辑面板 */}
          <Col xs={24} lg={15}>
            <Spin spinning={loadingDetail}>
              {currentPattern ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <Space wrap>
                    <Text strong>{t('patternDetailTitle')}</Text>
                    <Tag>{t('patternSourceInterface')}: {currentPattern.sourceInterfaceName}</Tag>
                    <Tag>{t('patternSlot')}: {currentPattern.slotIndex}</Tag>
                    {currentPattern.author && <Tag color="cyan">{currentPattern.author || t('patternAuthorUnknown')}</Tag>}
                    {dirty && <Tag color="orange">●</Tag>}
                  </Space>
                  <Space size={8}>
                    <Button icon={<SaveOutlined />} type="primary" onClick={savePattern} disabled={!dirty || !encodedNbt}>
                      {t('patternSaveSuccess').replace('已保存', '保存')}
                    </Button>
                    <Button icon={<DeleteOutlined />} danger onClick={deleteCurrent}>
                      {t('patternBatchDelete')}
                    </Button>
                    <Button icon={<DownloadOutlined />} onClick={() => {
                      const blob = new Blob([JSON.stringify(currentPattern, null, 2)], { type: 'application/json' });
                      const url = URL.createObjectURL(blob);
                      const a = document.createElement('a');
                      a.href = url;
                      a.download = `pattern_${currentPattern.slotIndex}_${Date.now()}.json`;
                      a.click();
                      URL.revokeObjectURL(url);
                    }}>
                      {t('patternBatchExport')}
                    </Button>
                  </Space>
                </div>
              ) : (
                <div style={{ marginBottom: 8 }}>
                  <Text type="secondary">{t('patternNoSelection')}</Text>
                </div>
              )}

              {/* 开关 + 作者 */}
              <Space style={{ marginTop: 12, marginBottom: 12 }} wrap>
                <span>{t('theme')}:</span>
                <Switch
                  checkedChildren={t('crafting')}
                  unCheckedChildren={t('processing')}
                  checked={crafting}
                  onChange={(v) => { setCrafting(v); markDirty(); }}
                />
                <Switch
                  checkedChildren={t('substitute')}
                  checked={substitute}
                  onChange={(v) => { setSubstitute(v); markDirty(); }}
                />
                <Switch
                  checkedChildren={t('beSubstitute')}
                  checked={beSubstitute}
                  onChange={(v) => { setBeSubstitute(v); markDirty(); }}
                />
                <Input
                  placeholder={t('author')}
                  value={author}
                  onChange={(e) => { setAuthor(e.target.value); markDirty(); }}
                  style={{ width: 120 }}
                />
              </Space>

              <Divider>{t('patternInputs')}</Divider>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(9, 1fr)',
                  gap: 4,
                  maxWidth: 500,
                }}
              >
                {inputs.map((slot, idx) => (
                  <Tooltip
                    key={idx}
                    title={
                      slot
                        ? `${slot.displayName || slot.registryName} ×${slot.stackSize}`
                        : `${t('selectedSlot')} ${idx}`
                    }
                  >
                    <div
                      onClick={() => {
                        setPickTarget({ kind: 'slot', slot: idx });
                        setItemSearch(slot?.registryName || '');
                      }}
                      style={{
                        aspectRatio: '1',
                        border:
                          pickTarget?.kind === 'slot' && pickTarget.slot === idx
                            ? '2px solid var(--accent)'
                            : '1px solid var(--border)',
                        borderRadius: 4,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer',
                        background: 'var(--bg-secondary)',
                        position: 'relative',
                      }}
                    >
                      {slot && (
                        <>
                          <Icon
                            id={resolveIconItemId(slot)}
                            size={28}
                            alt={slot.displayName || slot.registryName}
                          />
                          {slot.stackSize > 1 && (
                            <span
                              style={{
                                position: 'absolute',
                                bottom: 2,
                                right: 2,
                                fontSize: '0.6rem',
                                fontWeight: 600,
                                color: 'var(--text-primary)',
                                background: 'var(--bg-primary)',
                                borderRadius: 2,
                                padding: '0 2px',
                                lineHeight: 1.2,
                              }}
                            >
                              {slot.stackSize}
                            </span>
                          )}
                        </>
                      )}
                      <span
                        style={{
                          position: 'absolute',
                          top: 1,
                          left: 2,
                          fontSize: '0.5rem',
                          color: 'var(--text-dim)',
                        }}
                      >
                        {idx}
                      </span>
                    </div>
                  </Tooltip>
                ))}
              </div>
              <Space style={{ marginTop: 8 }} size={8}>
                <Button size="small" onClick={() => { setInputs(new Array(27).fill(null)); markDirty(); }}>
                  {t('clearInputs')}
                </Button>
                <Text type="secondary" style={{ fontSize: '0.75rem' }}>
                  {t('patternInputsCount').replace('{n}', String(inputs.filter((i) => i).length))}
                </Text>
              </Space>

              <Divider>{t('patternOutputs')}</Divider>
              <Space wrap style={{ marginBottom: 8 }} align="center">
                <Text type="secondary" style={{ fontSize: '0.75rem' }}>
                  {t('patternMultiplier')}:
                </Text>
                {currentMultiplier > 1 && <Tag color="blue">×{currentMultiplier}</Tag>}
                {MULTIPLIERS.map((m) => (
                  <Button key={m} size="small" onClick={() => applyMultiplier(m)} aria-label={`×${m}`}>
                    ×{m}
                  </Button>
                ))}
                <Tooltip title={t('patternMultiplierDivide')}>
                  <Button size="small" onClick={divideMultiplier} aria-label={t('patternMultiplierDivide')}>
                    {t('patternMultiplierDivide')}
                  </Button>
                </Tooltip>
              </Space>
              {outputs.map((out, idx) => (
                <Space key={out.key} style={{ marginBottom: 8 }} align="center">
                  {out.registryName ? (
                    <Icon
                      id={resolveIconItemId(out)}
                      size={32}
                      alt={out.displayName || out.registryName}
                    />
                  ) : (
                    <div style={{ width: 32, height: 32 }} />
                  )}
                  <Input
                    placeholder={t('itemSearchPlaceholder')}
                    value={out.registryName}
                    onChange={(e) => {
                      const next = [...outputs];
                      next[idx] = {
                        ...out,
                        registryName: e.target.value,
                        displayName: e.target.value,
                        originalStackSize: out.originalStackSize || 1,
                      };
                      setOutputs(next);
                      markDirty();
                    }}
                    style={{ width: 220 }}
                  />
                  <InputNumber
                    min={out.originalStackSize}
                    value={out.stackSize}
                    onChange={(v) => {
                      const next = [...outputs];
                      const qty = v || out.originalStackSize;
                      next[idx] = {
                        ...out,
                        stackSize: Math.max(out.originalStackSize, qty),
                      };
                      setOutputs(next);
                      markDirty();
                    }}
                  />
                  <Button
                    icon={<DeleteOutlined />}
                    danger
                    onClick={() => {
                      setOutputs(outputs.filter((o) => o.key !== out.key));
                      markDirty();
                    }}
                    disabled={outputs.length === 1}
                    aria-label={t('patternBatchDelete')}
                  />
                </Space>
              ))}
              <Button
                icon={<PlusOutlined />}
                size="small"
                style={{ marginTop: 4 }}
                onClick={() => {
                  outputKeySeq.current += 1;
                  setOutputs([
                    ...outputs,
                    {
                      key: String(outputKeySeq.current),
                      registryName: '',
                      displayName: '',
                      stackSize: 1,
                      originalStackSize: 1,
                      isFluid: false,
                    },
                  ]);
                  markDirty();
                }}
              >
                {t('addOutput')}
              </Button>
              <Text type="secondary" style={{ fontSize: '0.75rem', marginLeft: 8 }}>
                {t('patternOutputsCount').replace('{n}', String(outputs.filter((o) => o.registryName).length))}
              </Text>

              {/* 物品搜索 + 配方查找 */}
              <Divider>{t('itemSearchPlaceholder')}</Divider>
              <Space style={{ marginBottom: 8, width: '100%' }}>
                <AutoComplete
                  style={{ width: '100%' }}
                  options={suggestOptions}
                  value={itemSearch}
                  onChange={setItemSearch}
                  onSelect={(_, opt) => {
                    if (opt && 'entry' in opt && opt.entry) {
                      const entry = opt.entry as RecipeSuggestEntry;
                      setItemSearch(entry.registryName);
                      // 直接填入当前选中目标
                      if (pickTarget?.kind === 'slot') {
                        fillSlotFromEntry(pickTarget.slot, entry);
                        setPickTarget(null);
                      } else if (pickTarget?.kind === 'output') {
                        const next = [...outputs];
                        next[next.length - 1] = {
                          ...next[next.length - 1],
                          registryName: entry.registryName,
                          displayName: entry.displayName || entry.registryName,
                          originalStackSize: next[next.length - 1].originalStackSize || 1,
                        };
                        setOutputs(next);
                        markDirty();
                        setPickTarget(null);
                      }
                      searchRecipes(entry.registryName);
                    }
                  }}
                >
                  <Input
                    placeholder={t('itemSearchPlaceholder')}
                    prefix={<SearchOutlined />}
                    onPressEnter={() => searchRecipes()}
                  />
                </AutoComplete>
              </Space>
              <Space style={{ marginBottom: 8 }} size={8}>
                <Button size="small" onClick={() => searchRecipes()}>{t('findRecipes')}</Button>
                {pickTarget?.kind === 'slot' && (
                  <Button size="small" type="primary" onClick={() => setSlot(pickTarget.slot)} disabled={!itemSearch.trim()}>
                    {t('setSlot')} {pickTarget.slot}
                  </Button>
                )}
                {pickTarget && (
                  <Button size="small" onClick={() => setPickTarget(null)}>
                    <RollbackOutlined /> {t('patternBackToList')}
                  </Button>
                )}
              </Space>
              {mergedRecipeGroups.length > 0 && (
                <div
                  style={{
                    maxHeight: 280,
                    overflow: 'auto',
                    border: '1px solid var(--border)',
                    borderRadius: 4,
                    padding: 4,
                  }}
                >
                  {mergedRecipeGroups.map((group) => {
                    const main = group.primaryOutput;
                    return (
                      <div
                        key={group.primaryOutputKey}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          padding: '6px 8px',
                          borderBottom: '1px solid var(--border-light)',
                          cursor: 'pointer',
                        }}
                        onClick={() => useRecipe(group.recipes[0])}
                      >
                        <Icon item={main} size={36} alt={main.displayName || main.registryName} />
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div
                            style={{
                              fontSize: '0.85rem',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap',
                            }}
                          >
                            {main.displayName || main.registryName}
                          </div>
                          <Text type="secondary" style={{ fontSize: '0.7rem' }}>
                            {group.recipes.length} {t('recipeTypes')}
                          </Text>
                        </div>
                        <Space size={4} onClick={(e) => e.stopPropagation()}>
                          <Button
                            size="small"
                            type="primary"
                            onClick={() => useRecipe(group.recipes[0])}
                          >
                            {t('patternRecipeAdd')}
                          </Button>
                          <Tooltip title={t('patternRecipeDetail')}>
                            <Button
                              size="small"
                              icon={<InfoCircleOutlined />}
                              aria-label={t('patternRecipeDetail')}
                              onClick={() => {
                                setRecipeModalRecipes(group.recipes);
                                setRecipeModalOpen(true);
                              }}
                            />
                          </Tooltip>
                        </Space>
                      </div>
                    );
                  })}
                </div>
              )}

              <RecipeDetailModal
                open={recipeModalOpen}
                recipes={recipeModalRecipes}
                onClose={() => setRecipeModalOpen(false)}
                onApplyRecipe={useRecipe}
                applyLabel={t('useRecipe')}
                t={t}
              />

              <Divider />
              <Space wrap>
                <Button type="primary" icon={<ThunderboltOutlined />} onClick={() => encode(true)}>
                  {t('patternEncodeEditor')}
                </Button>
                <Button icon={<SaveOutlined />} onClick={savePattern} disabled={!currentPattern || !encodedNbt}>
                  {t('patternSaveSuccess').replace('已保存', '保存')}
                </Button>
                {encodedNbt && (
                  <Tag color="success" style={{ maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    NBT: {encodedNbt.substring(0, 40)}...
                  </Tag>
                )}
              </Space>

              {/* 注入到接口槽位（保留：把当前编辑的样板注入到指定接口槽位，用于新建/移动） */}
              <Divider>{t('selectInterface')}</Divider>
              <Space style={{ marginBottom: 16 }} wrap direction="vertical" size={8}>
                <Select
                  placeholder={t('selectMEInterface')}
                  value={selectedInterface || undefined}
                  onChange={setSelectedInterface}
                  style={{ width: '100%', maxWidth: 480 }}
                  options={interfaces.map((iface) => {
                    const patternCount = iface.existingPatterns?.length
                      ?? iface.slots?.filter((s) => s.occupied).length
                      ?? 0;
                    const recipeType =
                      iface.machineRecipeType
                      || iface.targetRecipePool
                      || iface.targetMachineName
                      || '';
                    const coord = `(${iface.x},${iface.y},${iface.z})`;
                    const suffix = [
                      coord,
                      t('patternInterfacePatternCount').replace('{n}', String(patternCount)),
                      recipeType,
                    ]
                      .filter(Boolean)
                      .join(' · ');
                    return {
                      label: `${iface.name} — ${suffix}`,
                      value: `${iface.x}_${iface.y}_${iface.z}_${iface.dim}`,
                    };
                  })}
                />
                {selectedIface && (
                  <div style={{ width: '100%', maxWidth: 480 }}>
                    {selectedIface.machineRecipeType || selectedIface.targetRecipePool ? (
                      <Text type="secondary" style={{ fontSize: '0.8rem', display: 'block', marginBottom: 4 }}>
                        {t('patternInterfaceRecipeType')}:{' '}
                        {selectedIface.machineRecipeType
                          || `${selectedIface.targetMachineName} / ${selectedIface.targetRecipePool}`}
                      </Text>
                    ) : null}
                    {(selectedIface.existingPatterns?.length ?? 0) > 0 && (
                      <>
                        <Text strong style={{ fontSize: '0.8rem' }}>
                          {t('patternInterfacePatterns')}
                        </Text>
                        <div
                          style={{
                            maxHeight: 160,
                            overflow: 'auto',
                            marginTop: 4,
                            border: '1px solid var(--border)',
                            borderRadius: 4,
                            padding: 4,
                          }}
                        >
                          {selectedIface.existingPatterns!.map((pat) => {
                            const out = pat.outputs[0];
                            return (
                              <div
                                key={pat.patternId}
                                style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 6,
                                  padding: '4px 6px',
                                  fontSize: '0.75rem',
                                }}
                              >
                                <Tag style={{ margin: 0 }}>{t('patternSlot')} {pat.slotIndex}</Tag>
                                {out && (
                                  <Icon item={out} size={20} alt={out.displayName || out.registryName} />
                                )}
                                <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                  {out?.displayName || out?.registryName || pat.patternId}
                                  {out && out.stackSize > 1 ? ` ×${out.stackSize}` : ''}
                                </span>
                                {pat.crafting ? (
                                  <Tag color="blue" style={{ margin: 0, fontSize: '0.65rem' }}>
                                    {t('crafting')}
                                  </Tag>
                                ) : (
                                  <Tag style={{ margin: 0, fontSize: '0.65rem' }}>{t('processing')}</Tag>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      </>
                    )}
                  </div>
                )}
                <Space wrap>
                <Select
                  value={injectSlot}
                  onChange={setInjectSlot}
                  style={{ width: 120 }}
                  options={Array.from({ length: 36 }, (_, i) => ({ label: `${t('selectedSlot')} ${i}`, value: i }))}
                />
                <Button
                  icon={<DownloadOutlined />}
                  onClick={injectPattern}
                  disabled={!encodedNbt || !selectedInterface}
                >
                  {t('injectPattern')}
                </Button>
                </Space>
              </Space>
            </Spin>
          </Col>
        </Row>
      </Card>
    </div>
  );
}
