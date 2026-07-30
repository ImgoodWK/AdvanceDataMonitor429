import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Checkbox, Modal, Space, Spin, Tabs, Tag, Typography } from 'antd';
import {
  DeleteOutlined,
  DownloadOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { Icon } from '@/components/Icon';
import { PageShell } from '@/components/Layout/PageShell';
import { SelectableListRow } from '@/components/common/SelectableListRow';
import { PatternEditorForm } from '@/components/patterns/PatternEditorForm';
import { PatternInterfaceWorkspace } from '@/components/patterns/PatternInterfaceWorkspace';
import { PatternListSidebar } from '@/components/patterns/PatternListSidebar';
import { PatternRecipeSidebar } from '@/components/patterns/PatternRecipeSidebar';
import type {
  PatternEditorInputSlot,
  PatternEditorOutputRow,
} from '@/components/patterns/patternEditorTypes';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { patternEntryIconId } from '@/utils/icon';
import {
  interfaceAddress,
  patternEntryToInput,
  recipeToPatternDraft,
} from '@/utils/patternEditor';
import type {
  InterfaceDto,
  InterfacesResponse,
  PatternBufferResponse,
  PatternCompatResponse,
  PatternDeleteResponse,
  PatternDetailResponse,
  PatternEncodeResponse,
  PatternInjectResponse,
  PatternListEntryDto,
  PatternListResponse,
  PatternMutationResponse,
  PatternUpdateResponse,
  RecipeDto,
  RecipeSuggestEntry,
  RecipeSuggestResponse,
} from '@/types/dto';

const { Text } = Typography;

function blankOutput(key = '1'): PatternEditorOutputRow {
  return {
    key,
    registryName: '',
    displayName: '',
    meta: 0,
    stackSize: 1,
    originalStackSize: 1,
    isFluid: false,
  };
}

export function PatternEditorPage() {
  const { selectedNetworks, notify, consumePageSearchPrefill } = useAppContext();
  const { t } = useI18n();
  const currentNet = selectedNetworks[0] ?? 0;

  const [patterns, setPatterns] = useState<PatternListEntryDto[]>([]);
  const [loadingList, setLoadingList] = useState(false);
  const [search, setSearch] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [currentPattern, setCurrentPattern] = useState<PatternListEntryDto | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [busy, setBusy] = useState(false);

  const [crafting, setCrafting] = useState(true);
  const [substitute, setSubstitute] = useState(false);
  const [beSubstitute, setBeSubstitute] = useState(false);
  const [author, setAuthor] = useState('');
  const [programmableHatches, setProgrammableHatches] = useState(false);
  const [programmableHatchesInstalled, setProgrammableHatchesInstalled] = useState(false);
  const [inputs, setInputs] = useState<(PatternEditorInputSlot | null)[]>(new Array(27).fill(null));
  const [outputs, setOutputs] = useState<PatternEditorOutputRow[]>([blankOutput()]);
  const [encodedNbt, setEncodedNbt] = useState('');
  const [currentMultiplier, setCurrentMultiplier] = useState(1);
  const [dirty, setDirty] = useState(false);
  const outputKeySeq = useRef(1);

  const [itemSearch, setItemSearch] = useState('');
  const [suggestOptions, setSuggestOptions] = useState<
    Array<{ value: string; label: string; entry: RecipeSuggestEntry }>
  >([]);
  const [pickTarget, setPickTarget] = useState<{ kind: 'slot'; slot: number } | null>(null);

  const [interfaces, setInterfaces] = useState<InterfaceDto[]>([]);
  const [bufferEntries, setBufferEntries] = useState<PatternBufferResponse['entries']>([]);
  const [selectedInterfaceId, setSelectedInterfaceId] = useState('');
  const [selectedSlot, setSelectedSlot] = useState(0);
  const [selectedBufferId, setSelectedBufferId] = useState('');

  useEffect(() => {
    const prefill = consumePageSearchPrefill('pattern');
    if (prefill?.query) setSearch(prefill.query);
  }, [consumePageSearchPrefill]);

  const markDirty = useCallback(() => {
    setDirty(true);
    setEncodedNbt('');
  }, []);

  const fetchPatterns = useCallback(async (force = false, retry = true) => {
    setLoadingList(true);
    try {
      const data = await getApiClient().get<PatternListResponse>(
        `/api/patterns?network=${currentNet}${force ? '&refresh=1' : ''}`
      );
      if (data.success) {
        setPatterns(data.patterns || []);
        if (data.cached === false && retry) {
          window.setTimeout(() => fetchPatterns(false, false), 650);
        }
      }
      else notify(data.message || t('patternListEmpty'), 'error');
    } catch (error) {
      notify((error as Error).message, 'error');
    } finally {
      setLoadingList(false);
    }
  }, [currentNet, notify, t]);

  const fetchWorkspace = useCallback(async () => {
    try {
      const [interfaceData, bufferData] = await Promise.all([
        getApiClient().get<InterfacesResponse>(`/api/interfaces?network=${currentNet}`),
        getApiClient().get<PatternBufferResponse>(`/api/pattern-buffer?network=${currentNet}`),
      ]);
      const nextInterfaces = interfaceData.success ? interfaceData.interfaces || [] : [];
      setInterfaces(nextInterfaces);
      setBufferEntries(bufferData.success ? bufferData.entries || [] : []);
      setSelectedInterfaceId((previous) => {
        if (previous && nextInterfaces.some((iface) => interfaceAddress(iface) === previous)) return previous;
        return nextInterfaces[0] ? interfaceAddress(nextInterfaces[0]) : '';
      });
      setSelectedBufferId((previous) => {
        const entries = bufferData.success ? bufferData.entries || [] : [];
        return previous && entries.some((entry) => entry.id === previous) ? previous : entries[0]?.id || '';
      });
    } catch {
      setInterfaces([]);
    }
  }, [currentNet]);

  const refreshAll = useCallback(async () => {
    await Promise.all([fetchPatterns(true), fetchWorkspace()]);
  }, [fetchPatterns, fetchWorkspace]);

  useEffect(() => {
    fetchPatterns();
    fetchWorkspace();
  }, [fetchPatterns, fetchWorkspace]);

  useEffect(() => {
    getApiClient()
      .get<PatternCompatResponse>('/api/pattern/compat')
      .then((data) => setProgrammableHatchesInstalled(Boolean(data.programmableHatches?.installed)))
      .catch(() => setProgrammableHatchesInstalled(false));
  }, []);

  useEffect(() => {
    if (!itemSearch.trim()) {
      setSuggestOptions([]);
      return;
    }
    const timer = window.setTimeout(async () => {
      try {
        const data = await getApiClient().get<RecipeSuggestResponse>(
          `/api/recipes/suggest?q=${encodeURIComponent(itemSearch.trim())}&limit=15`
        );
        setSuggestOptions(
          data.success
            ? (data.suggestions || []).map((entry) => ({
                value: entry.registryName,
                label: `${entry.displayName || entry.registryName} (${entry.registryName})`,
                entry,
              }))
            : []
        );
      } catch {
        setSuggestOptions([]);
      }
    }, 300);
    return () => window.clearTimeout(timer);
  }, [itemSearch]);

  const resetEditor = useCallback(() => {
    setCrafting(true);
    setSubstitute(false);
    setBeSubstitute(false);
    setAuthor('');
    setProgrammableHatches(false);
    setInputs(new Array(27).fill(null));
    outputKeySeq.current = 1;
    setOutputs([blankOutput()]);
    setEncodedNbt('');
    setCurrentMultiplier(1);
    setDirty(false);
    setPickTarget(null);
    setItemSearch('');
  }, []);

  const applyPatternToEditor = useCallback((pattern: PatternListEntryDto) => {
    setCrafting(pattern.crafting);
    setSubstitute(pattern.substitute);
    setBeSubstitute(pattern.beSubstitute);
    setAuthor(pattern.author || '');
    setProgrammableHatches(Boolean(pattern.programmableHatches));
    const nextInputs = new Array<PatternEditorInputSlot | null>(27).fill(null);
    (pattern.inputs || []).slice(0, 27).forEach((entry, index) => {
      if (entry?.registryName) nextInputs[index] = patternEntryToInput(entry);
    });
    setInputs(nextInputs);
    outputKeySeq.current = 0;
    const nextOutputs = (pattern.outputs || []).map((entry) => {
      outputKeySeq.current += 1;
      const amount = Math.max(1, entry.stackSize || 1);
      return {
        key: String(outputKeySeq.current),
        registryName: entry.registryName,
        displayName: entry.displayName || entry.registryName,
        meta: entry.meta ?? 0,
        stackSize: amount,
        nbt: entry.nbt,
        originalStackSize: amount,
        isFluid: Boolean(entry.isFluid),
      };
    });
    setOutputs(nextOutputs.length ? nextOutputs : [blankOutput()]);
    setEncodedNbt(pattern.encodedNbt || '');
    setCurrentMultiplier(1);
    setDirty(false);
    setPickTarget(null);
  }, []);

  const loadDetail = useCallback(async (patternId: string) => {
    setLoadingDetail(true);
    try {
      const data = await getApiClient().get<PatternDetailResponse>(
        `/api/patterns/${encodeURIComponent(patternId)}`
      );
      if (data.success && data.pattern) {
        setCurrentPattern(data.pattern);
        applyPatternToEditor(data.pattern);
      } else notify(data.message || t('patternNoSelection'), 'error');
    } catch (error) {
      notify((error as Error).message, 'error');
    } finally {
      setLoadingDetail(false);
    }
  }, [applyPatternToEditor, notify, t]);

  const filteredPatterns = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return patterns;
    return patterns.filter((pattern) => {
      const output = (pattern.outputs || []).map((entry) => `${entry.displayName} ${entry.registryName}`).join(' ');
      return `${output} ${pattern.sourceInterfaceName} ${pattern.sourceInterface} ${pattern.author}`.toLowerCase().includes(q);
    });
  }, [patterns, search]);

  const buildPatternPayload = useCallback(() => ({
    patternId: currentPattern?.patternId || `web-${Date.now()}`,
    crafting,
    substitute,
    beSubstitute,
    author,
    programmableHatches,
    inputs: inputs.map((entry) => entry ? {
      registryName: entry.registryName,
      displayName: entry.displayName || entry.registryName,
      meta: entry.meta,
      stackSize: Math.max(1, entry.stackSize),
      nbt: entry.nbt,
      isFluid: entry.isFluid,
      nonConsumable: entry.nonConsumable,
      programmableCircuit: entry.programmableCircuit,
    } : null),
    outputs: outputs.filter((entry) => entry.registryName.trim()).map((entry) => ({
      registryName: entry.registryName,
      displayName: entry.displayName || entry.registryName,
      meta: entry.meta,
      stackSize: entry.stackSize,
      nbt: entry.nbt,
      isFluid: entry.isFluid,
    })),
  }), [author, beSubstitute, crafting, currentPattern, inputs, outputs, programmableHatches, substitute]);

  const requestEncode = useCallback(async (): Promise<string | null> => {
    if (!outputs.some((entry) => entry.registryName.trim())) {
      notify(t('patternEncodeFailed'), 'error');
      return null;
    }
    if (programmableHatches && !programmableHatchesInstalled) {
      notify(t('patternCompatMissing'), 'error');
      return null;
    }
    const data = await getApiClient().post<PatternEncodeResponse>('/api/pattern/encode', {
      ...buildPatternPayload(),
      networkId: currentNet,
      consumeBlank: false,
    });
    const nbt = data.data?.encodedNbt || data.encodedNbt || '';
    if (!data.success || !nbt) {
      notify(data.message || t('patternEncodeFailed'), 'error');
      return null;
    }
    setEncodedNbt(nbt);
    return nbt;
  }, [buildPatternPayload, currentNet, notify, outputs, programmableHatches, programmableHatchesInstalled, t]);

  const previewEncode = useCallback(async () => {
    setBusy(true);
    try {
      if (await requestEncode()) notify(t('patternEncoded'), 'success');
    } catch (error) {
      notify((error as Error).message, 'error');
    } finally {
      setBusy(false);
    }
  }, [notify, requestEncode, t]);

  const savePattern = useCallback(async () => {
    if (!currentPattern) return;
    setBusy(true);
    try {
      const nbt = await requestEncode();
      if (!nbt) return;
      const data = await getApiClient().put<PatternUpdateResponse>(
        `/api/patterns/${encodeURIComponent(currentPattern.patternId)}`,
        { encodedNbt: nbt }
      );
      if (!data.success) throw new Error(data.message || t('patternSaveFailed'));
      notify(t('patternSaveSuccess'), 'success');
      setDirty(false);
      await refreshAll();
    } catch (error) {
      notify((error as Error).message || t('patternSaveFailed'), 'error');
    } finally {
      setBusy(false);
    }
  }, [currentPattern, notify, refreshAll, requestEncode, t]);

  const selectedInterface = interfaces.find((iface) => interfaceAddress(iface) === selectedInterfaceId);

  const injectCurrent = useCallback(async () => {
    if (!selectedInterface) return;
    setBusy(true);
    try {
      const nbt = await requestEncode();
      if (!nbt) return;
      const data = await getApiClient().post<PatternInjectResponse>('/api/pattern/inject', {
        encodedNbt: nbt,
        interfaceX: selectedInterface.x,
        interfaceY: selectedInterface.y,
        interfaceZ: selectedInterface.z,
        interfaceDim: selectedInterface.dim,
        interfaceSide: selectedInterface.partSide || '',
        slotIndex: selectedSlot,
        networkId: currentNet,
        consumeBlank: true,
      });
      if (!data.success || !data.result?.success) {
        const message = data.result?.message || data.message || t('patternInjectFailed');
        throw new Error(message.includes('NO_BLANK_PATTERN') ? t('patternNoBlankPattern') : message);
      }
      notify(t('patternInjectSuccess'), 'success');
      await refreshAll();
    } catch (error) {
      notify((error as Error).message || t('patternInjectFailed'), 'error');
    } finally {
      setBusy(false);
    }
  }, [currentNet, notify, refreshAll, requestEncode, selectedInterface, selectedSlot, t]);

  const mutatePlacement = useCallback(async (path: string, payload: object, successKey: string) => {
    setBusy(true);
    try {
      const data = await getApiClient().post<PatternMutationResponse>(path, payload);
      if (!data.success) throw new Error(data.message);
      notify(t(successKey), 'success');
      setCurrentPattern(null);
      resetEditor();
      await refreshAll();
    } catch (error) {
      notify((error as Error).message, 'error');
    } finally {
      setBusy(false);
    }
  }, [notify, refreshAll, resetEditor, t]);

  const movePattern = useCallback((patternId: string, iface: InterfaceDto, slot: number, swap: boolean) => {
    mutatePlacement('/api/patterns/move', {
      patternId,
      networkId: currentNet,
      interfaceX: iface.x,
      interfaceY: iface.y,
      interfaceZ: iface.z,
      interfaceDim: iface.dim,
      interfaceSide: iface.partSide || '',
      slotIndex: slot,
      swap,
    }, 'patternMoveSuccess');
  }, [currentNet, mutatePlacement]);

  const takePattern = useCallback((patternId: string) => {
    mutatePlacement('/api/pattern-buffer/take', { patternId, networkId: currentNet }, 'patternBufferTakeSuccess');
  }, [currentNet, mutatePlacement]);

  const placeBuffer = useCallback((bufferId: string, iface: InterfaceDto, slot: number) => {
    mutatePlacement('/api/pattern-buffer/place', {
      bufferId,
      networkId: currentNet,
      interfaceX: iface.x,
      interfaceY: iface.y,
      interfaceZ: iface.z,
      interfaceDim: iface.dim,
      interfaceSide: iface.partSide || '',
      slotIndex: slot,
    }, 'patternBufferPlaceSuccess');
  }, [currentNet, mutatePlacement]);

  const useRecipe = useCallback((recipe: RecipeDto) => {
    const draft = recipeToPatternDraft(recipe);
    setCrafting(draft.crafting);
    setInputs(draft.inputs);
    setOutputs(draft.outputs.length ? draft.outputs : [blankOutput()]);
    outputKeySeq.current = Math.max(1, draft.outputs.length);
    const hasNonConsumable = draft.inputs.some((entry) => entry?.nonConsumable);
    if (hasNonConsumable && programmableHatchesInstalled) setProgrammableHatches(true);
    setCurrentMultiplier(1);
    markDirty();
  }, [markDirty, programmableHatchesInstalled]);

  const fillSlot = useCallback((slot: number, entry: RecipeSuggestEntry) => {
    setInputs((previous) => {
      const next = [...previous];
      next[slot] = {
        registryName: entry.registryName,
        displayName: entry.displayName || entry.registryName,
        meta: 0,
        stackSize: 1,
        nbt: undefined,
        isFluid: false,
        nonConsumable: false,
        programmableCircuit: false,
      };
      return next;
    });
    markDirty();
  }, [markDirty]);

  const renderPatternItem = (pattern: PatternListEntryDto) => {
    const primaryOutput = pattern.outputs?.[0];
    const iconId = patternEntryIconId(primaryOutput);
    return (
      <SelectableListRow
        key={pattern.patternId}
        variant="card"
        as="div"
        active={currentPattern?.patternId === pattern.patternId}
        onClick={() => loadDetail(pattern.patternId)}
        leading={
          <>
            <Checkbox
              checked={selectedIds.has(pattern.patternId)}
              onClick={(event) => event.stopPropagation()}
              onChange={() => setSelectedIds((previous) => {
                const next = new Set(previous);
                if (next.has(pattern.patternId)) next.delete(pattern.patternId);
                else next.add(pattern.patternId);
                return next;
              })}
            />
            {iconId && <Icon id={iconId} size={28} alt={primaryOutput?.displayName || ''} />}
          </>
        }
        trailing={pattern.programmableHatches ? <Tag color="purple">PH</Tag> : undefined}
      >
        <div className="webae-list-row-title">
          {primaryOutput?.displayName || primaryOutput?.registryName || `#${pattern.slotIndex + 1}`}
        </div>
        <div className="webae-text-2xs webae-list-row-subtitle">
          {pattern.sourceInterfaceName} · {t('patternSlot')} {pattern.slotIndex + 1}
        </div>
      </SelectableListRow>
    );
  };

  const deletePatterns = useCallback((ids: string[]) => {
    if (!ids.length) return;
    Modal.confirm({
      title: t('patternDeleteConfirm').replace('{n}', String(ids.length)),
      okType: 'danger',
      onOk: async () => {
        let deleted = 0;
        for (const id of ids) {
          try {
            const data = await getApiClient().delete<PatternDeleteResponse>(`/api/patterns/${encodeURIComponent(id)}`);
            if (data.success) deleted += 1;
          } catch {
            // Continue deleting the remaining explicit selection.
          }
        }
        notify(t('patternDeleteSuccess').replace('{n}', String(deleted)), 'success');
        setSelectedIds(new Set());
        setCurrentPattern(null);
        resetEditor();
        await refreshAll();
      },
    });
  }, [notify, refreshAll, resetEditor, t]);

  const deleteSelected = useCallback(() => deletePatterns(Array.from(selectedIds)), [deletePatterns, selectedIds]);

  const exportPatterns = useCallback((ids: string[]) => {
    const idSet = new Set(ids);
    const selected = patterns.filter((pattern) => idSet.has(pattern.patternId));
    if (!selected.length) return;
    const url = URL.createObjectURL(new Blob([JSON.stringify(selected, null, 2)], { type: 'application/json' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `patterns_${Date.now()}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  }, [patterns]);

  const exportSelected = useCallback(() => exportPatterns(Array.from(selectedIds)), [exportPatterns, selectedIds]);

  const leftTabs = [
    {
      key: 'recipes',
      label: t('patternRecipeLibrary'),
      children: <PatternRecipeSidebar t={t} onUseRecipe={useRecipe} />,
    },
    {
      key: 'patterns',
      label: `${t('patternListTitle')} (${patterns.length})`,
      children: (
        <PatternListSidebar
          search={search}
          onSearchChange={setSearch}
          selectedCount={selectedIds.size}
          onSelectAll={() => setSelectedIds(new Set(filteredPatterns.map((pattern) => pattern.patternId)))}
          onClearSelection={() => setSelectedIds(new Set())}
          onBatchDelete={deleteSelected}
          onBatchExport={exportSelected}
          loadingList={loadingList}
          patterns={filteredPatterns}
          renderItem={renderPatternItem}
        />
      ),
    },
  ];

  return (
    <PageShell
      title={t('patternEditor')}
      description={t('patternWorkbenchHint')}
      actions={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={refreshAll} loading={loadingList || busy}>
            {t('patternRefresh')}
          </Button>
          <Button icon={<PlusOutlined />} type="primary" onClick={() => {
            setCurrentPattern(null);
            resetEditor();
          }}>
            {t('patternNew')}
          </Button>
        </Space>
      }
    >
      <div className="webae-pattern-workbench">
        <Card size="small" className="webae-pattern-workbench-left">
          <Tabs items={leftTabs} defaultActiveKey="recipes" />
        </Card>

        <Card
          size="small"
          className="webae-pattern-workbench-editor"
          title={currentPattern ? t('patternDetailTitle') : t('patternNew')}
          extra={
            <Space>
              {dirty && <Tag color="warning">{t('patternUnsaved')}</Tag>}
              {currentPattern && (
                <>
                  <Button
                    size="small"
                    icon={<DownloadOutlined />}
                    onClick={() => exportPatterns([currentPattern.patternId])}
                  >
                    {t('patternBatchExport')}
                  </Button>
                  <Button
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => deletePatterns([currentPattern.patternId])}
                  >
                    {t('patternBatchDelete')}
                  </Button>
                </>
              )}
            </Space>
          }
        >
          <Spin spinning={loadingDetail}>
            <PatternEditorForm
              t={t}
              crafting={crafting}
              onCraftingChange={(value) => { setCrafting(value); markDirty(); }}
              substitute={substitute}
              onSubstituteChange={(value) => { setSubstitute(value); markDirty(); }}
              beSubstitute={beSubstitute}
              onBeSubstituteChange={(value) => { setBeSubstitute(value); markDirty(); }}
              author={author}
              onAuthorChange={(value) => { setAuthor(value); markDirty(); }}
              programmableHatches={programmableHatches}
              programmableHatchesInstalled={programmableHatchesInstalled}
              onProgrammableHatchesChange={(value) => { setProgrammableHatches(value); markDirty(); }}
              inputs={inputs}
              onClearInputs={() => { setInputs(new Array(27).fill(null)); markDirty(); }}
              onSlotClick={(slot) => {
                setPickTarget({ kind: 'slot', slot });
                setItemSearch(inputs[slot]?.registryName || '');
              }}
              onToggleNonConsumable={(slot) => {
                setInputs((previous) => previous.map((entry, index) =>
                  index === slot && entry ? { ...entry, nonConsumable: !entry.nonConsumable } : entry
                ));
                markDirty();
              }}
              outputs={outputs}
              onOutputChange={(index, output) => {
                setOutputs((previous) => previous.map((entry, i) => i === index ? output : entry));
                markDirty();
              }}
              onRemoveOutput={(key) => { setOutputs((previous) => previous.filter((entry) => entry.key !== key)); markDirty(); }}
              onAddOutput={() => {
                outputKeySeq.current += 1;
                setOutputs((previous) => [...previous, blankOutput(String(outputKeySeq.current))]);
                markDirty();
              }}
              currentMultiplier={currentMultiplier}
              onApplyMultiplier={(factor) => {
                setOutputs((previous) => previous.map((entry) => ({ ...entry, stackSize: entry.stackSize * factor })));
                setCurrentMultiplier((previous) => previous * factor);
                markDirty();
              }}
              onDivideMultiplier={() => {
                setOutputs((previous) => previous.map((entry) => ({
                  ...entry,
                  stackSize: Math.max(entry.originalStackSize, Math.floor(entry.stackSize / 2)),
                })));
                setCurrentMultiplier((previous) => Math.max(1, Math.floor(previous / 2)));
                markDirty();
              }}
              itemSearch={itemSearch}
              onItemSearchChange={setItemSearch}
              suggestOptions={suggestOptions}
              onSuggestSelect={(entry) => {
                setItemSearch(entry.registryName);
                if (pickTarget?.kind === 'slot') fillSlot(pickTarget.slot, entry);
              }}
              pickTarget={pickTarget}
              onSetSlot={(slot) => fillSlot(slot, { registryName: itemSearch.trim(), displayName: itemSearch.trim() })}
              encodedNbt={encodedNbt}
              onEncode={previewEncode}
              onSavePattern={savePattern}
              canSave={Boolean(currentPattern)}
              busy={busy}
            />
          </Spin>
        </Card>

        <Card size="small" className="webae-pattern-workbench-right">
          <PatternInterfaceWorkspace
            t={t}
            interfaces={interfaces}
            bufferEntries={bufferEntries}
            currentPattern={currentPattern}
            selectedInterfaceId={selectedInterfaceId}
            selectedSlot={selectedSlot}
            selectedBufferId={selectedBufferId}
            busy={busy}
            canInject={outputs.some((entry) => Boolean(entry.registryName.trim()))}
            onSelectedInterfaceChange={(id) => { setSelectedInterfaceId(id); setSelectedSlot(0); }}
            onSelectedSlotChange={setSelectedSlot}
            onSelectedBufferChange={setSelectedBufferId}
            onEditPattern={loadDetail}
            onMovePattern={movePattern}
            onTakePattern={takePattern}
            onPlaceBuffer={placeBuffer}
            onInjectCurrent={injectCurrent}
            onRefresh={refreshAll}
          />
        </Card>
      </div>
    </PageShell>
  );
}
