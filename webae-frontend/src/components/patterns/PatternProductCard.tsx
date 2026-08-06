import { memo, useState } from 'react';



import { Card, Tag, Tooltip, Typography, InputNumber, Button } from 'antd';

import { AppstoreOutlined, PlusOutlined } from '@ant-design/icons';



import { Icon } from '@/components/Icon';

import { useAppContext } from '@/context/AppContext';

import { patternEntryIconId } from '@/utils/icon';
import type { PatternProductGroup } from '@/utils/patternGroup';



const { Text } = Typography;




interface PatternProductCardProps {

  group: PatternProductGroup;

  t: (k: string) => string;

  onClick: () => void;

  onQuickAdd?: (group: PatternProductGroup, amount: number) => void;

  quickAddLoading?: boolean;

}



export const PatternProductCard = memo(function PatternProductCard({

  group,

  t,

  onClick,

  onQuickAdd,

  quickAddLoading,

}: PatternProductCardProps) {

  const { iconWikiEnabled } = useAppContext();

  const iconId = patternEntryIconId(group.primaryOutput);

  const variantCount = group.patterns.length;

  const interfaceCount = group.sourceInterfaces.length;

  const recipeType = group.allCrafting ? t('crafting') : t('processing');

  const recipeTagClass = group.allCrafting ? 'pattern-tag-crafting' : 'pattern-tag-processing';

  const [quickAmount, setQuickAmount] = useState(1);

  const [quickOpen, setQuickOpen] = useState(false);



  const handleQuickAdd = (e: React.MouseEvent) => {

    e.stopPropagation();

    if (variantCount > 1) {

      onClick();

      return;

    }

    if (quickOpen && onQuickAdd) {

      onQuickAdd(group, quickAmount);

      setQuickOpen(false);

      return;

    }

    setQuickOpen(true);

  };



  return (

    <Card

      size="small"

      hoverable

      onClick={onClick}

      className="recipe-thumbnail-card pattern-product-card"

      styles={{

        body: {

          padding: 8,

          display: 'flex',

          flexDirection: 'column',

          alignItems: 'center',

          justifyContent: 'space-between',

          height: '100%',

          position: 'relative',

        },

      }}

      role="button"

      tabIndex={0}

      aria-label={group.primaryOutput.displayName || group.primaryOutput.registryName}

      onKeyDown={(e) => {

        if (e.key === 'Enter' || e.key === ' ') {

          e.preventDefault();

          onClick();

        }

      }}

    >

      {onQuickAdd && (

        <div

          className="pattern-order-add-wrap"

          onClick={(e) => e.stopPropagation()}

          onKeyDown={(e) => e.stopPropagation()}

        >

          {quickOpen && variantCount === 1 ? (

            <SpaceCompactQuickAdd

              amount={quickAmount}

              onAmountChange={setQuickAmount}

              onConfirm={(e) => handleQuickAdd(e)}

              loading={quickAddLoading}

              t={t}

            />

          ) : (

            <Tooltip title={variantCount > 1 ? t('orderProductVariants').replace('{n}', String(variantCount)) : t('orderQuickAdd')}>

              <button

                type="button"

                className="pattern-order-add-btn"

                onClick={handleQuickAdd}

                aria-label={t('orderQuickAdd')}

                disabled={quickAddLoading}

              >

                <PlusOutlined />

              </button>

            </Tooltip>

          )}

        </div>

      )}

      <Tooltip title={t('orderProductVariants').replace('{n}', String(variantCount))}>

        <button

          type="button"

          className="pattern-order-info-btn"

          onClick={(e) => {

            e.stopPropagation();

            onClick();

          }}

          aria-label={t('orderProductVariants').replace('{n}', String(variantCount))}

        >

          <AppstoreOutlined />

        </button>

      </Tooltip>

      <div className="recipe-thumbnail-icon">

        {iconId ? (

          <Icon

            id={iconId}

            item={{

              registryName: group.primaryOutput.registryName,

              displayName: group.primaryOutput.displayName,

              meta: group.primaryOutput.meta,

            }}

            size={48}

            alt={group.primaryOutput.displayName || ''}

            linkToWiki={iconWikiEnabled}

            onIconClick={

              !iconWikiEnabled && onQuickAdd

                ? (e) => {

                    e.stopPropagation();

                    if (variantCount > 1) onClick();

                    else handleQuickAdd(e);

                  }

                : undefined

            }

          />

        ) : (

          <div className="recipe-thumbnail-icon-placeholder" aria-hidden />

        )}

      </div>

      <Text strong className="recipe-thumbnail-name" ellipsis={{ tooltip: true }}>

        {group.primaryOutput.displayName || group.primaryOutput.registryName || group.key}

      </Text>

      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', justifyContent: 'center', marginTop: 2 }}>

        <Tag className={`recipe-thumbnail-tag ${recipeTagClass}`} style={{ margin: 0 }}>{recipeType}</Tag>

        <Tag className="recipe-thumbnail-tag" style={{ margin: 0 }} color="blue">

          {t('orderProductVariantCount').replace('{n}', String(variantCount))}

        </Tag>

      </div>

      <Text type="secondary" style={{ fontSize: '0.65rem', maxWidth: '100%' }} ellipsis={{ tooltip: true }}>

        {t('orderProductInterfaceCount').replace('{n}', String(interfaceCount))}

      </Text>

    </Card>

  );

});



function SpaceCompactQuickAdd({

  amount,

  onAmountChange,

  onConfirm,

  loading,

  t,

}: {

  amount: number;

  onAmountChange: (v: number) => void;

  onConfirm: (e: React.MouseEvent) => void;

  loading?: boolean;

  t: (k: string) => string;

}) {

  return (

    <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>

      <InputNumber

        min={1}

        size="small"

        value={amount}

        onChange={(v) => onAmountChange(v || 1)}

        aria-label={t('qty')}

        style={{ width: 64 }}

      />

      <Button type="primary" size="small" icon={<PlusOutlined />} loading={loading} onClick={onConfirm} aria-label={t('orderQuickAdd')} />

    </div>

  );

}

