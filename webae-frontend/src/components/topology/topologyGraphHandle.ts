/** Imperative zoom/pan API shared by abstract (Cytoscape) and simulated topology views. */
export interface TopologyGraphHandle {
  resetView: () => void;
  fitView: () => void;
  zoomIn: () => void;
  zoomOut: () => void;
}

export const TOPOLOGY_ZOOM_FACTOR = 1.15;
export const TOPOLOGY_MIN_ZOOM = 0.35;
export const TOPOLOGY_MAX_ZOOM = 4;
