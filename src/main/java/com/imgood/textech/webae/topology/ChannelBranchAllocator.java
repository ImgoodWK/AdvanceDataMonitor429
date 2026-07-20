package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.imgood.textech.webae.topology.TopologyFacilityGrouper.AggregatedGroup;

/**
 * Assigns channel-consuming device groups to four smart branches (8 channels each).
 * Same subtype prefers the same branch; podKind preferred lane is tried first (ae_budget_v2).
 * Overflow still places on the least-loaded branch and marks {@link Allocation#overflow}.
 */
public final class ChannelBranchAllocator {

    public static final int BRANCH_COUNT = TopologyRules.SMART_BRANCH_COUNT;
    public static final int CHANNELS_PER_BRANCH = TopologyRules.CABLE_SMART_MAX;

    private ChannelBranchAllocator() {}

    public static final class Allocation {

        public int branchIndex;
        public int slotIndex;
        public boolean overflow;
    }

    public static final class Result {

        public final Map<String, Allocation> byGroupKey = new LinkedHashMap<String, Allocation>();
        public final int[] branchUsed = new int[BRANCH_COUNT];
        public final int[] branchGroupCount = new int[BRANCH_COUNT];
        public final boolean[] branchOverflow = new boolean[BRANCH_COUNT];
    }

    public static Result allocate(List<AggregatedGroup> groups) {
        Result result = new Result();
        if (groups == null || groups.isEmpty()) {
            return result;
        }

        List<AggregatedGroup> channelGroups = new ArrayList<AggregatedGroup>();
        for (AggregatedGroup group : groups) {
            if (group != null && group.channelCostSum > 0) {
                channelGroups.add(group);
            }
        }

        Map<Integer, String> branchPrimarySubtype = new HashMap<Integer, String>();

        for (AggregatedGroup group : channelGroups) {
            int cost = Math.max(1, group.channelCostSum);
            int branch = pickBranch(group, result.branchUsed, branchPrimarySubtype, cost);
            Allocation alloc = new Allocation();
            alloc.branchIndex = branch;
            alloc.slotIndex = result.branchGroupCount[branch];
            result.byGroupKey.put(group.groupKey, alloc);
            result.branchUsed[branch] += cost;
            result.branchGroupCount[branch]++;
            if (result.branchUsed[branch] > CHANNELS_PER_BRANCH) {
                alloc.overflow = true;
                result.branchOverflow[branch] = true;
            }
            if (!branchPrimarySubtype.containsKey(branch)) {
                branchPrimarySubtype.put(branch, group.subtype);
            }
        }
        return result;
    }

    private static int pickBranch(AggregatedGroup group, int[] branchUsed, Map<Integer, String> branchPrimarySubtype,
        int cost) {
        String subtype = group.subtype == null ? "" : group.subtype;
        String podKind = TopologyRules.podKindForSubtype(subtype);
        int preferred = TopologyRules.preferredLaneForPodKind(podKind);

        if (preferred >= 0 && preferred < BRANCH_COUNT && branchUsed[preferred] + cost <= CHANNELS_PER_BRANCH) {
            String primary = branchPrimarySubtype.get(preferred);
            if (primary == null || primary.equals(subtype) || branchUsed[preferred] == 0) {
                return preferred;
            }
        }

        for (int i = 0; i < BRANCH_COUNT; i++) {
            String primary = branchPrimarySubtype.get(i);
            if (primary != null && primary.equals(subtype) && branchUsed[i] + cost <= CHANNELS_PER_BRANCH) {
                return i;
            }
        }

        for (int i = 0; i < BRANCH_COUNT; i++) {
            if (branchUsed[i] == 0 && cost <= CHANNELS_PER_BRANCH) {
                return i;
            }
        }

        for (int i = 0; i < BRANCH_COUNT; i++) {
            if (branchUsed[i] + cost <= CHANNELS_PER_BRANCH) {
                return i;
            }
        }

        int best = 0;
        int minUsed = branchUsed[0];
        for (int i = 1; i < BRANCH_COUNT; i++) {
            if (branchUsed[i] < minUsed) {
                minUsed = branchUsed[i];
                best = i;
            }
        }
        return best;
    }
}
