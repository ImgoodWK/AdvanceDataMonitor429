import { memo, useMemo } from 'react';

import { Popover, Typography } from 'antd';

import { useI18n } from '@/i18n';
import type {
  WorldMapDiffMarkerDto,
  WorldMapMarkerChangeDto,
  WorldMapTileChangeDto,
  WorldMapVersionDiffResponse,
} from '@/types/dto';
import {
  describeWorldMapMarkerChange,
  normalizeWorldMapDiffStatus,
  worldMapDiffColor,
  type WorldMapDiffDetail,
  type WorldMapDiffStatus,
} from '@/utils/worldMapVersionDiff';
import {
  worldToScreen,
  type MapViewport,
  type WorldMapOrigin,
} from '@/utils/worldMapProjection';

const { Text } = Typography;

export interface WorldMapDiffOverlayProps {
  diff: WorldMapVersionDiffResponse | null;
  visible: boolean;
  viewport: MapViewport;
  origin: WorldMapOrigin;
  containerWidth: number;
  containerHeight: number;
}

interface MarkerVisual {
  key: string;
  detail: WorldMapDiffDetail;
  sx: number;
  sy: number;
  fromScreen: { sx: number; sy: number } | null;
  toScreen: { sx: number; sy: number } | null;
}

interface TileVisual {
  key: string;
  change: WorldMapTileChangeDto;
  status: WorldMapDiffStatus;
  color: string;
  left: number;
  top: number;
  width: number;
  height: number;
}

function finite(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function markerScreen(
  marker: WorldMapDiffMarkerDto | null | undefined,
  viewport: MapViewport,
  origin: WorldMapOrigin,
): { sx: number; sy: number } | null {
  const x = finite(marker?.x);
  const z = finite(marker?.z);
  if (x == null || z == null) return null;
  return worldToScreen(x, z, viewport, origin);
}

function markerCoordinates(marker: WorldMapDiffMarkerDto | null | undefined): string {
  if (!marker) return '--';
  const dimension = finite(marker.dimension) ?? finite(marker.dim);
  const x = finite(marker.x);
  const y = finite(marker.y);
  const z = finite(marker.z);
  const coordinates = [x, y, z].map((value) => (value == null ? '?' : String(value))).join(', ');
  return `${dimension == null ? 'D?' : `D${dimension}`} · ${coordinates}`;
}

function statusLabel(status: WorldMapDiffStatus, t: (key: string) => string): string {
  const key =
    status === 'added'
      ? 'worldMapDiffAdded'
      : status === 'removed'
        ? 'worldMapDiffRemoved'
        : status === 'changed'
          ? 'worldMapDiffChanged'
          : status === 'moved'
            ? 'worldMapDiffMoved'
            : status === 'unchanged'
              ? 'worldMapDiffUnchanged'
              : 'worldMapDiffUnknown';
  return t(key);
}

function statusGlyph(status: WorldMapDiffStatus): string {
  if (status === 'added') return '+';
  if (status === 'removed') return '−';
  if (status === 'moved') return '↔';
  if (status === 'changed') return '•';
  return '·';
}

function markerLabel(detail: WorldMapDiffDetail, t: (key: string) => string): string {
  return detail.label || t('worldMapDiffUnknownMarker');
}

function markerDetailContent(
  detail: WorldMapDiffDetail,
  t: (key: string) => string,
) {
  const hasDetails = detail.id || detail.from || detail.to;
  return (
    <div className="worldmap-diff-detail">
      {detail.id && (
        <div>
          <Text type="secondary">{t('worldMapDiffMarkerId')}</Text>
          <div>{detail.id}</div>
        </div>
      )}
      {detail.from && (
        <div>
          <Text type="secondary">{t('worldMapDiffFromPosition')}</Text>
          <div>{markerCoordinates(detail.from)}</div>
        </div>
      )}
      {detail.to && (
        <div>
          <Text type="secondary">{t('worldMapDiffToPosition')}</Text>
          <div>{markerCoordinates(detail.to)}</div>
        </div>
      )}
      {!hasDetails && <Text type="secondary">{t('worldMapDiffNoDetails')}</Text>}
    </div>
  );
}

function shortHash(value: string | null | undefined): string {
  if (!value) return '--';
  return value.length > 16 ? `${value.slice(0, 16)}…` : value;
}

function tileDetailContent(
  change: WorldMapTileChangeDto,
  t: (key: string) => string,
) {
  const dimension = finite(change.dimension) ?? finite(change.dim);
  return (
    <div className="worldmap-diff-detail">
      <div>
        <Text type="secondary">{t('worldMapDiffLayer')}</Text>
        <div>{change.layer || '--'}</div>
      </div>
      <div>
        <Text type="secondary">{t('worldMapDiffChunk')}</Text>
        <div>{`${dimension == null ? 'D?' : `D${dimension}`} · ${change.chunkX}, ${change.chunkZ}`}</div>
      </div>
      <div>
        <Text type="secondary">{t('worldMapDiffFromHash')}</Text>
        <div>{shortHash(change.fromSha256)}</div>
      </div>
      <div>
        <Text type="secondary">{t('worldMapDiffToHash')}</Text>
        <div>{shortHash(change.toSha256)}</div>
      </div>
    </div>
  );
}

function visiblePoint(sx: number, sy: number, width: number, height: number): boolean {
  const margin = 48;
  return sx >= -margin && sx <= width + margin && sy >= -margin && sy <= height + margin;
}

function WorldMapDiffOverlayInner({
  diff,
  visible,
  viewport,
  origin,
  containerWidth,
  containerHeight,
}: WorldMapDiffOverlayProps) {
  const { t } = useI18n();

  const markerVisuals = useMemo<MarkerVisual[]>(() => {
    if (!visible || !diff?.markerChanges) return [];
    const out: MarkerVisual[] = [];
    diff.markerChanges.forEach((change: WorldMapMarkerChangeDto, index) => {
      const detail = describeWorldMapMarkerChange(change);
      const fromScreen = markerScreen(detail.from, viewport, origin);
      const toScreen = markerScreen(detail.to, viewport, origin);
      const primary = detail.status === 'removed' ? fromScreen : toScreen ?? fromScreen;
      if (!primary || !visiblePoint(primary.sx, primary.sy, containerWidth, containerHeight)) return;
      out.push({
        key: `marker-${detail.id ?? index}-${index}`,
        detail,
        sx: primary.sx,
        sy: primary.sy,
        fromScreen,
        toScreen,
      });
    });
    return out;
  }, [containerHeight, containerWidth, diff?.markerChanges, origin, viewport, visible]);

  const tileVisuals = useMemo<TileVisual[]>(() => {
    if (!visible || !diff?.tileChanges) return [];
    const out: TileVisual[] = [];
    diff.tileChanges.forEach((change, index) => {
      const chunkX = finite(change.chunkX);
      const chunkZ = finite(change.chunkZ);
      if (chunkX == null || chunkZ == null) return;
      const topLeft = worldToScreen(chunkX * 16, chunkZ * 16 + 16, viewport, origin);
      const bottomRight = worldToScreen(chunkX * 16 + 16, chunkZ * 16, viewport, origin);
      const left = Math.min(topLeft.sx, bottomRight.sx);
      const top = Math.min(topLeft.sy, bottomRight.sy);
      const width = Math.max(2, Math.abs(bottomRight.sx - topLeft.sx));
      const height = Math.max(2, Math.abs(bottomRight.sy - topLeft.sy));
      if (
        left > containerWidth + 8 ||
        top > containerHeight + 8 ||
        left + width < -8 ||
        top + height < -8
      ) return;
      const status = normalizeWorldMapDiffStatus(change.status);
      out.push({
        key: `tile-${change.key || `${change.layer}-${chunkX}-${chunkZ}`}-${index}`,
        change,
        status,
        color: worldMapDiffColor(status),
        left,
        top,
        width,
        height,
      });
    });
    return out;
  }, [containerHeight, containerWidth, diff?.tileChanges, origin, viewport, visible]);

  if (!visible || !diff) return null;

  return (
    <div className="worldmap-diff-overlay" aria-label={t('worldMapDiff')}>
      <svg className="worldmap-diff-move-paths" aria-hidden="true">
        {markerVisuals.map((visual) =>
          visual.detail.status === 'moved' && visual.fromScreen && visual.toScreen ? (
            <line
              key={`${visual.key}-path`}
              x1={visual.fromScreen.sx}
              y1={visual.fromScreen.sy}
              x2={visual.toScreen.sx}
              y2={visual.toScreen.sy}
              stroke={visual.detail.color}
            />
          ) : null,
        )}
      </svg>

      {tileVisuals.map((visual) => {
        const style = {
          left: visual.left,
          top: visual.top,
          width: visual.width,
          height: visual.height,
          borderColor: visual.color,
          backgroundColor: `${visual.color}26`,
        };
        // A normal retained comparison commonly has unchanged rows for most
        // visible chunks. They remain visible in gray and counted in the
        // summary, but must not cover the entire map with hit targets.
        if (visual.status === 'unchanged') {
          return (
            <div
              key={visual.key}
              className="worldmap-diff-tile worldmap-diff-unchanged"
              style={style}
              aria-hidden="true"
            />
          );
        }
        return (
          <Popover
            key={visual.key}
            title={statusLabel(visual.status, t)}
            content={tileDetailContent(visual.change, t)}
            trigger="click"
          >
            <button
              type="button"
              className={`worldmap-diff-hit worldmap-diff-tile worldmap-diff-${visual.status}`}
              style={style}
              onPointerDown={(event) => event.stopPropagation()}
              onClick={(event) => event.stopPropagation()}
              aria-label={`${statusLabel(visual.status, t)} · ${visual.change.layer} · ${visual.change.chunkX}, ${visual.change.chunkZ}`}
            />
          </Popover>
        );
      })}

      {markerVisuals.map((visual) => (
        <Popover
          key={visual.key}
          title={`${statusLabel(visual.detail.status, t)} · ${markerLabel(visual.detail, t)}`}
          content={markerDetailContent(visual.detail, t)}
          trigger="click"
        >
          <button
            type="button"
            className={`worldmap-diff-hit worldmap-diff-marker worldmap-diff-${visual.detail.status}`}
            style={{
              left: visual.sx,
              top: visual.sy,
              color: visual.detail.color,
              borderColor: visual.detail.color,
            }}
            onPointerDown={(event) => event.stopPropagation()}
            onClick={(event) => event.stopPropagation()}
            aria-label={`${statusLabel(visual.detail.status, t)} · ${markerLabel(visual.detail, t)}`}
          >
            {statusGlyph(visual.detail.status)}
          </button>
        </Popover>
      ))}
    </div>
  );
}

export const WorldMapDiffOverlay = memo(WorldMapDiffOverlayInner);
