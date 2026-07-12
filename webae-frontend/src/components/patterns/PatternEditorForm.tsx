import {
  AutoComplete,
  Button,
  Divider,
  Input,
  InputNumber,
  Space,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  RollbackOutlined,
  SaveOutlined,
  SearchOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Icon } from '@/components/Icon';
import { SelectableListRow } from '@/components/common/SelectableListRow';
import { RecipeDetailModal } from '@/components/recipes/RecipeDetailModal';
import { resolveIconItemId, type RecipeMergedGroup } from '@/utils/recipe';
import type { RecipeDto, RecipeSuggestEntry } from '@/types/dto';
import {
  PATTERN_MULTIPLIERS,
  type PatternEditorInputSlot,
  type PatternEditorOutputRow,
  type PatternPickTarget,
} from './patternEditorTypes';

const { Text } = Typography;

export interface PatternEditorFormProps {
  t: (k: string) => string;
  crafting: boolean;
  onCraftingChange: (v: boolean) => void;
  substitute: boolean;
  onSubstituteChange: (v: boolean) => void;
  beSubstitute: boolean;
  onBeSubstituteChange: (v: boolean) => void;
  author: string;
  onAuthorChange: (v: string) => void;
  inputs: (PatternEditorInputSlot | null)[];
  onClearInputs: () => void;
  onSlotClick: (slot: number) => void;
  outputs: PatternEditorOutputRow[];
  onOutputChange: (index: number, row: PatternEditorOutputRow) => void;
  onRemoveOutput: (key: string) => void;
  onAddOutput: () => void;
  currentMultiplier: number;
  onApplyMultiplier: (factor: number) => void;
  onDivideMultiplier: () => void;
  itemSearch: string;
  onItemSearchChange: (v: string) => void;
  suggestOptions: Array<{ value: string; label: string; entry: RecipeSuggestEntry }>;
  onSuggestSelect: (entry: RecipeSuggestEntry) => void;
  pickTarget: PatternPickTarget;
  onPickTargetClear: () => void;
  onSearchRecipes: () => void;
  onSetSlot: (slot: number) => void;
  mergedRecipeGroups: RecipeMergedGroup[];
  onUseRecipe: (recipe: RecipeDto) => void;
  onOpenRecipeDetail: (recipes: RecipeDto[]) => void;
  recipeModalOpen: boolean;
  recipeModalRecipes: RecipeDto[];
  onRecipeModalClose: () => void;
  encodedNbt: string;
  onEncode: (consumeBlank: boolean) => void;
  onSavePattern: () => void;
  canSave: boolean;
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
  inputs,
  onClearInputs,
  onSlotClick,
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
  onPickTargetClear,
  onSearchRecipes,
  onSetSlot,
  mergedRecipeGroups,
  onUseRecipe,
  onOpenRecipeDetail,
  recipeModalOpen,
  recipeModalRecipes,
  onRecipeModalClose,
  encodedNbt,
  onEncode,
  onSavePattern,
  canSave,
}: PatternEditorFormProps) {
  return (
    <>
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
          placeholder={t('author')}
          value={author}
          onChange={(e) => onAuthorChange(e.target.value)}
          className="webae-pattern-author-input"
        />
      </Space>

      <Divider>{t('patternInputs')}</Divider>
      <div className="webae-pattern-slot-grid">
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
              className={
                'webae-pattern-slot' +
                (pickTarget?.kind === 'slot' && pickTarget.slot === idx ? ' webae-pattern-slot--active' : '')
              }
              onClick={() => onSlotClick(idx)}
            >
              {slot && (
                <>
                  <Icon
                    id={resolveIconItemId(slot)}
                    size={28}
                    alt={slot.displayName || slot.registryName}
                  />
                  {slot.stackSize > 1 && (
                    <span className="webae-pattern-slot-count">{slot.stackSize}</span>
                  )}
                </>
              )}
              <span className="webae-pattern-slot-index">{idx}</span>
            </div>
          </Tooltip>
        ))}
      </div>
      <Space className="webae-pattern-slot-actions" size={8}>
        <Button size="small" onClick={onClearInputs}>
          {t('clearInputs')}
        </Button>
        <Text type="secondary" className="webae-text-xs">
          {t('patternInputsCount').replace('{n}', String(inputs.filter((i) => i).length))}
        </Text>
      </Space>

      <Divider>{t('patternOutputs')}</Divider>
      <Space wrap className="webae-pattern-multiplier-bar" align="center">
        <Text type="secondary" className="webae-text-xs">
          {t('patternMultiplier')}:
        </Text>
        {currentMultiplier > 1 && <Tag color="blue">×{currentMultiplier}</Tag>}
        {PATTERN_MULTIPLIERS.map((m) => (
          <Button key={m} size="small" onClick={() => onApplyMultiplier(m)} aria-label={`×${m}`}>
            ×{m}
          </Button>
        ))}
        <Tooltip title={t('patternMultiplierDivide')}>
          <Button size="small" onClick={onDivideMultiplier} aria-label={t('patternMultiplierDivide')}>
            {t('patternMultiplierDivide')}
          </Button>
        </Tooltip>
      </Space>
      {outputs.map((out, idx) => (
        <Space key={out.key} className="webae-pattern-output-row" align="center">
          {out.registryName ? (
            <Icon id={resolveIconItemId(out)} size={32} alt={out.displayName || out.registryName} />
          ) : (
            <div className="webae-icon-placeholder-32" aria-hidden />
          )}
          <Input
            placeholder={t('itemSearchPlaceholder')}
            value={out.registryName}
            onChange={(e) => {
              onOutputChange(idx, {
                ...out,
                registryName: e.target.value,
                displayName: e.target.value,
                originalStackSize: out.originalStackSize || 1,
              });
            }}
            className="webae-pattern-output-input"
          />
          <InputNumber
            min={out.originalStackSize}
            value={out.stackSize}
            onChange={(v) => {
              const qty = v || out.originalStackSize;
              onOutputChange(idx, {
                ...out,
                stackSize: Math.max(out.originalStackSize, qty),
              });
            }}
          />
          <Button
            icon={<DeleteOutlined />}
            danger
            onClick={() => onRemoveOutput(out.key)}
            disabled={outputs.length === 1}
            aria-label={t('patternBatchDelete')}
          />
        </Space>
      ))}
      <Button icon={<PlusOutlined />} size="small" className="webae-pattern-add-output" onClick={onAddOutput}>
        {t('addOutput')}
      </Button>
      <Text type="secondary" className="webae-text-xs webae-pattern-output-count">
        {t('patternOutputsCount').replace('{n}', String(outputs.filter((o) => o.registryName).length))}
      </Text>

      <Divider>{t('itemSearchPlaceholder')}</Divider>
      <Space className="webae-full-width" style={{ marginBottom: 8 }}>
        <AutoComplete
          className="webae-full-width"
          options={suggestOptions}
          value={itemSearch}
          onChange={onItemSearchChange}
          onSelect={(_, opt) => {
            if (opt && 'entry' in opt && opt.entry) {
              onSuggestSelect(opt.entry as RecipeSuggestEntry);
            }
          }}
        >
          <Input
            placeholder={t('itemSearchPlaceholder')}
            prefix={<SearchOutlined />}
            onPressEnter={() => onSearchRecipes()}
          />
        </AutoComplete>
      </Space>
      <Space className="webae-pattern-search-actions" size={8}>
        <Button size="small" onClick={() => onSearchRecipes()}>
          {t('findRecipes')}
        </Button>
        {pickTarget?.kind === 'slot' && (
          <Button
            size="small"
            type="primary"
            onClick={() => onSetSlot(pickTarget.slot)}
            disabled={!itemSearch.trim()}
          >
            {t('setSlot')} {pickTarget.slot}
          </Button>
        )}
        {pickTarget && (
          <Button size="small" onClick={onPickTargetClear}>
            <RollbackOutlined /> {t('patternBackToList')}
          </Button>
        )}
      </Space>
      {mergedRecipeGroups.length > 0 && (
        <div className="webae-scroll-panel webae-scroll-panel--md">
          {mergedRecipeGroups.map((group) => {
            const main = group.primaryOutput;
            return (
              <SelectableListRow
                key={group.primaryOutputKey}
                as="div"
                onClick={() => onUseRecipe(group.recipes[0])}
                leading={<Icon item={main} size={36} alt={main.displayName || main.registryName} />}
                trailing={
                  <Space size={4} onClick={(e) => e.stopPropagation()}>
                    <Button size="small" type="primary" onClick={() => onUseRecipe(group.recipes[0])}>
                      {t('patternRecipeAdd')}
                    </Button>
                    <Tooltip title={t('patternRecipeDetail')}>
                      <Button
                        size="small"
                        icon={<InfoCircleOutlined />}
                        aria-label={t('patternRecipeDetail')}
                        onClick={() => onOpenRecipeDetail(group.recipes)}
                      />
                    </Tooltip>
                  </Space>
                }
              >
                <div className="webae-list-row-title">{main.displayName || main.registryName}</div>
                <Text type="secondary" className="webae-text-2xs">
                  {group.recipes.length} {t('recipeTypes')}
                </Text>
              </SelectableListRow>
            );
          })}
        </div>
      )}

      <RecipeDetailModal
        open={recipeModalOpen}
        recipes={recipeModalRecipes}
        onClose={onRecipeModalClose}
        onApplyRecipe={onUseRecipe}
        applyLabel={t('useRecipe')}
        t={t}
      />

      <Divider />
      <Space wrap>
        <Button type="primary" icon={<ThunderboltOutlined />} onClick={() => onEncode(true)}>
          {t('patternEncodeEditor')}
        </Button>
        <Button icon={<SaveOutlined />} onClick={onSavePattern} disabled={!canSave}>
          {t('patternSaveSuccess').replace('已保存', '保存')}
        </Button>
        {encodedNbt && (
          <Tag color="success" className="webae-nbt-preview">
            NBT: {encodedNbt.substring(0, 40)}...
          </Tag>
        )}
      </Space>
    </>
  );
}
