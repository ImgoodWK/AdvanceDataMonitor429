import { AutoComplete, Button, Divider, Input, InputNumber, Space, Switch, Tag, Tooltip, Typography } from 'antd';
import {
  DeleteOutlined,
  PlusOutlined,
  SaveOutlined,
  SearchOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Icon } from '@/components/Icon';
import { resolveIconItemId } from '@/utils/recipe';
import type { RecipeSuggestEntry } from '@/types/dto';
import {
  PATTERN_MULTIPLIERS,
  type PatternEditorInputSlot,
  type PatternEditorOutputRow,
  type PatternPickTarget,
} from './patternEditorTypes';

const { Text } = Typography;

export interface PatternEditorFormProps {
  t: (key: string) => string;
  crafting: boolean;
  onCraftingChange: (value: boolean) => void;
  substitute: boolean;
  onSubstituteChange: (value: boolean) => void;
  beSubstitute: boolean;
  onBeSubstituteChange: (value: boolean) => void;
  author: string;
  onAuthorChange: (value: string) => void;
  programmableHatches: boolean;
  programmableHatchesInstalled: boolean;
  onProgrammableHatchesChange: (value: boolean) => void;
  inputs: (PatternEditorInputSlot | null)[];
  onClearInputs: () => void;
  onSlotClick: (slot: number) => void;
  onToggleNonConsumable: (slot: number) => void;
  outputs: PatternEditorOutputRow[];
  onOutputChange: (index: number, row: PatternEditorOutputRow) => void;
  onRemoveOutput: (key: string) => void;
  onAddOutput: () => void;
  currentMultiplier: number;
  onApplyMultiplier: (factor: number) => void;
  onDivideMultiplier: () => void;
  itemSearch: string;
  onItemSearchChange: (value: string) => void;
  suggestOptions: Array<{ value: string; label: string; entry: RecipeSuggestEntry }>;
  onSuggestSelect: (entry: RecipeSuggestEntry) => void;
  pickTarget: PatternPickTarget;
  onSetSlot: (slot: number) => void;
  encodedNbt: string;
  onEncode: () => void;
  onSavePattern: () => void;
  canSave: boolean;
  busy: boolean;
}

export function PatternEditorForm({
  t,
  crafting,
  onCraftingChange,
  substitute,
  onSubstituteChange,
  beSubstitute,
  onBeSubstituteChange,
  author,
  onAuthorChange,
  programmableHatches,
  programmableHatchesInstalled,
  onProgrammableHatchesChange,
  inputs,
  onClearInputs,
  onSlotClick,
  onToggleNonConsumable,
  outputs,
  onOutputChange,
  onRemoveOutput,
  onAddOutput,
  currentMultiplier,
  onApplyMultiplier,
  onDivideMultiplier,
  itemSearch,
  onItemSearchChange,
  suggestOptions,
  onSuggestSelect,
  pickTarget,
  onSetSlot,
  encodedNbt,
  onEncode,
  onSavePattern,
  canSave,
  busy,
}: PatternEditorFormProps) {
  const selectedInput = pickTarget?.kind === 'slot' ? inputs[pickTarget.slot] : null;
  return (
    <section className="webae-pattern-editor-form" aria-label={t('patternEditorCanvas')}>
      <Space className="webae-pattern-toggles" wrap>
        <span>{t('patternType')}:</span>
        <Switch
          checkedChildren={t('crafting')}
          unCheckedChildren={t('processing')}
          checked={crafting}
          onChange={onCraftingChange}
        />
        <Switch checkedChildren={t('substitute')} checked={substitute} onChange={onSubstituteChange} />
        <Switch checkedChildren={t('beSubstitute')} checked={beSubstitute} onChange={onBeSubstituteChange} />
        <Input
          aria-label={t('author')}
          placeholder={t('author')}
          value={author}
          onChange={(event) => onAuthorChange(event.target.value)}
          className="webae-pattern-author-input"
        />
      </Space>

      <div className="webae-pattern-programmable-row">
        <div>
          <Text strong>{t('patternProgrammableHatches')}</Text>
          <Text type="secondary" className="webae-text-xs">{t('patternProgrammableHatchesHint')}</Text>
        </div>
        <Space>
          <Tag color={programmableHatchesInstalled ? 'success' : 'default'}>
            {programmableHatchesInstalled ? t('patternCompatInstalled') : t('patternCompatMissing')}
          </Tag>
          <Switch
            checked={programmableHatches}
            disabled={!programmableHatchesInstalled}
            onChange={onProgrammableHatchesChange}
            aria-label={t('patternProgrammableHatches')}
          />
        </Space>
      </div>

      <Divider>{crafting ? t('patternCraftingInputs') : t('patternProcessingInputs')}</Divider>
      <div className={`webae-pattern-slot-grid${crafting ? ' webae-pattern-slot-grid--crafting' : ''}`}>
        {(crafting ? inputs.slice(0, 9) : inputs).map((slot, index) => (
          <Tooltip
            key={index}
            title={
              slot
                ? `${slot.displayName || slot.registryName} ×${slot.stackSize}${slot.nonConsumable ? ` · ${t('patternNonConsumable')}` : ''}`
                : `${t('selectedSlot')} ${index + 1}`
            }
          >
            <div
              className={`webae-pattern-slot${pickTarget?.kind === 'slot' && pickTarget.slot === index ? ' webae-pattern-slot--active' : ''}${slot?.nonConsumable ? ' webae-pattern-slot--non-consumable' : ''}`}
              role="button"
              tabIndex={0}
              onClick={() => onSlotClick(index)}
              onDoubleClick={() => slot && onToggleNonConsumable(index)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') onSlotClick(index);
              }}
            >
              {slot && (
                <>
                  <Icon id={resolveIconItemId(slot)} size={28} alt={slot.displayName || slot.registryName} />
                  {slot.stackSize > 1 && <span className="webae-pattern-slot-count">{slot.stackSize}</span>}
                  {slot.nonConsumable && <span className="webae-pattern-non-consumable-badge">∞</span>}
                </>
              )}
              <span className="webae-pattern-slot-index">{index + 1}</span>
            </div>
          </Tooltip>
        ))}
      </div>
      <Space className="webae-pattern-slot-actions" size={8} wrap>
        <Button size="small" onClick={onClearInputs}>{t('clearInputs')}</Button>
        <Button
          size="small"
          disabled={!selectedInput || !programmableHatchesInstalled}
          onClick={() => pickTarget?.kind === 'slot' && onToggleNonConsumable(pickTarget.slot)}
        >
          {selectedInput?.nonConsumable ? t('patternMarkConsumable') : t('patternMarkNonConsumable')}
        </Button>
        <Text type="secondary" className="webae-text-xs">
          {t('patternInputsCount').replace('{n}', String(inputs.filter(Boolean).length))}
        </Text>
      </Space>

      <div className="webae-pattern-item-picker">
        <AutoComplete
          className="webae-full-width"
          options={suggestOptions}
          value={itemSearch}
          onChange={onItemSearchChange}
          onSelect={(_, option) => {
            if ('entry' in option && option.entry) onSuggestSelect(option.entry as RecipeSuggestEntry);
          }}
        >
          <Input placeholder={t('itemSearchPlaceholder')} prefix={<SearchOutlined />} />
        </AutoComplete>
        <Button
          type="primary"
          disabled={pickTarget?.kind !== 'slot' || !itemSearch.trim()}
          onClick={() => pickTarget?.kind === 'slot' && onSetSlot(pickTarget.slot)}
        >
          {t('setSlot')}
        </Button>
      </div>

      <Divider>{t('patternOutputs')}</Divider>
      <Space wrap className="webae-pattern-multiplier-bar" align="center">
        <Text type="secondary" className="webae-text-xs">{t('patternMultiplier')}:</Text>
        {currentMultiplier > 1 && <Tag color="blue">×{currentMultiplier}</Tag>}
        {PATTERN_MULTIPLIERS.map((multiplier) => (
          <Button key={multiplier} size="small" onClick={() => onApplyMultiplier(multiplier)}>
            ×{multiplier}
          </Button>
        ))}
        <Button size="small" onClick={onDivideMultiplier}>{t('patternMultiplierDivide')}</Button>
      </Space>
      {outputs.map((output, index) => (
        <Space key={output.key} className="webae-pattern-output-row" align="center">
          {output.registryName ? (
            <Icon id={resolveIconItemId(output)} size={32} alt={output.displayName || output.registryName} />
          ) : (
            <div className="webae-icon-placeholder-32" aria-hidden />
          )}
          <Input
            aria-label={`${t('patternOutputs')} ${index + 1}`}
            placeholder={t('itemSearchPlaceholder')}
            value={output.registryName}
            onChange={(event) => onOutputChange(index, {
              ...output,
              registryName: event.target.value,
              displayName: event.target.value,
            })}
            className="webae-pattern-output-input"
          />
          <InputNumber
            min={output.originalStackSize}
            value={output.stackSize}
            onChange={(value) => onOutputChange(index, {
              ...output,
              stackSize: Math.max(output.originalStackSize, value || output.originalStackSize),
            })}
          />
          <Button
            icon={<DeleteOutlined />}
            danger
            onClick={() => onRemoveOutput(output.key)}
            disabled={outputs.length === 1}
            aria-label={t('patternBatchDelete')}
          />
        </Space>
      ))}
      <Button icon={<PlusOutlined />} size="small" className="webae-pattern-add-output" onClick={onAddOutput}>
        {t('addOutput')}
      </Button>

      <Divider />
      <Space wrap>
        <Button icon={<ThunderboltOutlined />} onClick={onEncode} loading={busy}>
          {t('patternEncodeEditor')}
        </Button>
        <Button type="primary" icon={<SaveOutlined />} onClick={onSavePattern} disabled={!canSave} loading={busy}>
          {t('patternSaveAction')}
        </Button>
        {encodedNbt && <Tag color="success">{t('patternPreviewReady')}</Tag>}
      </Space>
    </section>
  );
}
