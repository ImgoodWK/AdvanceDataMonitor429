import { memo, useEffect, useState } from 'react';
import { useAppContext } from '@/context/AppContext';
import { buildIconUrl } from '@/utils/icon';

interface SimTextureImageProps {
  iconId?: string;
  /** When set, used instead of icon-pack `/api/icon` (e.g. AE2 cable classpath textures). */
  href?: string | null;
  x: number;
  y: number;
  size: number;
  fallbackColor?: string;
  /** Override global icon render mode — topology simulated view uses block textures. */
  renderMode?: string;
}

/** SVG <image> tile using AE icon cache textures or an explicit URL. */
export const SimTextureImage = memo(function SimTextureImage({
  iconId,
  href: hrefOverride,
  x,
  y,
  size,
  fallbackColor = '#334155',
  renderMode,
}: SimTextureImageProps) {
  const { token, iconPack, iconCacheEnabled, iconRenderMode } = useAppContext();
  const mode = renderMode ?? iconRenderMode;
  const fromIcon =
    hrefOverride == null || hrefOverride === ''
      ? buildIconUrl(iconId, iconPack, token, iconCacheEnabled, mode)
      : '';
  const href = hrefOverride || fromIcon;
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [href]);

  if (!href || failed) {
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
      onError={() => setFailed(true)}
    />
  );
});
