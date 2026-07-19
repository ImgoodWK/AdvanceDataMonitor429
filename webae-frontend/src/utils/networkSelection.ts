/** Keep a page-local network selection valid when the global selection changes. */
export function resolveActiveNetworkId(
  selectedNetworks: number[],
  preferredNetwork: number | null
): number {
  if (preferredNetwork != null && selectedNetworks.includes(preferredNetwork)) {
    return preferredNetwork;
  }
  return selectedNetworks[0] ?? 0;
}
