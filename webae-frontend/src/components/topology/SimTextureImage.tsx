import { memo } from 'react';
import { useAppContext } from '@/context/AppContext';
import { buildIconUrl } from '@/utils/icon';

interface SimTextureImageProps {
  iconId: string;
  x: number;
  y: number;
  size: number;
  fallbackColor?: string;
  /** Override global icon render mode — topology simulated view uses block textures. */
  renderMode?: string;
}

/** SVG <image> tile using AE icon cache textures. */
export const SimTextureImage = memo(function SimTextureImage({
  iconId,
  x,
  y,
  size,
  fallbackColor = '#334155',
  renderMode,
}: SimTextureImageProps) {
  const { token, iconPack, iconCacheEnabled, iconRenderMode } = useAppContext();
  const mode = renderMode ?? iconRenderMode;
  const href = buildIconUrl(iconId, iconPack, token, iconCacheEnabled, mode);

  if (!href) {
    return <rect x={x} y={y} width={size} height={size} fill={fallbackColor} rx={1} />;
  }

  return (
    <image
      href={href}
      x={x}
      y={y}
      width={size}
      height={size}
      preserveAspectRatio="xMidYMid meet"
      className="topology-sim-texture"
    />
  );
});
