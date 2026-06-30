package com.imgood.textech.utils;

import java.util.ArrayList;
import java.util.List;

public class DataBound {

    private int x;
    private int y;
    private int z;
    private DataType dataType;
    private List<Double> dataValues = new ArrayList<>();
    private List<String> stringValues = new ArrayList<>();
    private double yMin = 0.0;
    private double yMax = 20.0;
    private int dataLimit = 10;
    private int lineColor = 0x00FFFF;
    private float lineWidth = 5f;
    private float scale = 1.0f;
    private float yOffset = 0.0f;
    private float rotationX = 0.0f;
    private float rotationY = 0.0f;
    private float rotationZ = 0.0f;
    private String name;
    private float xOffset;
    private String displayName;

    public String getXYZ() {
        return x + "," + y + "," + z;
    }

    public void setXYZ(String XYZ) {
        String[] xyz = XYZ.split(",");
        try {
            x = Integer.parseInt(xyz[0]);
            y = Integer.parseInt(xyz[1]);
            z = Integer.parseInt(xyz[2]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public enum DataType {
        line,
        crafting,
        storage,
        bar,
        bar3d,
        waterfall,
        diffrence
    }

    public DataBound(int x, int y, int z, DataType dataType) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dataType = dataType;
    }

    public DataBound(String XYZ, DataType dataType) {
        this.setXYZ(XYZ);
        this.dataType = dataType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getStringValues() {
        return stringValues;
    }

    public void setStringValues(List<String> stringValues) {
        this.stringValues = stringValues;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public String getDataTypeName() {
        return dataType.name();
    }

    public List<Double> getDataValues() {
        return dataValues;
    }

    public void setDataValues(List<Double> dataValues) {
        this.dataValues = dataValues;
    }

    public double getyMin() {
        return yMin;
    }

    public void setyMin(double yMin) {
        this.yMin = yMin;
    }

    public double getyMax() {
        return yMax;
    }

    public void setyMax(double yMax) {
        this.yMax = yMax;
    }

    public int getDataLimit() {
        return dataLimit;
    }

    public void setDataLimit(int dataLimit) {
        this.dataLimit = dataLimit;
    }

    public int getLineColor() {
        return lineColor;
    }

    public void setLineColor(int lineColor) {
        this.lineColor = lineColor;
    }

    public float getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(float lineWidth) {
        this.lineWidth = lineWidth;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public float getyOffset() {
        return yOffset;
    }

    public void setyOffset(float yOffset) {
        this.yOffset = yOffset;
    }

    public float getRotationX() {
        return rotationX;
    }

    public void setRotationX(float rotationX) {
        this.rotationX = rotationX;
    }

    public float getRotationY() {
        return rotationY;
    }

    public void setRotationY(float rotationY) {
        this.rotationY = rotationY;
    }

    public float getRotationZ() {
        return rotationZ;
    }

    public void setRotationZ(float rotationZ) {
        this.rotationZ = rotationZ;
    }

    public float getxOffset() {
        return xOffset;
    }

    public void setxOffset(float xOffset) {
        this.xOffset = xOffset;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
