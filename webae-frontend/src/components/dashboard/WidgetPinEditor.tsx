import { useCallback, useMemo, useState } from 'react';
import { AutoComplete, Button, Checkbox, Select, Space, Tag, Typography } from 'antd';
import { getApiClient } from '@/api/client';
import { useI18n } from '@/i18n';
import { useAppContext } from '@/context/AppContext';
import type { StorageDto, GtMachineDto } from '@/types/dto';
import type { DashboardPin, DashboardPinKind, DashboardWidgetConfig } from '@/utils/presets';
import { DATA_TABLE_COLUMNS, resolveColumns, pinKey } from '@/utils/dashboardColumns';

const { Text } = Typography;

interface SuggestHit {
  itemId?: string;
  registryName?: string;
  displayName?: string;
  meta?: number;
  kindHint?: DashboardPinKind;
}

interface WidgetPinEditorProps {
  widget: DashboardWidgetConfig;
  onChange: (patch: Partial<DashboardWidgetConfig>) => void;
  storage?: StorageDto | null;
  gtMachines?: GtMachineDto[] | null;
  scalarDataSources: string[];
  /** Cross-network balance suggestions for pin search (optional). */
  balanceSuggestions?: Array<{
    itemId?: string;
    displayName: string;
    transferable: number;
    needyAmount: number;
    resourceType?: string;
  }> | null;
}

export function WidgetPinEditor({
  widget,
  onChange,
  storage,
  gtMachines,
  scalarDataSources,
  balanceSuggestions,
}: WidgetPinEditorProps) {
  const { t } = useI18n();
  const { serverConfig } = useAppContext();
  const maxPins = serverConfig?.dashboardMaxTracksPerWidget ?? 10;
  const [query, setQuery] = useState('');
  const [options, setOptions] = useState<{ value: string; label: string; pin: DashboardPin }[]>([]);
  const [searchKind, setSearchKind] = useState<DashboardPinKind | 'auto'>('auto');

  const pins = widget.pins || [];

  const runSearch = useCallback(
    async (q: string) => {
      setQuery(q);
      if (!q.trim()) {
        setOptions([]);
        return;
      }
      const needle = q.trim().toLowerCase();
      const hits: { value: string; label: string; pin: DashboardPin }[] = [];

      const kind = searchKind === 'auto' ? null : searchKind;

      if (!kind || kind === 'item') {
        try {
          const resp = await getApiClient().get<{
            success?: boolean;
            suggestions?: SuggestHit[];
            items?: SuggestHit[];
          }>(`/api/recipes/suggest?q=${encodeURIComponent(q.trim())}&limit=15`);
          const list = resp.suggestions || resp.items || [];
          for (const s of list) {
            const id = s.itemId || (s.registryName ? (s.meta ? `${s.registryName}:${s.meta}` : s.registryName) : '');
            if (!id) continue;
            hits.push({
              value: `item:${id}`,
              label: `${s.displayName || id} (${id})`,
              pin: { kind: 'item', id, label: s.displayName || id },
            });
          }
        } catch {
          /* ignore */
        }
        for (const item of storage?.items || []) {
          const name = (item.displayName || item.registryName || '').toLowerCase();
          const id = item.itemId || item.registryName;
          if (!id || !name.includes(needle)) continue;
          hits.push({
            value: `item:${id}`,
            label: `${item.displayName || id} · AE`,
            pin: { kind: 'item', id, label: item.displayName || id },
          });
        }
      }

      if (!kind || kind === 'fluid') {
        for (const f of storage?.fluids || []) {
          if (!f.fluidName?.toLowerCase().includes(needle)) continue;
          hits.push({
            value: `fluid:${f.fluidName}`,
            label: `💧 ${f.fluidName}`,
            pin: { kind: 'fluid', id: f.fluidName, label: f.fluidName },
          });
        }
        if (needle.length >= 2) {
          hits.push({
            value: `fluid:${q.trim()}`,
            label: `${t('dashPinFluidAs')} ${q.trim()}`,
            pin: { kind: 'fluid', id: q.trim(), label: q.trim() },
          });
        }
      }

      if (!kind || kind === 'essentia') {
        for (const e of storage?.essentia || []) {
          if (!e.aspect?.toLowerCase().includes(needle)) continue;
          hits.push({
            value: `essentia:${e.aspect}`,
            label: `✦ ${e.aspect}`,
            pin: { kind: 'essentia', id: e.aspect, label: e.aspect },
          });
        }
      }

      if (!kind || kind === 'cpu') {
        for (const cpu of storage?.cpus || []) {
          if (!cpu.name?.toLowerCase().includes(needle)) continue;
          hits.push({
            value: `cpu:${cpu.name}`,
            label: `CPU ${cpu.name}`,
            pin: { kind: 'cpu', id: `cpu:${cpu.name}`, label: cpu.name, metricField: 'craftingProgress' },
          });
        }
      }

      if (!kind || kind === 'gt') {
        for (const m of gtMachines || []) {
          const label = `${m.recipeMapName || m.statusText || 'GT'} @${m.x},${m.y},${m.z}`;
          if (!label.toLowerCase().includes(needle) && !String(m.x).includes(needle)) continue;
          const id = `gt:${m.dim}:${m.x}:${m.y}:${m.z}`;
          hits.push({
            value: id,
            label,
            pin: { kind: 'gt', id, label, metricField: 'progressPercent' },
          });
        }
      }

      if (!kind || kind === 'balance') {
        for (const s of balanceSuggestions || []) {
          const name = (s.displayName || s.itemId || '').toLowerCase();
          if (!name.includes(needle)) continue;
          const id = s.itemId || s.displayName;
          if (!id) continue;
          hits.push({
            value: `balance:${id}`,
            label: `${s.displayName} (${s.resourceType || 'res'})`,
            pin: { kind: 'balance', id, label: s.displayName },
          });
        }
      }

      if (!kind || kind === 'scalar' || kind === 'power') {
        for (const ds of scalarDataSources) {
          if (!ds.toLowerCase().includes(needle) && !t('dataSource_' + ds).toLowerCase().includes(needle)) {
            continue;
          }
          const powerLike = ds.startsWith('eu') || ds.startsWith('steam');
          hits.push({
            value: `scalar:${ds}`,
            label: t('dataSource_' + ds),
            pin: { kind: powerLike ? 'power' : 'scalar', id: ds, label: t('dataSource_' + ds) },
          });
        }
      }

      // Dedupe by value
      const seen = new Set<string>();
      const deduped = hits.filter((h) => {
        if (seen.has(h.value)) return false;
        seen.add(h.value);
        return true;
      });
      setOptions(deduped.slice(0, 20));
    },
    [searchKind, storage, gtMachines, scalarDataSources, balanceSuggestions, t]
  );

  const addPin = (pin: DashboardPin) => {
    if (pins.length >= maxPins) return;
    if (pins.some((p) => pinKey(p) === pinKey(pin))) return;
    onChange({ pins: [...pins, pin] });
    setQuery('');
    setOptions([]);
  };

  const removePin = (idx: number) => {
    onChange({ pins: pins.filter((_, i) => i !== idx) });
  };

  const columnDefs = DATA_TABLE_COLUMNS[widget.dataSource] || DATA_TABLE_COLUMNS.customPins;
  const selectedCols = resolveColumns(widget);

  const topCandidates = useMemo(() => {
    if (widget.dataSource !== 'topItems' || !storage?.items) return [];
    return [...storage.items].sort((a, b) => b.amount - a.amount).slice(0, 15);
  }, [widget.dataSource, storage]);

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <div>
        <Text strong>{t('dashPinSection')}</Text>
        <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem' }}>
          {t('dashPinSectionHint', maxPins)}
        </Text>
        <Space style={{ width: '100%', marginTop: 8 }} wrap>
          <Select
            style={{ width: 120 }}
            value={searchKind}
            onChange={(v) => setSearchKind(v)}
            options={[
              { value: 'auto', label: t('dashPinKind_auto') },
              { value: 'item', label: t('dashPinKind_item') },
              { value: 'fluid', label: t('dashPinKind_fluid') },
              { value: 'essentia', label: t('dashPinKind_essentia') },
              { value: 'cpu', label: t('dashPinKind_cpu') },
              { value: 'gt', label: t('dashPinKind_gt') },
              { value: 'balance', label: t('dashPinKind_balance') },
              { value: 'scalar', label: t('dashPinKind_scalar') },
            ]}
          />
          <AutoComplete
            style={{ flex: 1, minWidth: 180 }}
            value={query}
            options={options.map((o) => ({ value: o.value, label: o.label }))}
            onSearch={(v) => void runSearch(v)}
            onSelect={(v) => {
              const hit = options.find((o) => o.value === v);
              if (hit) addPin(hit.pin);
            }}
            placeholder={t('dashPinSearchPlaceholder')}
          />
        </Space>
        <div style={{ marginTop: 8 }}>
          {pins.map((p, i) => (
            <Tag key={pinKey(p) + i} closable onClose={() => removePin(i)} style={{ marginBottom: 4 }}>
              [{p.kind}] {p.label || p.id}
            </Tag>
          ))}
          {pins.length === 0 && (
            <Text type="secondary" style={{ fontSize: '0.75rem' }}>
              {t('dashPinEmpty')}
            </Text>
          )}
        </div>
      </div>

      {topCandidates.length > 0 && (
        <div>
          <Text strong>{t('dashPinFromTop')}</Text>
          <div style={{ marginTop: 6, maxHeight: 120, overflow: 'auto' }}>
            {topCandidates.map((item) => {
              const id = item.itemId || item.registryName;
              if (!id) return null;
              const pinned = pins.some((p) => p.kind === 'item' && p.id === id);
              return (
                <Button
                  key={id}
                  size="small"
                  type={pinned ? 'primary' : 'default'}
                  style={{ margin: 2 }}
                  disabled={!pinned && pins.length >= maxPins}
                  onClick={() => {
                    if (pinned) {
                      onChange({ pins: pins.filter((p) => !(p.kind === 'item' && p.id === id)) });
                    } else {
                      addPin({ kind: 'item', id, label: item.displayName || id });
                    }
                  }}
                >
                  {item.displayName || id}
                </Button>
              );
            })}
          </div>
        </div>
      )}

      {widget.type === 'dataTable' && columnDefs && (
        <div>
          <Text strong>{t('dashColumnsSection')}</Text>
          <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem' }}>
            {t('dashColumnsSectionHint')}
          </Text>
          <Checkbox.Group
            style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 4 }}
            value={selectedCols}
            onChange={(vals) => onChange({ columns: vals as string[] })}
            options={columnDefs.map((c) => ({
              label: t('dashCol_' + c.key),
              value: c.key,
            }))}
          />
          <Checkbox
            style={{ marginTop: 8 }}
            checked={!!widget.pinsOnly}
            onChange={(e) => onChange({ pinsOnly: e.target.checked })}
          >
            {t('dashPinsOnly')}
          </Checkbox>
        </div>
      )}
    </Space>
  );
}
