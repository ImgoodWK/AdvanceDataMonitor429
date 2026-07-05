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
        return snap;
    }
}
