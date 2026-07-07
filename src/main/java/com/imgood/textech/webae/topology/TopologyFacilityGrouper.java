package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.imgood.textech.webae.topology.NetworkStatusEnumerator.NetworkFacility;

/**
 * Aggregates enumerated grid facilities by fine subtype + item id,
 * matching the in-game network tool merge-by-item behavior.
 */
public final class TopologyFacilityGrouper {

    private TopologyFacilityGrouper() {}

    public static final class AggregatedGroup {

        public TopologyNodeType type;
        public String subtype = "";
        public String groupKey;
        public String displayName;
        public String iconItemId = "";
        public int count;
        public int channelCostSum;
        public int patternCountSum;
        public final List<NetworkFacility> members = new ArrayList<NetworkFacility>();
    }

    public static List<AggregatedGroup> group(List<NetworkFacility> facilities) {
        if (facilities == null || facilities.isEmpty()) {
            return new ArrayList<AggregatedGroup>();
        }

        Map<String, AggregatedGroup> byKey = new LinkedHashMap<String, AggregatedGroup>();
        for (NetworkFacility facility : facilities) {
            if (facility == null) {
                continue;
            }
            String subtype = facility.subtype == null || facility.subtype.isEmpty() ? TopologyRules.SUB_MISC
                : facility.subtype;
            String itemId = facility.representationItemId == null ? "" : facility.representationItemId;
            String key = subtype + "|" + itemId;
            AggregatedGroup group = byKey.get(key);
            if (group == null) {
                group = new AggregatedGroup();
                group.type = facility.type;
                group.subtype = subtype;
                group.groupKey = key;
                group.displayName = facility.displayName;
                group.iconItemId = itemId;
                byKey.put(key, group);
            }
            group.count++;
            group.channelCostSum += Math.max(0, facility.channelCost);
            group.patternCountSum += Math.max(0, facility.patternCount);
            group.members.add(facility);
        }

        List<AggregatedGroup> groups = new ArrayList<AggregatedGroup>(byKey.values());
        Collections.sort(groups, new Comparator<AggregatedGroup>() {

            @Override
            public int compare(AggregatedGroup a, AggregatedGroup b) {
                int order = Integer
                    .compare(TopologyRules.branchOrderIndex(a.subtype), TopologyRules.branchOrderIndex(b.subtype));
                if (order != 0) {
                    return order;
                }
                String nameA = a.displayName == null ? "" : a.displayName;
                String nameB = b.displayName == null ? "" : b.displayName;
                return nameA.compareToIgnoreCase(nameB);
            }
        });
        return groups;
    }
}
