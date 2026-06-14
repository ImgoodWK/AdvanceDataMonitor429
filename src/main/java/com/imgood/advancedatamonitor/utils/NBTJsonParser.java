package com.imgood.advancedatamonitor.utils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.imgood.advancedatamonitor.AdvanceDataMonitor;

public class NBTJsonParser {

    private static final String[] NBT_TYPES = { "TAG_End", "TAG_Byte", "TAG_Short", "TAG_Int", "TAG_Long", "TAG_Float",
        "TAG_Double", "TAG_Byte_Array", "TAG_String", "TAG_List", "TAG_Compound", "TAG_Int_Array" };

    // 通过反射获取 NBTTagList 的 tagList 字段
    private static final Field TAG_LIST_FIELD;

    static {
        try {
            TAG_LIST_FIELD = NBTTagList.class.getDeclaredField("tagList");
            TAG_LIST_FIELD.setAccessible(true); // 解除私有访问限制
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access NBTTagList.tagList", e);
        }
    }

    private static final boolean DEBUG = AdvanceDataMonitor.DEBUG_MODE;

    private static void debug(String message) {
        if (DEBUG) {
            AdvanceDataMonitor.LOG.debug("[NBTDebug] " + message);
        }
    }

    public static JsonObject parseNBTToJson(NBTTagCompound nbt) {
        debug("开始解析NBT根复合标签");
        JsonObject root = new JsonObject();
        parseCompound(nbt, root);
        debug("完成根复合标签解析");
        return root;
    }

    private static void parseCompound(NBTTagCompound compound, JsonObject json) {
        debug(
            "进入复合标签解析，当前键数量: " + compound.func_150296_c()
                .size());
        for (Object keyObj : compound.func_150296_c()) {
            String key = (String) keyObj;
            NBTBase tag = compound.getTag(key);
            debug("解析键 '" + key + "' 类型: " + getTypeName(tag));
            json.add(key, parseTag(tag));
        }
    }

    private static JsonElement parseTag(NBTBase tag) {
        debug("解析单个标签开始，类型ID: " + tag.getId() + " (" + getTypeName(tag) + ")");
        JsonObject entry = new JsonObject();

        // 添加类型安全检查
        String typeName;
        try {
            typeName = NBT_TYPES[tag.getId()];
        } catch (ArrayIndexOutOfBoundsException e) {
            typeName = "UNKNOWN";
            debug("遇到未知类型ID: " + tag.getId());
        }
        entry.addProperty("type", typeName);

        switch (tag.getId()) {
            case 1: // Byte
                byte byteVal = ((NBTTagByte) tag).func_150290_f();
                debug("Byte 值: " + byteVal);
                entry.addProperty("value", byteVal);
                break;
            case 2: // Short
                entry.addProperty("value", ((NBTTagShort) tag).func_150289_e());
                break;
            case 3: // Int
                entry.addProperty("value", ((NBTTagInt) tag).func_150287_d());
                break;
            case 4: // Long
                entry.addProperty("value", ((NBTTagLong) tag).func_150291_c());
                break;
            case 5: // Float
                entry.addProperty("value", ((NBTTagFloat) tag).func_150288_h());
                break;
            case 6: // Double
                entry.addProperty("value", ((NBTTagDouble) tag).func_150286_g());
                break;

            case 7: // ByteArray
                byte[] bytes = ((NBTTagByteArray) tag).func_150292_c();
                debug("字节数组长度: " + bytes.length + " 内容: " + Arrays.toString(bytes));
                JsonArray byteArray = new JsonArray();
                for (byte b : bytes) {
                    byteArray.add(new JsonPrimitive(b));
                }
                entry.add("value", byteArray);
                break;
            case 8: // String
                entry.addProperty("value", ((NBTTagString) tag).func_150285_a_());
                break;
            case 9: // List
                debug("开始解析列表标签");
                entry.add("value", parseList((NBTTagList) tag));
                debug("完成列表标签解析");
                break;

            case 10: // Compound
                debug("进入嵌套复合标签解析");
                JsonObject compoundJson = new JsonObject();
                parseCompound((NBTTagCompound) tag, compoundJson);
                entry.add("value", compoundJson);
                debug("退出嵌套复合标签");
                break;

            default:
                debug("遇到未处理类型: ID=" + tag.getId());
                entry.addProperty("value", "[Unsupported Type]");
        }
        return entry;
    }

    private static JsonArray parseList(NBTTagList list) {
        try {
            int listTypeId = list.func_150303_d(); // 获取列表元素类型ID
            debug("\n--- 开始解析列表 ---");
            debug("ListTag" + list);
            debug("列表元类型: " + getTypeNameById(listTypeId) + " 元素数量: " + list.tagCount());

            List<NBTBase> tagList = (List<NBTBase>) TAG_LIST_FIELD.get(list);
            JsonArray array = new JsonArray();

            debug("实际存储的列表元素数量: " + tagList.size());
            for (int i = 0; i < tagList.size(); i++) {
                NBTBase element = tagList.get(i);
                debug("处理元素 #" + i + " 类型: " + getTypeName(element) + " 内容: " + element.toString());
                array.add(parseTag(element));
            }
            debug("--- 结束列表解析 ---\n");
            return array;
        } catch (IllegalAccessException e) {
            debug("列表解析失败！错误信息: " + e.getMessage());
            e.printStackTrace(); // 打印完整堆栈
            throw new RuntimeException("无法读取NBTTagList", e);
        }
    }

    // 辅助方法：通过ID获取类型名称
    private static String getTypeName(NBTBase tag) {
        return getTypeNameById(tag.getId());
    }

    private static String getTypeNameById(int id) {
        try {
            return NBT_TYPES[id];
        } catch (ArrayIndexOutOfBoundsException e) {
            return "UNKNOWN_ID_" + id;
        }
    }
}
