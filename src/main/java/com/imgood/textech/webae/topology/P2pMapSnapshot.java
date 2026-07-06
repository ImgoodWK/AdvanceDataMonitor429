package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated P2P frequency map for one AE network (Phase 10).
 */
public final class P2pMapSnapshot {

    public int networkId;
    public long timestamp;
    public int tunnelCount;
    public int frequencyCount;
    public List<P2pFrequencyGroupDto> groups = new ArrayList<P2pFrequencyGroupDto>();
    /** Power P2P channels with estimated EU/t (Phase 3.2); empty when none enumerated. */
    public List<P2pPowerChannelDto> powerChannels = new ArrayList<P2pPowerChannelDto>();

    public static final class P2pPowerChannelDto {

        public int frequency;
        public String frequencyHex = "";
        public double avgEuPerTick;
        public int endpointCount;
    }

    public static final class P2pFrequencyGroupDto {

        public int frequency;
        public String frequencyHex = "";
        public String type = "";
        public int endpointCount;
        public List<P2pTunnelDto> endpoints = new ArrayList<P2pTunnelDto>();
    }

    /** Build frequency groups from flat tunnel list. */
    public static P2pMapSnapshot fromTunnels(int networkId, List<P2pTunnelDto> tunnels) {
        P2pMapSnapshot snap = new P2pMapSnapshot();
        snap.networkId = networkId;
        snap.timestamp = System.currentTimeMillis();
        snap.tunnelCount = tunnels != null ? tunnels.size() : 0;
        if (tunnels == null || tunnels.isEmpty()) {
            snap.frequencyCount = 0;
            return snap;
        }
        Map<Integer, P2pFrequencyGroupDto> map = new HashMap<Integer, P2pFrequencyGroupDto>();
        for (P2pTunnelDto t : tunnels) {
            if (t == null) {
                continue;
            }
            P2pFrequencyGroupDto group = map.get(t.frequency);
            if (group == null) {
                group = new P2pFrequencyGroupDto();
                group.frequency = t.frequency;
                group.frequencyHex = t.frequencyHex;
                group.type = t.type;
                map.put(t.frequency, group);
            }
            group.endpoints.add(t);
            group.endpointCount = group.endpoints.size();
        }
        List<P2pFrequencyGroupDto> groups = new ArrayList<P2pFrequencyGroupDto>(map.values());
        Collections.sort(groups, new java.util.Comparator<P2pFrequencyGroupDto>() {

            @Override
            public int compare(P2pFrequencyGroupDto a, P2pFrequencyGroupDto b) {
                return a.frequency - b.frequency;
            }
        });
        snap.groups = groups;
        snap.frequencyCount = groups.size();
        snap.powerChannels = buildPowerChannels(groups, tunnels);
        return snap;
    }

    private static List<P2pPowerChannelDto> buildPowerChannels(List<P2pFrequencyGroupDto> groups,
        List<P2pTunnelDto> tunnels) {
        List<P2pPowerChannelDto> channels = new ArrayList<P2pPowerChannelDto>();
        if (groups == null) {
            return channels;
        }
        for (P2pFrequencyGroupDto group : groups) {
            if (group == null || group.type == null) {
                continue;
            }
            String typeLower = group.type.toLowerCase();
            if (!typeLower.contains("power")) {
                continue;
            }
            P2pPowerChannelDto ch = new P2pPowerChannelDto();
            ch.frequency = group.frequency;
            ch.frequencyHex = group.frequencyHex;
            ch.endpointCount = group.endpointCount;
            ch.avgEuPerTick = P2pTunnelEnumerator.probePowerEuPerTick(tunnels, group.frequency);
            channels.add(ch);
        }
        return channels;
    }
}
