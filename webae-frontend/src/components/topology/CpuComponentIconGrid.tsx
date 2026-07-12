import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type { TopologyNodeDto } from '@/types/dto';
import { iconItemFromRegistryId } from '@/utils/icon';
import { summarizeCpuComponents, type CpuComponentGroup } from '@/utils/cpuComponents';
export interface CpuComponentIconGridProps {
  node: TopologyNodeDto;
}

function componentLabel(kind: CpuComponentGroup['kind'], t: (key: string) => string): string {
  return t(`topologyCpuComponent_${kind}`);
}

export function CpuComponentIconGrid({ node }: CpuComponentIconGridProps) {
  const { t } = useI18n();
  const groups = summarizeCpuComponents(node);
  if (groups.length === 0) return null;

  return (
    <div className="cpu-component-icon-grid" role="list" aria-label={t('topologyCpuComposition')}>
      {groups.map((group) => {
        const label = componentLabel(group.kind, t);
        return (
          <span
            key={group.kind}
            className="cpu-component-icon-item"
            role="listitem"
            title={`${label} ×${group.count}`}
          >
            <Icon
              item={iconItemFromRegistryId(group.iconId, label)}
              size={28}
              linkToWiki={false}
              alt={label}
            />
            <span className="cpu-component-icon-label">{label}</span>
            <span className="cpu-component-icon-count">×{group.count}</span>
          </span>
        );
      })}
    </div>
  );
}
