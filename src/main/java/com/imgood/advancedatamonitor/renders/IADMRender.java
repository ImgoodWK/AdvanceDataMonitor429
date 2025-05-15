package com.imgood.advancedatamonitor.renders;

import net.minecraft.nbt.NBTTagCompound;

import java.util.HashMap;
import java.util.Map;

public interface IADMRender {
    void render(NBTTagCompound nbt, double x, double y, double z, int facing);
    void cleanup();
}


