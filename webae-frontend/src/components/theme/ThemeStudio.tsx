import { startTransition, useMemo, useState } from 'react';
import { Button, Empty, Input, Segmented, Space, Tabs, Tag, Tooltip, Typography } from 'antd';
import {
  CheckOutlined,
  DashboardOutlined,
  ExperimentOutlined,
  RocketOutlined,
  StarFilled,
  StarOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';

import { ThemeOptionGrid, type ThemeOptionItem } from './ThemeOptionGrid';
import { ThemePreviewMini } from './ThemePreviewMini';
import { THEME_COLORS, type EffectsLevel, type ThemeColor } from '@/theme/colors';
import {
  DESIGN_PACK_CATEGORIES,
  DESIGN_PACKS,
  filterDesignPacks,
  isDesignPackActive,
  type DesignPack,
  type DesignPackFilterCategory,
} from '@/theme/designPacks';
import { THEME_LAYOUTS, type ThemeLayout } from '@/theme/layouts';
import { PAGE_STYLES, type PageStyle } from '@/theme/pageStyles';

const { Text, Title } = Typography;

export const THEME_PACK_FAVORITES_KEY = 'webae_theme_pack_favorites';

type Translate = (key: string) => string;

interface ThemeStudioProps {
  themeColor: ThemeColor;
  setThemeColor: (value: ThemeColor) => void;
  themeLayout: ThemeLayout;
  setThemeLayout: (value: ThemeLayout) => void;
  pageStyle: PageStyle;
  setPageStyle: (value: PageStyle) => void;
  effectsLevel: EffectsLevel;
  setEffectsLevel: (value: EffectsLevel) => void;
  t: Translate;
  notify?: (text: string, type?: 'success' | 'error' | 'warning' | 'info') => void;
}

const BATCH6_COLORS = new Set<ThemeColor>([
  'terra-amber',
  'terra-danger',
  'cyber-lime',
  'cyber-redline',
  'ueg-orange',
  'lunar-ice',
  'gtnh-stargate',
  'gregtech-steel',
  'gregtech-bronze',
  'gt-cleanroom',
  'gt-fusion',
  'textech-quantum',
  'bridges-white',
]);

const BATCH6_LAYOUTS = new Set<ThemeLayout>([
  'tactical-grid',
  'mission-control',
  'engine-room',
  'orbital-console',
  'assembly-line',
  'quantum-frame',
]);

const BATCH6_STYLES = new Set<PageStyle>([
  'terra-command',
  'terra-contract',
  'terra-originium',
  'cyber-grid',
  'cyber-chrome',
  'earth-engine',
  'lunar-orbit',
  'gtnh-cosmos',
  'gt-assembly',
  'gt-cleanroom',
  'gt-fusion',
  'textech-quantum',
]);

function readFavorites(): Set<string> {
  if (typeof window === 'undefined') return new Set();
  try {
    const value = JSON.parse(window.localStorage.getItem(THEME_PACK_FAVORITES_KEY) || '[]');
    if (!Array.isArray(value)) return new Set();
    return new Set(value.filter((item): item is string => typeof item === 'string'));
  } catch {
    return new Set();
  }
}

function persistFavorites(favorites: Set<string>) {
  try {
    window.localStorage.setItem(THEME_PACK_FAVORITES_KEY, JSON.stringify(Array.from(favorites)));
  } catch {
    // Appearance remains usable when storage is unavailable/private.
  }
}

function axisFooter(isNew: boolean, t: Translate) {
  return isNew ? <Tag color="gold">{t('themeStudioNew')}</Tag> : undefined;
}

function effectIcon(level: EffectsLevel) {
  if (level === 'none') return <DashboardOutlined />;
  if (level === 'subtle') return <ExperimentOutlined />;
  return <RocketOutlined />;
}

export function ThemeStudio({
  themeColor,
  setThemeColor,
  themeLayout,
  setThemeLayout,
  pageStyle,
  setPageStyle,
  effectsLevel,
  setEffectsLevel,
  t,
  notify,
}: ThemeStudioProps) {
  const [packQuery, setPackQuery] = useState('');
  const [packCategory, setPackCategory] = useState<DesignPackFilterCategory>('featured');
  const [favorites, setFavorites] = useState<Set<string>>(readFavorites);

  const current = useMemo(
    () => ({ themeColor, themeLayout, pageStyle, effectsLevel }),
    [themeColor, themeLayout, pageStyle, effectsLevel]
  );
  const activePack = useMemo(
    () => DESIGN_PACKS.find((pack) => isDesignPackActive(pack, current)),
    [current]
  );
  const visiblePacks = useMemo(
    () => filterDesignPacks(DESIGN_PACKS, packQuery, packCategory, favorites),
    [packQuery, packCategory, favorites]
  );

  const colorItems = useMemo<ThemeOptionItem<ThemeColor>[]>(
    () =>
      THEME_COLORS.map((color) => ({
        id: color,
        label: t(`themeColor_${color}`),
        themeColor: color,
        themeLayout,
        pageStyle,
        effectsLevel,
        emphasize: 'color',
        footer: axisFooter(BATCH6_COLORS.has(color), t),
        searchText: BATCH6_COLORS.has(color) ? 'batch6 flagship terra cyber space gtnh gregtech' : '',
      })),
    [t, themeLayout, pageStyle, effectsLevel]
  );
  const layoutItems = useMemo<ThemeOptionItem<ThemeLayout>[]>(
    () =>
      THEME_LAYOUTS.map((layout) => ({
        id: layout,
        label: t(`themeLayout_${layout}`),
        themeColor,
        themeLayout: layout,
        pageStyle,
        effectsLevel,
        emphasize: 'layout',
        footer: axisFooter(BATCH6_LAYOUTS.has(layout), t),
        searchText: BATCH6_LAYOUTS.has(layout) ? 'batch6 flagship tactical mission engine orbit assembly quantum' : '',
      })),
    [t, themeColor, pageStyle, effectsLevel]
  );
  const styleItems = useMemo<ThemeOptionItem<PageStyle>[]>(
    () =>
      PAGE_STYLES.map((style) => ({
        id: style,
        label: t(`pageStyle_${style}`),
        themeColor,
        themeLayout,
        pageStyle: style,
        effectsLevel,
        emphasize: 'style',
        footer: axisFooter(BATCH6_STYLES.has(style), t),
        searchText: BATCH6_STYLES.has(style) ? 'batch6 flagship terra cyber space gtnh gregtech textech' : '',
      })),
    [t, themeColor, themeLayout, effectsLevel]
  );

  const applyPack = (pack: DesignPack) => {
    startTransition(() => {
      setThemeColor(pack.themeColor);
      setThemeLayout(pack.themeLayout);
      setPageStyle(pack.pageStyle);
      setEffectsLevel(pack.effectsLevel);
    });
    notify?.(t('themeStudioApplied').replace('{name}', t(pack.nameKey)), 'success');
  };

  const toggleFavorite = (pack: DesignPack) => {
    setFavorites((previous) => {
      const next = new Set(previous);
      if (next.has(pack.id)) next.delete(pack.id);
      else next.add(pack.id);
      persistFavorites(next);
      return next;
    });
  };

  const categoryOptions = [
    { label: t('themeStudioCategory_all'), value: 'all' },
    { label: t('themeStudioCategory_featured'), value: 'featured', icon: <ThunderboltOutlined /> },
    ...DESIGN_PACK_CATEGORIES.map((category) => ({
      label: t(`themeStudioCategory_${category}`),
      value: category,
    })),
    { label: t('themeStudioCategory_favorites'), value: 'favorites', icon: <StarFilled /> },
  ];

  const effectLevels: EffectsLevel[] = ['none', 'subtle', 'full'];

  return (
    <section className="theme-studio">
      <div className="theme-studio__hero">
        <div className="theme-studio__hero-mark" aria-hidden>
          <span>TT</span>
          <i />
        </div>
        <div className="theme-studio__hero-copy">
          <Text className="theme-studio__eyebrow">{t('themeStudioEyebrow')}</Text>
          <Title level={3}>{t('themeStudioTitle')}</Title>
          <Text>{t('themeStudioDescription')}</Text>
          <div className="theme-studio__active-spec" aria-live="polite">
            <span>{activePack ? t(activePack.nameKey) : t('themeStudioCustomMix')}</span>
            <code>{themeColor}</code>
            <code>{themeLayout}</code>
            <code>{pageStyle}</code>
          </div>
        </div>
        <div className="theme-studio__hero-preview">
          <ThemePreviewMini
            themeColor={themeColor}
            themeLayout={themeLayout}
            pageStyle={pageStyle}
            effectsLevel={effectsLevel}
            title={activePack ? t(activePack.nameKey) : t('themeStudioCustomMix')}
          />
        </div>
      </div>

      <Tabs
        className="theme-studio__tabs"
        defaultActiveKey="packs"
        items={[
          {
            key: 'packs',
            label: t('themeStudioTabPacks'),
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <div className="theme-studio__filters">
                  <Input
                    allowClear
                    value={packQuery}
                    onChange={(event) => setPackQuery(event.target.value)}
                    placeholder={t('themeStudioSearch')}
                    aria-label={t('themeStudioSearch')}
                  />
                  <Segmented
                    options={categoryOptions}
                    value={packCategory}
                    onChange={(value) => setPackCategory(value as DesignPackFilterCategory)}
                  />
                </div>
                {visiblePacks.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('themeStudioEmpty')} />
                ) : (
                  <div className="theme-studio__pack-grid">
                    {visiblePacks.map((pack) => {
                      const active = isDesignPackActive(pack, current);
                      const favorite = favorites.has(pack.id);
                      return (
                        <article
                          className={`theme-studio-pack${pack.featured ? ' theme-studio-pack--featured' : ''}${active ? ' theme-studio-pack--active' : ''}`}
                          key={pack.id}
                          data-pack-motif={pack.motif}
                        >
                          <button
                            type="button"
                            className="theme-studio-pack__visual"
                            onClick={() => applyPack(pack)}
                            aria-label={`${t('themeStudioApply')}: ${t(pack.nameKey)}`}
                          >
                            <ThemePreviewMini
                              themeColor={pack.themeColor}
                              themeLayout={pack.themeLayout}
                              pageStyle={pack.pageStyle}
                              effectsLevel={pack.effectsLevel}
                              title={t(pack.nameKey)}
                            />
                            <span className="theme-studio-pack__mark" aria-hidden>
                              {pack.mark}
                            </span>
                            <span className="theme-studio-pack__serial" aria-hidden>
                              {pack.serial}
                            </span>
                            {pack.featured && (
                              <span className="theme-studio-pack__featured">
                                {pack.featuredRank
                                  ? t('themeStudioFeaturedRank').replace(
                                      '{rank}',
                                      String(pack.featuredRank).padStart(2, '0')
                                    )
                                  : t('themeStudioFeatured')}
                              </span>
                            )}
                          </button>
                          <div className="theme-studio-pack__body">
                            <div>
                              <Text strong>{t(pack.nameKey)}</Text>
                              <Text type="secondary">{t(pack.descriptionKey)}</Text>
                              {pack.referenceKey && (
                                <Text className="theme-studio-pack__reference">
                                  {t(pack.referenceKey)}
                                </Text>
                              )}
                            </div>
                            <Space size={4}>
                              <Tag>{t(`themeStudioCategory_${pack.category}`)}</Tag>
                              <Tag>{t(`effectsLevel_${pack.effectsLevel}`)}</Tag>
                            </Space>
                          </div>
                          <div className="theme-studio-pack__actions">
                            <Button
                              type={active ? 'primary' : 'default'}
                              icon={active ? <CheckOutlined /> : <ThunderboltOutlined />}
                              onClick={() => applyPack(pack)}
                            >
                              {active ? t('themeStudioActive') : t('themeStudioApply')}
                            </Button>
                            <Tooltip title={favorite ? t('themeStudioUnfavorite') : t('themeStudioFavorite')}>
                              <Button
                                icon={favorite ? <StarFilled /> : <StarOutlined />}
                                onClick={() => toggleFavorite(pack)}
                                aria-label={favorite ? t('themeStudioUnfavorite') : t('themeStudioFavorite')}
                                aria-pressed={favorite}
                              />
                            </Tooltip>
                          </div>
                        </article>
                      );
                    })}
                  </div>
                )}
              </Space>
            ),
          },
          {
            key: 'colors',
            label: `${t('themeColor')} · ${THEME_COLORS.length}`,
            children: (
              <ThemeOptionGrid
                items={colorItems}
                value={themeColor}
                onChange={setThemeColor}
                searchPlaceholder={t('themeOptionSearch')}
                maxHeight={520}
              />
            ),
          },
          {
            key: 'layouts',
            label: `${t('themeLayout')} · ${THEME_LAYOUTS.length}`,
            children: (
              <ThemeOptionGrid
                items={layoutItems}
                value={themeLayout}
                onChange={setThemeLayout}
                searchPlaceholder={t('themeOptionSearch')}
                maxHeight={520}
              />
            ),
          },
          {
            key: 'styles',
            label: `${t('pageStyle')} · ${PAGE_STYLES.length}`,
            children: (
              <ThemeOptionGrid
                items={styleItems}
                value={pageStyle}
                onChange={setPageStyle}
                searchPlaceholder={t('themeOptionSearch')}
                maxHeight={560}
              />
            ),
          },
          {
            key: 'effects',
            label: t('effectsLevel'),
            children: (
              <div className="theme-studio__effect-grid">
                {effectLevels.map((level) => (
                  <button
                    type="button"
                    key={level}
                    className={`theme-studio-effect${effectsLevel === level ? ' theme-studio-effect--active' : ''}`}
                    onClick={() => setEffectsLevel(level)}
                    aria-pressed={effectsLevel === level}
                  >
                    <span className="theme-studio-effect__icon">{effectIcon(level)}</span>
                    <span>
                      <strong>{t(`effectsLevel_${level}`)}</strong>
                      <small>{t(`effectsLevel_${level}_desc`)}</small>
                    </span>
                    {effectsLevel === level && <CheckOutlined />}
                  </button>
                ))}
                <Text type="secondary" className="theme-studio__effect-note">
                  {t('effectsLevel_hint')}
                </Text>
              </div>
            ),
          },
        ]}
      />
    </section>
  );
}
