package com.imgood.advancedatamonitor.items;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import net.minecraft.nbt.NBTTagCompound;

public class PlannerEntry {

    private int slotIndex;
    private String text;
    private long timestamp;
    private boolean completed;

    public PlannerEntry() {
        this.slotIndex = 0;
        this.text = "";
        this.timestamp = System.currentTimeMillis();
        this.completed = false;
    }

    public PlannerEntry(int slotIndex, String text) {
        this.slotIndex = slotIndex;
        this.text = text == null ? "" : text;
        this.timestamp = System.currentTimeMillis();
        this.completed = false;
    }

    public PlannerEntry(int slotIndex, String text, long timestamp, boolean completed) {
        this.slotIndex = slotIndex;
        this.text = text == null ? "" : text;
        this.timestamp = timestamp;
        this.completed = completed;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void toggleCompleted() {
        this.completed = !this.completed;
    }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("slotIndex", slotIndex);
        tag.setString("text", text);
        tag.setLong("timestamp", timestamp);
        tag.setBoolean("completed", completed);
        return tag;
    }

    public static PlannerEntry fromNBT(NBTTagCompound tag) {
        if (tag == null) {
            return null;
        }
        return new PlannerEntry(
            tag.getInteger("slotIndex"),
            tag.getString("text"),
            tag.hasKey("timestamp") ? tag.getLong("timestamp") : System.currentTimeMillis(),
            tag.getBoolean("completed"));
    }

    public PlannerEntry copy() {
        return new PlannerEntry(slotIndex, text, timestamp, completed);
    }

    @Override
    public String toString() {
        return "PlannerEntry{slot=" + slotIndex + ", text=\"" + text + "\", completed=" + completed + "}";
    }
}
