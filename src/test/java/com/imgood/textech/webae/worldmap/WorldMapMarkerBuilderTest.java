package com.imgood.textech.webae.worldmap;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.imgood.textech.webae.topology.TopologyNode;
import com.imgood.textech.webae.topology.TopologySnapshot;

public class WorldMapMarkerBuilderTest {

    @Test
    public void controllerMultiblockEmitsSingleAnchorMarker() throws Exception {
        TopologySnapshot snapshot = newSnapshot();
        snapshot.nodes = new ArrayList<TopologyNode>();

        TopologyNode controller = new TopologyNode();
        controller.id = "controller:0";
        controller.type = "controller";
        controller.subtype = "controller";
        controller.displayName = "ME Controller";
        controller.devices = deviceGrid(3, 64, 10, 20);
        snapshot.nodes.add(controller);

        List<WorldMapMarkerDto> markers = WorldMapMarkerBuilder.fromLogicalSnapshot(snapshot);
        Assert.assertEquals(1, markers.size());
        Assert.assertEquals(64, markers.get(0).y);
        Assert.assertEquals(10, markers.get(0).x);
        Assert.assertEquals(20, markers.get(0).z);
    }

    @Test
    public void cpuMultiblockEmitsSingleAnchorMarker() throws Exception {
        TopologySnapshot snapshot = newSnapshot();
        snapshot.nodes = new ArrayList<TopologyNode>();

        TopologyNode cpu = new TopologyNode();
        cpu.id = "cpu:0";
        cpu.type = "cpu";
        cpu.subtype = "cpu";
        cpu.displayName = "Crafting CPU";
        cpu.devices = new ArrayList<TopologyNode.DeviceRecord>();
        cpu.devices.add(device("storage", 1, 2, 3));
        cpu.devices.add(device("monitor", 4, 5, 6));
        snapshot.nodes.add(cpu);

        List<WorldMapMarkerDto> markers = WorldMapMarkerBuilder.fromLogicalSnapshot(snapshot);
        Assert.assertEquals(1, markers.size());
        Assert.assertEquals(4, markers.get(0).x);
        Assert.assertEquals(5, markers.get(0).y);
        Assert.assertEquals(6, markers.get(0).z);
    }

    private static TopologySnapshot newSnapshot() throws Exception {
        Constructor<TopologySnapshot> ctor = TopologySnapshot.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static List<TopologyNode.DeviceRecord> deviceGrid(int count, int y, int baseX, int baseZ) {
        List<TopologyNode.DeviceRecord> devices = new ArrayList<TopologyNode.DeviceRecord>();
        for (int i = 0; i < count; i++) {
            devices.add(device("controller", baseX + i, y + i, baseZ + i));
        }
        return devices;
    }

    private static TopologyNode.DeviceRecord device(String label, int x, int y, int z) {
        TopologyNode.DeviceRecord device = new TopologyNode.DeviceRecord();
        device.displayName = label;
        device.x = x;
        device.y = y;
        device.z = z;
        device.dim = 0;
        return device;
    }
}
