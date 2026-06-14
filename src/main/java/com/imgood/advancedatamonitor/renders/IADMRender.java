package com.imgood.advancedatamonitor.renders;

import net.minecraft.nbt.NBTTagCompound;

public interface IADMRender {

    void render(NBTTagCompound nbt, double x, double y, double z, int facing);

    void cleanup();
}
