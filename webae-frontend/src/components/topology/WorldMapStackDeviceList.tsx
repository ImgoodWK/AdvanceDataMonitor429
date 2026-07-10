import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type { TopologyNodeDto } from '@/types/dto';
import { blockIconIdForNode } from '@/utils/aeCableColors';
import { filterNodesWithDetailPage, resolveGroupType, topologyNodeLabel } from '@/utils/topologyDevices';

export interface WorldMapStackDeviceListProps {
  nodes: TopologyNodeDto[];
  selectedNodeId?: string | null;
  onSelectNode: (node: TopologyNodeDto) => void;
}

export function WorldMapStackDeviceList({
  nodes,
  selectedNodeId,
  onSelectNode,
}: WorldMapStackDeviceListProps) {
  const { t } = useI18n();
  const rows = filterNodesWithDetailPage(nodes);

  if (rows.length === 0) {
    return (
      <div className="worldmap-stack-device-list-empty">{t('worldMapPopupEmpty')}</div>
    );
  }

  return (
    <ul className="worldmap-stack-device-list" role="listbox">
      {rows.map((node) => {
        const iconId = blockIconIdForNode(node.type, node.iconItemId);
        const subtype = resolveGroupType(node);
        const selected = selectedNodeId === node.id;
        const device = node.devices?.[0];
        const coords = device ? `${device.x}, ${device.y}, ${device.z}` : null;
        return (
          <li key={node.id}>
            <button
              type="button"
              role="option"
              aria-selected={selected}
              className={`worldmap-stack-device-row${selected ? ' worldmap-stack-device-row--selected' : ''}`}
              onClick={() => onSelectNode(node)}
            >
              <span className="worldmap-stack-device-icon">
                <Icon id={iconId} size={24} linkToWiki={false} alt="" />
              </span>
              <span className="worldmap-stack-device-text">
                <span className="worldmap-stack-device-name">{topologyNodeLabel(node)}</span>
                <span className="worldmap-stack-device-meta">
                  {t(`topologySubtype_${subtype}`)}
                  {coords ? ` · ${coords}` : ''}
                </span>
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
