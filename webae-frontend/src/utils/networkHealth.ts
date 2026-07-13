import type { NetworkInfo } from '@/types/dto';

/** Treat missing `healthy` as reachable until the backend reports otherwise. */
export function isNetworkHealthy(network: NetworkInfo): boolean {
  return network.healthy !== false;
}

export function networkBaseLabel(network: NetworkInfo): string {
  return network.name || `Network ${network.networkId}`;
}

export function formatNetworkOptionLabel(
  network: NetworkInfo,
  unavailableSuffix: string
): string {
  const base = networkBaseLabel(network);
  if (isNetworkHealthy(network)) return base;
  return `${base} ${unavailableSuffix}`;
}

export function findFirstHealthyNetworkId(networks: NetworkInfo[]): number | null {
  const match = networks.find(isNetworkHealthy);
  return match != null ? match.networkId : null;
}

export function filterHealthyNetworkIds(
  ids: number[],
  networks: NetworkInfo[]
): number[] {
  if (networks.length === 0) return ids;
  const byId = new Map(networks.map((n) => [n.networkId, n]));
  return ids.filter((id) => {
    const net = byId.get(id);
    return net == null || isNetworkHealthy(net);
  });
}
