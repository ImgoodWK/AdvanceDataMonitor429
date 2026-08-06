import { useEffect, useState } from 'react';

import { Modal, Space, Tag, Typography, Divider, Spin, Button } from 'antd';

import { getApiClient } from '@/api/client';
import { Icon } from '@/components/Icon';
import { RecipeDetailModal } from '@/components/recipes/RecipeDetailModal';
import { patternEntryIconId } from '@/utils/icon';
import type { PatternItemEntry, PatternListEntryDto, RecipeDto, RecipeSearchResponse } from '@/types/dto';

const { Text } = Typography;

interface PatternDetailModalProps {
  open: boolean;
  pattern: PatternListEntryDto | null;
  allPatterns?: PatternListEntryDto[];
  networkId?: number;
  onClose: () => void;
  onSelectPattern?: (p: PatternListEntryDto) => void;
  t: (k: string) => string;
}

export function PatternDetailModal({
  open,
  pattern,
  allPatterns = [],
  networkId = 0,
  onClose,
  onSelectPattern,
  t,
}: PatternDetailModalProps) {
  const [linkedRecipes, setLinkedRecipes] = useState<RecipeDto[]>([]);
  const [loadingRecipes, setLoadingRecipes] = useState(false);
  const [recipeModalOpen, setRecipeModalOpen] = useState(false);
  const [recipeModalIndex, setRecipeModalIndex] = useState(0);
  const [detailPattern, setDetailPattern] = useState<PatternListEntryDto | null>(pattern);
  const [loadingDetail, setLoadingDetail] = useState(false);

  useEffect(() => {
    setDetailPattern(pattern);
  }, [pattern]);

  useEffect(() => {
    if (!open || !pattern || pattern.source !== 'grid' || !pattern.gridKey) {
      return;
    }
    const needsFetch =
      !pattern.inputs?.length ||
      !pattern.outputs?.length ||
      (pattern.inputsCount != null && pattern.inputsCount > (pattern.inputs?.length || 0));
    if (!needsFetch) {
      return;
    }
    let cancelled = false;
    setLoadingDetail(true);
    getApiClient()
      .get<{ success: boolean; entry?: PatternListEntryDto }>(
        `/api/patterns/grid/${encodeURIComponent(pattern.gridKey)}?network=${networkId}`
      )
      .then((data) => {
        if (!cancelled && data.success && data.entry) {
          setDetailPattern({ ...pattern, ...data.entry });
        }
      })
      .catch(() => {
        /* keep browse snapshot */
      })
      .finally(() => {
        if (!cancelled) setLoadingDetail(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, pattern, networkId]);

  useEffect(() => {
    if (!open || !detailPattern) {
      setLinkedRecipes([]);
      return;
    }
    const primary = detailPattern.outputs[0];
    if (!primary?.registryName) {
      setLinkedRecipes([]);
      return;
    }
    let cancelled = false;
    setLoadingRecipes(true);
    const q = primary.displayName || primary.registryName;
    getApiClient()
      .get<RecipeSearchResponse>(
        `/api/recipes/search?q=${encodeURIComponent(q)}&scope=output&limit=12`
      )
      .then((data) => {
        if (!cancelled && data.success) {
          setLinkedRecipes(data.results || []);
        }
      })
      .catch(() => {
        if (!cancelled) setLinkedRecipes([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingRecipes(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, detailPattern?.patternId]);

  if (!detailPattern) return null;

  const p = detailPattern;
  const primaryOutput = p.outputs[0];
  const title = primaryOutput?.displayName || primaryOutput?.registryName || t('patternDetailTitle');

  const siblingPatterns = allPatterns.filter(
    (item) => item.sourceInterface === p.sourceInterface && item.patternId !== p.patternId
  );

  return (
    <>
      <Modal open={open} onCancel={onClose} footer={null} width={680} title={title} destroyOnClose>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {loadingDetail && <Spin size="small" />}
          <Space wrap>
            {p.source === 'grid' ? (
              <Tag color="blue">{t('orderPatternSourceGrid')}</Tag>
            ) : (
              <Tag>{t('patternSourceInterface')}: {p.sourceInterfaceName}</Tag>
            )}
            {p.source !== 'grid' && (
              <>
                <Tag>{t('patternInterfaceCoords')}: {p.sourceInterface}</Tag>
                <Tag>{t('patternSlot')}: {p.slotIndex}</Tag>
              </>
            )}
            <Tag className={p.crafting ? 'pattern-tag-crafting' : 'pattern-tag-processing'}>
              {p.crafting ? t('crafting') : t('processing')}
            </Tag>
            {p.author && <Tag>{t('patternAuthor')}: {p.author}</Tag>}
          </Space>

          {siblingPatterns.length > 0 && (
            <div>
              <Text strong>{t('patternInterfaceOthers')}</Text>
              <div className="pattern-detail-siblings" style={{ marginTop: 8, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {siblingPatterns.map((sib) => {
                  const out = sib.outputs[0];
                  const iconId = patternEntryIconId(out);
                  return (
                    <button
                      key={sib.patternId}
                      type="button"
                      className="pattern-detail-sibling-btn"
                      onClick={() => onSelectPattern?.(sib)}
                      title={out?.displayName || sib.patternId}
                      style={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        gap: 4,
                        padding: 6,
                        border: '1px solid var(--border)',
                        borderRadius: 6,
                        background: 'var(--bg)',
                        cursor: onSelectPattern ? 'pointer' : 'default',
                        minWidth: 72,
                      }}
                    >
                      {iconId && <Icon id={iconId} size={32} alt={out?.displayName} />}
                      <Text style={{ fontSize: '0.65rem', maxWidth: 80 }} ellipsis>
                        {out?.displayName || `#${sib.slotIndex}`}
                      </Text>
                      <Text type="secondary" style={{ fontSize: '0.6rem' }}>
                        {t('patternSlot')} {sib.slotIndex}
                      </Text>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          <div>
            <Text strong>{t('patternInputs')}</Text>
            <div className="pattern-detail-io-grid" style={{ marginTop: 8 }}>
              {(p.inputs || []).map((entry, idx) => {
                if (!entry) {
                  return <div key={`in-${idx}`} className="pattern-detail-io-empty" aria-hidden />;
                }
                const iconId = patternEntryIconId(entry);
                return (
                  <div key={`in-${idx}`} className="pattern-detail-io-slot" title={entry.displayName}>
                    {iconId && <Icon id={iconId} size={28} alt={entry.displayName} />}
                    {entry.stackSize > 1 && (
                      <span className="pattern-detail-io-count">{entry.stackSize}</span>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          <div>
            <Text strong>{t('patternOutputs')}</Text>
            <Space wrap style={{ marginTop: 8 }}>
              {p.outputs.map((entry, idx) => {
                const iconId = patternEntryIconId(entry);
                return (
                  <div key={`out-${idx}`} className="pattern-detail-io-slot" title={entry.displayName}>
                    {iconId && <Icon id={iconId} size={32} alt={entry.displayName} />}
                    <span className="pattern-detail-io-count">{entry.stackSize}</span>
                    <Text style={{ fontSize: '0.75rem', maxWidth: 120 }} ellipsis>
                      {entry.displayName}
                    </Text>
                  </div>
                );
              })}
            </Space>
          </div>

          <Divider style={{ margin: '8px 0' }} />
          <div>
            <Space style={{ marginBottom: 8 }}>
              <Text strong>{t('patternLinkedRecipes')}</Text>
              {loadingRecipes && <Spin size="small" />}
            </Space>
            {!loadingRecipes && linkedRecipes.length === 0 && (
              <Text type="secondary" style={{ fontSize: '0.8rem' }}>
                {t('patternLinkedRecipesEmpty')}
              </Text>
            )}
            {linkedRecipes.length > 0 && (
              <Space wrap>
                {linkedRecipes.slice(0, 6).map((recipe, idx) => (
                  <Button
                    key={`${recipe.handlerId}_${recipe.recipeIndex}`}
                    size="small"
                    onClick={() => {
                      setRecipeModalIndex(idx);
                      setRecipeModalOpen(true);
                    }}
                  >
                    {recipe.handlerName} #{recipe.recipeIndex}
                  </Button>
                ))}
                {linkedRecipes.length > 6 && (
                  <Button
                    size="small"
                    type="link"
                    onClick={() => {
                      setRecipeModalIndex(0);
                      setRecipeModalOpen(true);
                    }}
                  >
                    +{linkedRecipes.length - 6}
                  </Button>
                )}
              </Space>
            )}
          </div>

          {p.encodedNbt && (
            <>
              <Divider style={{ margin: '8px 0' }} />
              <div>
                <Text strong>{t('patternEncodedRecipe')}</Text>
                <Text
                  type="secondary"
                  style={{
                    display: 'block',
                    marginTop: 4,
                    fontSize: '0.7rem',
                    fontFamily: 'monospace',
                    wordBreak: 'break-all',
                    maxHeight: 120,
                    overflow: 'auto',
                  }}
                >
                  {p.encodedNbt.length > 400
                    ? p.encodedNbt.substring(0, 400) + '…'
                    : p.encodedNbt}
                </Text>
              </div>
            </>
          )}
        </Space>
      </Modal>

      <RecipeDetailModal
        open={recipeModalOpen}
        recipes={linkedRecipes}
        initialIndex={recipeModalIndex}
        onClose={() => setRecipeModalOpen(false)}
        t={t}
      />
    </>
  );
}
