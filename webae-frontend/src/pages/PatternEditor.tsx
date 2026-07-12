import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Card,
  Button,
  Space,
  Tag,
  Checkbox,
  Row,
  Col,
  Spin,
  Modal,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  DownloadOutlined,
  SaveOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { getApiClient } from '@/api/client';
import { Icon } from '@/components/Icon';
import { groupByPrimaryOutput } from '@/utils/recipe';
import { SelectableListRow } from '@/components/common/SelectableListRow';
import { PageShell } from '@/components/Layout/PageShell';
import { PatternListSidebar } from '@/components/patterns/PatternListSidebar';
import { PatternInjectPanel } from '@/components/patterns/PatternInjectPanel';
import { PatternEditorForm } from '@/components/patterns/PatternEditorForm';
import type {
  PatternEditorInputSlot,
  PatternEditorOutputRow,
} from '@/components/patterns/patternEditorTypes';
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

import { patternEntryIconId } from '@/utils/icon';
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
  const [inputs, setInputs] = useState<(PatternEditorInputSlot | null)[]>(new Array(27).fill(null));
  const [outputs, setOutputs] = useState<PatternEditorOutputRow[]>([
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
    const newInputs = new Array(27).fill(null) as (PatternEditorInputSlot | null)[];
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
        notify(t('patternEncodeFailed'), 'error');
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
          notify(data.message || t('patternEncodeFailed'), 'error');
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
    const newInputs = new Array(27).fill(null) as (PatternEditorInputSlot | null)[];
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

  const handleSuggestSelect = useCallback(
    (entry: RecipeSuggestEntry) => {
      setItemSearch(entry.registryName);
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
    },
    [pickTarget, outputs, searchRecipes]
  );

  const handleSlotClick = useCallback(
    (slot: number) => {
      setPickTarget({ kind: 'slot', slot });
      setItemSearch(inputs[slot]?.registryName || '');
    },
    [inputs]
  );

  const handleOutputChange = useCallback((index: number, row: PatternEditorOutputRow) => {
    const next = [...outputs];
    next[index] = row;
    setOutputs(next);
    markDirty();
  }, [outputs]);

  const handleAddOutput = useCallback(() => {
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
  }, [outputs]);

  // ---- 渲染样板列表项 ----
  const renderPatternItem = (p: PatternListEntryDto) => {
    const isSelected = selectedIds.has(p.patternId);
    const isActive = currentPattern?.patternId === p.patternId;
    const primaryOutput = p.outputs[0];
    const iconId = patternEntryIconId(primaryOutput);
    return (
      <SelectableListRow
        variant="card"
        as="div"
        active={isActive}
        onClick={() => loadDetail(p)}
        leading={
          <>
            <Checkbox
              checked={isSelected}
              onClick={(e) => {
                e.stopPropagation();
                toggleSelect(p.patternId);
              }}
              onChange={() => {}}
            />
            {iconId ? <Icon id={iconId} size={28} alt={primaryOutput?.displayName || ''} /> : null}
          </>
        }
        trailing={
          <Space size={4}>
            {p.crafting && <Tag color="blue" style={{ fontSize: '0.65rem' }}>{t('crafting')}</Tag>}
            {p.substitute && <Tag color="orange" style={{ fontSize: '0.65rem' }}>S</Tag>}
            {p.beSubstitute && <Tag color="purple" style={{ fontSize: '0.65rem' }}>B</Tag>}
          </Space>
        }
      >
        <div className="webae-list-row-title">
          {primaryOutput?.displayName || primaryOutput?.registryName || `#${p.slotIndex}`}
          {primaryOutput && primaryOutput.stackSize > 1 ? ` ×${primaryOutput.stackSize}` : ''}
        </div>
        <div className="webae-text-2xs webae-list-row-subtitle">
          {p.sourceInterfaceName} · {t('patternSlot')} {p.slotIndex}
          {p.author ? ` · ${p.author}` : ''}
        </div>
      </SelectableListRow>
    );
  };

  return (
    <PageShell
      title={t('patternEditor')}
      actions={
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
      <Card>
        <Row gutter={16}>
          {/* 左侧：样板总览列表 */}
          <Col xs={24} lg={9}>
            <PatternListSidebar
              search={search}
              onSearchChange={setSearch}
              selectedCount={selectedIds.size}
              onSelectAll={selectAll}
              onClearSelection={clearSelection}
              onBatchDelete={batchDelete}
              onBatchExport={batchExport}
              loadingList={loadingList}
              patterns={filteredPatterns}
              renderItem={renderPatternItem}
            />
          </Col>

          {/* 右侧：详情/编辑面板 */}
          <Col xs={24} lg={15}>
            <Spin spinning={loadingDetail}>
              {currentPattern ? (
                <div className="webae-pattern-detail-header">
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
                <div className="webae-pattern-no-selection">
                  <Text type="secondary">{t('patternNoSelection')}</Text>
                </div>
              )}

              <PatternEditorForm
                t={t}
                crafting={crafting}
                onCraftingChange={(v) => { setCrafting(v); markDirty(); }}
                substitute={substitute}
                onSubstituteChange={(v) => { setSubstitute(v); markDirty(); }}
                beSubstitute={beSubstitute}
                onBeSubstituteChange={(v) => { setBeSubstitute(v); markDirty(); }}
                author={author}
                onAuthorChange={(v) => { setAuthor(v); markDirty(); }}
                inputs={inputs}
                onClearInputs={() => { setInputs(new Array(27).fill(null)); markDirty(); }}
                onSlotClick={handleSlotClick}
                outputs={outputs}
                onOutputChange={handleOutputChange}
                onRemoveOutput={(key) => {
                  setOutputs(outputs.filter((o) => o.key !== key));
                  markDirty();
                }}
                onAddOutput={handleAddOutput}
                currentMultiplier={currentMultiplier}
                onApplyMultiplier={applyMultiplier}
                onDivideMultiplier={divideMultiplier}
                itemSearch={itemSearch}
                onItemSearchChange={setItemSearch}
                suggestOptions={suggestOptions}
                onSuggestSelect={handleSuggestSelect}
                pickTarget={pickTarget}
                onPickTargetClear={() => setPickTarget(null)}
                onSearchRecipes={() => searchRecipes()}
                onSetSlot={setSlot}
                mergedRecipeGroups={mergedRecipeGroups}
                onUseRecipe={useRecipe}
                onOpenRecipeDetail={(recipes) => {
                  setRecipeModalRecipes(recipes);
                  setRecipeModalOpen(true);
                }}
                recipeModalOpen={recipeModalOpen}
                recipeModalRecipes={recipeModalRecipes}
                onRecipeModalClose={() => setRecipeModalOpen(false)}
                encodedNbt={encodedNbt}
                onEncode={encode}
                onSavePattern={savePattern}
                canSave={Boolean(currentPattern && encodedNbt)}
              />

              <PatternInjectPanel
                interfaces={interfaces}
                selectedInterface={selectedInterface}
                onSelectedInterfaceChange={setSelectedInterface}
                selectedIface={selectedIface}
                injectSlot={injectSlot}
                onInjectSlotChange={setInjectSlot}
                encodedNbt={encodedNbt}
                onInject={injectPattern}
              />
            </Spin>
          </Col>
        </Row>
      </Card>
    </PageShell>
  );
}
