import { useMemo } from 'react';

import {
  Alert,
  Button,
  Checkbox,
  Select,
  Spin,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  HistoryOutlined,
  PlusOutlined,
  ReloadOutlined,
  SwapOutlined,
} from '@ant-design/icons';

import type { UseWorldMapVersionDiffResult } from '@/hooks/useWorldMapVersionDiff';
import { useI18n } from '@/i18n';
import { formatDateTime } from '@/utils/format';
import {
  summarizeWorldMapDiff,
  WORLD_MAP_DIFF_COLORS,
  type WorldMapDiffDataState,
} from '@/utils/worldMapVersionDiff';

const { Text } = Typography;

export interface WorldMapVersionControlsProps {
  history: UseWorldMapVersionDiffResult;
  readOnly?: boolean;
  onAddAnnotation?: () => void;
}

function diffStateMessage(
  state: WorldMapDiffDataState,
  truncated: boolean,
  error: string | null,
  serverMessage: string | undefined,
  t: (key: string) => string,
): string | null {
  if (state === 'loading' || state === 'ready') return null;
  if (state === 'error') return error || serverMessage || t('worldMapDiffUnavailable');
  if (state === 'empty') return t('worldMapDiffEmpty');
  if (state === 'partial') {
    return truncated ? t('worldMapDiffTruncated') : serverMessage || t('worldMapDiffPartial');
  }
  return serverMessage || t('worldMapDiffUnavailable');
}

function alertType(state: WorldMapDiffDataState): 'error' | 'warning' | 'info' {
  if (state === 'error') return 'error';
  if (state === 'unknown' || state === 'partial') return 'warning';
  return 'info';
}

export function WorldMapVersionControls({
  history,
  readOnly = false,
  onAddAnnotation,
}: WorldMapVersionControlsProps) {
  const { lang, t } = useI18n();

  const versionOptions = useMemo(
    () =>
      history.versions
        .filter((entry) => Number.isFinite(entry.version) && entry.version > 0)
        .slice()
        .sort((a, b) => b.version - a.version)
        .map((entry) => {
          const role =
            entry.version === history.currentVersion
              ? t('worldMapVersionCurrent')
              : entry.version === history.previousVersion
                ? t('worldMapVersionPrevious')
                : '';
          const timestamp = formatDateTime(entry.timestamp, lang);
          return {
            value: entry.version,
            label: [`v${entry.version}`, role, timestamp].filter(Boolean).join(' · '),
          };
        }),
    [history.currentVersion, history.previousVersion, history.versions, lang, t],
  );

  const diff = history.diffState.filteredData;
  const summary = useMemo(() => summarizeWorldMapDiff(diff), [diff]);
  const stateMessage = history.diffEnabled
    ? diffStateMessage(
        history.diffState.state,
        summary.truncated,
        history.diffState.error,
        diff?.message,
        t,
      )
    : null;
  const hasPair =
    history.fromVersion != null &&
    history.toVersion != null &&
    history.fromVersion !== history.toVersion;
  const fromVersionOptions = useMemo(
    () =>
      versionOptions.map((option) => ({
        ...option,
        disabled: option.value === history.toVersion,
      })),
    [history.toVersion, versionOptions],
  );
  const toVersionOptions = useMemo(
    () =>
      versionOptions.map((option) => ({
        ...option,
        disabled: option.value === history.fromVersion,
      })),
    [history.fromVersion, versionOptions],
  );
  const canComparePrevious =
    history.previousVersion != null &&
    history.currentVersion != null &&
    history.previousVersion !== history.currentVersion;

  return (
    <section className="worldmap-version-controls" aria-label={t('worldMapVersionHistory')}>
      <div className="worldmap-version-controls-head">
        <span className="worldmap-version-controls-title">
          <HistoryOutlined aria-hidden />
          <Text strong>{t('worldMapVersionHistory')}</Text>
        </span>
        <span className="worldmap-version-controls-actions">
          {!readOnly && onAddAnnotation && (
            <Tooltip title={t('worldMapAnnotationAdd')}>
              <Button
                type="text"
                size="small"
                icon={<PlusOutlined />}
                onClick={onAddAnnotation}
                aria-label={t('worldMapAnnotationAdd')}
              />
            </Tooltip>
          )}
          <span className="worldmap-diff-switch">
            <Switch
              size="small"
              checked={history.diffEnabled}
              disabled={!hasPair}
              onChange={history.setDiffEnabled}
              aria-label={t('worldMapDiffEnabled')}
            />
            <Text type="secondary">{t('worldMapDiff')}</Text>
          </span>
        </span>
      </div>

      {history.versionState.loading ? (
        <div className="worldmap-version-loading">
          <Spin size="small" />
          <Text type="secondary">{t('loading')}</Text>
        </div>
      ) : history.versionState.error ? (
        <Alert
          type="warning"
          showIcon
          message={t('worldMapVersionUnavailable')}
          description={history.versionState.error}
          action={
            <Tooltip title={t('retry')}>
              <Button
                size="small"
                type="text"
                icon={<ReloadOutlined />}
                onClick={() => void history.versionState.retry()}
                aria-label={t('retry')}
              />
            </Tooltip>
          }
        />
      ) : versionOptions.length === 0 ? (
        <Text type="secondary">{t('worldMapVersionUnavailable')}</Text>
      ) : (
        <>
          <div className="worldmap-version-selectors">
            <label>
              <span>{t('worldMapVersionFrom')}</span>
              <Select<number>
                size="small"
                value={history.fromVersion ?? undefined}
                options={fromVersionOptions}
                onChange={(value) => history.setFromVersion(value)}
                aria-label={t('worldMapVersionFrom')}
              />
            </label>
            <Tooltip title={t('worldMapVersionSwap')}>
              <Button
                type="text"
                size="small"
                className="worldmap-version-swap"
                icon={<SwapOutlined />}
                disabled={!hasPair}
                onClick={() => history.setVersionPair(history.toVersion, history.fromVersion)}
                aria-label={t('worldMapVersionSwap')}
              />
            </Tooltip>
            <label>
              <span>{t('worldMapVersionTo')}</span>
              <Select<number>
                size="small"
                value={history.toVersion ?? undefined}
                options={toVersionOptions}
                onChange={(value) => history.setToVersion(value)}
                aria-label={t('worldMapVersionTo')}
              />
            </label>
          </div>
          <Button
            size="small"
            block
            icon={<HistoryOutlined />}
            disabled={!canComparePrevious}
            onClick={() => history.comparePrevious()}
          >
            {t('worldMapVersionComparePrevious')}
          </Button>
        </>
      )}

      {history.diffEnabled && (
        <div className="worldmap-diff-controls">
          <div className="worldmap-diff-options">
            <Checkbox
              checked={history.includeMarkers}
              onChange={(event) => history.setDiffOptions({ includeMarkers: event.target.checked })}
            >
              {t('worldMapDiffMarkers')}
            </Checkbox>
            <Checkbox
              checked={history.includeTiles}
              onChange={(event) => history.setDiffOptions({ includeTiles: event.target.checked })}
            >
              {t('worldMapDiffTiles')}
            </Checkbox>
            {history.diffState.loading && <Spin size="small" />}
          </div>

          {diff && (
            <div className="worldmap-diff-legend" aria-label={t('worldMapDiff')}>
              <Tag color={WORLD_MAP_DIFF_COLORS.added}>
                {t('worldMapDiffAdded')} {summary.added}
              </Tag>
              <Tag color={WORLD_MAP_DIFF_COLORS.removed}>
                {t('worldMapDiffRemoved')} {summary.removed}
              </Tag>
              <Tag color={WORLD_MAP_DIFF_COLORS.changed}>
                {t('worldMapDiffChanged')} {summary.changed}
              </Tag>
              <Tag color={WORLD_MAP_DIFF_COLORS.moved}>
                {t('worldMapDiffMoved')} {summary.moved}
              </Tag>
              <Tag color={WORLD_MAP_DIFF_COLORS.unchanged}>
                {t('worldMapDiffUnchanged')} {summary.unchanged}
              </Tag>
              {summary.unknown > 0 && (
                <Tag color={WORLD_MAP_DIFF_COLORS.unknown}>
                  {t('worldMapDiffUnknown')} {summary.unknown}
                </Tag>
              )}
            </div>
          )}

          {stateMessage && (
            <Alert
              type={alertType(history.diffState.state)}
              showIcon
              message={stateMessage}
              action={
                history.diffState.state === 'error' ? (
                  <Tooltip title={t('retry')}>
                    <Button
                      size="small"
                      type="text"
                      icon={<ReloadOutlined />}
                      onClick={() => void history.diffState.retry()}
                      aria-label={t('retry')}
                    />
                  </Tooltip>
                ) : undefined
              }
            />
          )}
        </div>
      )}

      {history.annotationState.loading && (
        <div className="worldmap-version-loading">
          <Spin size="small" />
          <Text type="secondary">{t('worldMapAnnotations')}</Text>
        </div>
      )}
      {history.annotationState.error && (
        <Alert
          type="warning"
          showIcon
          message={t('worldMapAnnotationLoadFailed')}
          description={history.annotationState.error}
          action={
            <Tooltip title={t('retry')}>
              <Button
                size="small"
                type="text"
                icon={<ReloadOutlined />}
                onClick={() => void history.annotationState.retry()}
                aria-label={t('retry')}
              />
            </Tooltip>
          }
        />
      )}
      {!history.annotationState.loading &&
        !history.annotationState.error &&
        history.toVersion != null &&
        history.toVersion > 0 && (
          <div className="worldmap-annotation-summary">
            <Text type="secondary">{t('worldMapAnnotations')}</Text>
            <Tag>{history.annotationState.visible.length}</Tag>
          </div>
        )}
    </section>
  );
}
