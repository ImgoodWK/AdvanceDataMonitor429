/**
 * 玩家头像工具 —— 浏览器直连 Crafatar CDN 获取正版皮肤头像。
 *
 * 仅在浏览器有外网时生效；纯内网环境由调用方做首字母圆形占位回退。
 *
 * Crafatar 文档：https://crafatar.com
 * - avatars: 2D 头像（默认 size=64，overlay 可加帽子层）
 * - renders/head: 3D 头部渲染（size 推荐 128）
 *
 * UUID 必须是带连字符的标准 36 字符格式（Crafatar 也接受无连字符的 32 字符简写）。
 */

const CRAFATAR_BASE = 'https://crafatar.com';

/** 是否为合法的 Minecraft UUID（接受 32 或 36 字符两种形式）。 */
export function isValidMinecraftUuid(uuid: string | null | undefined): uuid is string {
  if (!uuid) return false;
  const trimmed = uuid.trim();
  if (!trimmed) return false;
  // 36 字符带连字符：xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
  // 32 字符无连字符
  return /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(trimmed)
    || /^[0-9a-fA-F]{32}$/.test(trimmed);
}

/**
 * 2D 头像 URL（用于聊天气泡、玩家列表小头像）。
 * @param uuid 玩家 UUID
 * @param size 像素尺寸（默认 64，Crafatar 支持 8~512）
 * @param overlay 是否叠加帽子/头盔层（默认 true）
 */
export function getAvatarUrl(uuid: string | null | undefined, size = 64, overlay = true): string | null {
  if (!isValidMinecraftUuid(uuid)) return null;
  const params = `?size=${size}${overlay ? '&overlay' : ''}`;
  return `${CRAFATAR_BASE}/avatars/${uuid!.replace(/-/g, '')}${params}`;
}

/**
 * 3D 头部渲染 URL（用于详情面板大头像）。
 * @param uuid 玩家 UUID
 * @param size 像素尺寸（默认 128）
 */
export function getHeadUrl(uuid: string | null | undefined, size = 128): string | null {
  if (!isValidMinecraftUuid(uuid)) return null;
  return `${CRAFATAR_BASE}/renders/head/${uuid!.replace(/-/g, '')}?size=${size}`;
}

/**
 * 由玩家名生成首字母（最多 2 字符），用于头像加载失败时的圆形占位。
 * 优先取首个有效字符；中文名取首字，英文名取大写首字母。
 */
export function avatarInitial(name: string | null | undefined): string {
  if (!name) return '?';
  const trimmed = name.trim();
  if (!trimmed) return '?';
  // 取首个字符即可，避免长名溢出
  return trimmed.charAt(0).toUpperCase();
}

/** 简单确定性哈希，用于给首字母占位分配稳定颜色。 */
export function avatarColorSeed(uuid: string | null | undefined, name: string | null | undefined): number {
  const src = (uuid && uuid.trim()) || (name && name.trim()) || 'unknown';
  let hash = 0;
  for (let i = 0; i < src.length; i++) {
    hash = ((hash << 5) - hash) + src.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}
