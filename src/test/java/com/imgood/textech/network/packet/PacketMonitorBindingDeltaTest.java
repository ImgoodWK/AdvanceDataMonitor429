package com.imgood.textech.network.packet;

import java.lang.reflect.Field;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Assert;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketMonitorBindingDeltaTest {

    @Test
    public void roundTripsRevisionPatchAndAppendedSamples() throws Exception {
        NBTTagCompound patch = new NBTTagCompound();
        patch.setString("title", "EU");
        NBTTagCompound point = new NBTTagCompound();
        point.setDouble("data", 42.5D);
        NBTTagList appended = new NBTTagList();
        appended.appendTag(point);

        PacketMonitorBindingDelta source = new PacketMonitorBindingDelta(1, 2, 3, 35, 77, false, patch, appended);
        ByteBuf buffer = Unpooled.buffer();
        source.toBytes(buffer);
        PacketMonitorBindingDelta decoded = new PacketMonitorBindingDelta();
        decoded.fromBytes(buffer);

        Assert.assertEquals(35, intField(decoded, "index"));
        Assert.assertEquals(77, intField(decoded, "revision"));
        Assert.assertEquals("EU", compoundField(decoded, "fieldPatch").getString("title"));
        Assert.assertEquals(
            42.5D,
            listField(decoded, "appendedData").getCompoundTagAt(0)
                .getDouble("data"),
            0.0D);
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = target.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static NBTTagCompound compoundField(Object target, String name) throws Exception {
        Field field = target.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return (NBTTagCompound) field.get(target);
    }

    private static NBTTagList listField(Object target, String name) throws Exception {
        Field field = target.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return (NBTTagList) field.get(target);
    }
}
