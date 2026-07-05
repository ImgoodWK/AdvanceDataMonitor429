import { Divider, Space, Switch, Typography } from 'antd';
import type { DashboardSettings, DashboardWidgetConfig } from '@/utils/presets';
import { ColorField } from './ColorField';
import { ColorListField } from './ColorListField';

const { Text } = Typography;

type ColorOverrides = NonNullable<DashboardWidgetConfig['colors']>;

interface WidgetColorSectionProps {
  widget: DashboardWidgetConfig;
  settings: DashboardSettings;
  t: (key: string, arg?: string | number) => string;
  colorsInherit: boolean;
  ensureColors: () => ColorOverrides;
  patchColors: (p: Partial<ColorOverrides>) => void;
}

/** Shared + type-specific color fields for EditWidgetModal. */
export function WidgetColorSection({
  widget,
  settings,
  t,
  colorsInherit,
  ensureColors,
  patchColors,
}: WidgetColorSectionProps) {
  const colors = ensureColors();

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="small">
      <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
        <Text strong>{t('dashCfgWidgetColors')}</Text>
        <Space>
          <Text type="secondary" style={{ fontSize: '0.72rem' }}>
            {t('dashCfgWidgetColorsHint')}
          </Text>
          <Switch
            size="small"
            checked={colorsInherit}
            onChange={(checked) => patchColors({ inheritDefault: checked })}
            aria-label={t('dashCfgInheritDefault')}
          />
        </Space>
      </Space>

      <ColorField
        label={t('dashCfgBackgroundColor')}
        value={colors.backgroundColor}
        onChange={(v) => patchColors({ backgroundColor: v })}
        disabled={colorsInherit}
      />
      <ColorField
        label={t('dashCfgBorderColor')}
        value={colors.borderColor}
        onChange={(v) => patchColors({ borderColor: v })}
        disabled={colorsInherit}
      />
      <ColorField
        label={t('dashCfgTitleColor')}
        value={colors.titleColor}
        onChange={(v) => patchColors({ titleColor: v })}
        disabled={colorsInherit}
      />

      <Divider style={{ margin: '8px 0' }} />
      {renderTypeColors(widget, t, colors, patchColors, colorsInherit)}
    </Space>
  );
}

function renderTypeColors(
  widget: DashboardWidgetConfig,
  t: (key: string) => string,
  colors: ColorOverrides,
  patchColors: (p: Partial<ColorOverrides>) => void,
  colorsInherit: boolean
) {
  switch (widget.type) {
    case 'statCard':
      return (
        <ColorField
          label={t('dashCfgChartColor')}
          value={colors.chartColor}
          onChange={(v) => patchColors({ chartColor: v })}
          disabled={colorsInherit}
        />
      );

    case 'progressBar':
      return (
        <>
          <ColorField
            label={t('dashCfgProgressTrackColor')}
            value={colors.progressTrackColor}
            onChange={(v) => patchColors({ progressTrackColor: v })}
            disabled={colorsInherit}
          />
          <ColorField
            label={t('dashCfgProgressFillColor')}
            value={colors.progressFillColor}
            onChange={(v) => patchColors({ progressFillColor: v })}
            disabled={colorsInherit}
          />
        </>
      );

    case 'gauge':
      return (
        <>
          <ColorField
            label={t('dashCfgGaugeTrackColor')}
            value={colors.gaugeTrackColor}
            onChange={(v) => patchColors({ gaugeTrackColor: v })}
            disabled={colorsInherit}
          />
          <ColorField
            label={t('dashCfgGaugeStrokeColor')}
            value={colors.gaugeStrokeColor}
            onChange={(v) => patchColors({ gaugeStrokeColor: v })}
            disabled={colorsInherit}
          />
        </>
      );

    case 'lineChart':
      return (
        <>
          <ColorField
            label={t('dashCfgChartLineColor')}
            value={colors.chartLineColor}
            onChange={(v) => patchColors({ chartLineColor: v })}
            disabled={colorsInherit}
          />
          <ColorField
            label={t('dashCfgChartAreaColor')}
            value={colors.chartAreaColor}
            onChange={(v) => patchColors({ chartAreaColor: v })}
            disabled={colorsInherit}
          />
          <ColorField
            label={t('dashCfgChartGridColor')}
            value={colors.chartGridColor}
            onChange={(v) => patchColors({ chartGridColor: v })}
            disabled={colorsInherit}
          />
          <ColorField
            label={t('dashCfgChartPointColor')}
            value={colors.chartPointColor}
            onChange={(v) => patchColors({ chartPointColor: v })}
            disabled={colorsInherit}
          />
          <ColorField
            label={t('dashCfgAxisTextColor')}
            value={colors.axisTextColor}
            onChange={(v) => patchColors({ axisTextColor: v })}
            disabled={colorsInherit}
          />
          {(widget.dataSource === 'powerHistory' ||
            widget.dataSource.startsWith('steam') ||
            widget.dataSource.startsWith('eu')) && (
            <>
              <Divider style={{ margin: '4px 0' }} />
              <ColorField
                label={t('dashCfgChartSecondaryLineColor')}
                value={colors.chartSecondaryLineColor}
                onChange={(v) => patchColors({ chartSecondaryLineColor: v })}
                disabled={colorsInherit}
              />
              <ColorField
                label={t('dashCfgChartSecondaryAreaColor')}
                value={colors.chartSecondaryAreaColor}
                onChange={(v) => patchColors({ chartSecondaryAreaColor: v })}
                disabled={colorsInherit}
              />
            </>
          )}
        </>
      );

    case 'barChart':
      return widget.dataSource === 'storageByCategory' ? (
        <>
          <ColorField label={t('dashCfgCategoryItemsColor')} value={colors.categoryItemsColor} onChange={(v) => patchColors({ categoryItemsColor: v })} disabled={colorsInherit} />
          <ColorField label={t('dashCfgCategoryFluidsColor')} value={colors.categoryFluidsColor} onChange={(v) => patchColors({ categoryFluidsColor: v })} disabled={colorsInherit} />
          <ColorField label={t('dashCfgCategoryEssentiaColor')} value={colors.categoryEssentiaColor} onChange={(v) => patchColors({ categoryEssentiaColor: v })} disabled={colorsInherit} />
          <ColorField label={t('dashCfgChartGridColor')} value={colors.chartGridColor} onChange={(v) => patchColors({ chartGridColor: v })} disabled={colorsInherit} />
        </>
      ) : (
        <>
          <ColorListField
            label={t('dashCfgBarSegmentColors')}
            value={colors.barSegmentColors}
            onChange={(v) => patchColors({ barSegmentColors: v })}
            disabled={colorsInherit}
          />
          <ColorField
            label={t('dashCfgChartGridColor')}
            value={colors.chartGridColor}
            onChange={(v) => patchColors({ chartGridColor: v })}
            disabled={colorsInherit}
          />
        </>
      );

    case 'pieChart':
      return widget.dataSource === 'storageByCategory' ? (
        <>
          <ColorField label={t('dashCfgCategoryItemsColor')} value={colors.categoryItemsColor} onChange={(v) => patchColors({ categoryItemsColor: v })} disabled={colorsInherit} />
          <ColorField label={t('dashCfgCategoryFluidsColor')} value={colors.categoryFluidsColor} onChange={(v) => patchColors({ categoryFluidsColor: v })} disabled={colorsInherit} />
          <ColorField label={t('dashCfgCategoryEssentiaColor')} value={colors.categoryEssentiaColor} onChange={(v) => patchColors({ categoryEssentiaColor: v })} disabled={colorsInherit} />
        </>
      ) : (
        <ColorListField
          label={t('dashCfgPieSliceColors')}
          value={colors.pieSliceColors}
          onChange={(v) => patchColors({ pieSliceColors: v })}
          disabled={colorsInherit}
        />
      );

    case 'radarChart':
      return (
        <>
          <ColorField
            label={t('dashCfgRadarAxisColor')}
            value={colors.radarAxisColor}
            onChange={(v) => patchColors({ radarAxisColor: v })}
            disabled={colorsInherit}
          />
          <ColorField
            label={t('dashCfgChartColor')}
            value={colors.chartColor}
            onChange={(v) => patchColors({ chartColor: v })}
            disabled={colorsInherit}
          />
        </>
      );

    case 'dataTable':
      return (
        <>
          <ColorField
            label={t('dashCfgChartColor')}
            value={colors.chartColor}
            onChange={(v) => patchColors({ chartColor: v })}
            disabled={colorsInherit}
          />
          <ColorField
            label={t('dashCfgDataTableRowAltColor')}
            value={colors.dataTableRowAltColor}
            onChange={(v) => patchColors({ dataTableRowAltColor: v })}
            disabled={colorsInherit}
          />
        </>
      );

    default:
      return null;
  }
}
