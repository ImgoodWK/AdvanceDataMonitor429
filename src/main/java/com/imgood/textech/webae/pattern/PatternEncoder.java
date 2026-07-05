package com.imgood.textech.webae.pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.google.gson.JsonObject;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.utils.NBTJsonParser;
import com.imgood.textech.webae.dto.PatternDto;
import com.imgood.textech.webae.dto.PatternDto.PatternItemEntry;

import appeng.api.AEApi;

/**
 * Pattern encoder — encodes a PatternDto into an AE2 encoded pattern ItemStack NBT.
 * Emulates ContainerPatternTerm.encode() behavior.
 * This process does NOT involve World and can execute on the HTTP thread.
 */
public class PatternEncoder {

    /**
     * Encode a PatternDto into a JSON string representing the encoded NBTTagCompound.
     * The returned JSON can be passed to POST /api/pattern/inject for injection.
     *
     * @param pattern the pattern data to encode
     * @return JSON string of the full NBTTagCompound
     */
    public static String encode(PatternDto pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null");
        }

        NBTTagCompound root = new NBTTagCompound();

        // Encode inputs (max 27 = 9x3 grid)
        NBTTagList inList = new NBTTagList();
        if (pattern.inputs != null) {
            int maxInputs = Math.min(pattern.inputs.size(), 27);
            for (int i = 0; i < maxInputs; i++) {
                PatternItemEntry entry = pattern.inputs.get(i);
                if (entry != null && !isEmpty(entry)) {
                    ItemStack stack = toItemStack(entry);
                    if (stack != null) {
                        NBTTagCompound stackTag = new NBTTagCompound();
                        stack.writeToNBT(stackTag);
                        inList.appendTag(stackTag);
                    }
                }
            }
        }
        root.setTag("in", inList);

        // Encode outputs
        NBTTagList outList = new NBTTagList();
        if (pattern.outputs != null) {
            for (PatternItemEntry entry : pattern.outputs) {
                if (entry != null && !isEmpty(entry)) {
                    ItemStack stack = toItemStack(entry);
                    if (stack != null) {
                        NBTTagCompound stackTag = new NBTTagCompound();
                        stack.writeToNBT(stackTag);
                        outList.appendTag(stackTag);
                    }
                }
            }
        }
        root.setTag("out", outList);

        // Pattern type flags
        root.setByte("crafting", (byte) (pattern.crafting ? 1 : 0));
        root.setByte("substitute", (byte) (pattern.substitute ? 1 : 0));
        root.setByte("beSubstitute", (byte) (pattern.beSubstitute ? 1 : 0));

        // Author
        if (pattern.author != null && !pattern.author.isEmpty()) {
            root.setString("author", pattern.author);
        }

        // Fluid patterns are handled by including ae2fc:fluid_drop items
        // in the inputs/outputs. These get written as regular ItemStacks and
        // AE2FC detects them by registry name at runtime.

        // Create encoded pattern ItemStack to validate NBT structure
        try {
            ItemStack patternStack = AEApi.instance()
                .definitions()
                .items()
                .encodedPattern()
                .maybeStack(1)
                .get();
            if (patternStack != null) {
                NBTTagCompound validationNbt = (NBTTagCompound) root.copy();
                patternStack.setTagCompound(validationNbt);
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to validate encoded pattern NBT: {}", t.getMessage());
        }

        // Convert to JSON using NBTJsonParser
        try {
            JsonObject jsonObj = NBTJsonParser.parseNBTToJson(root);
            return jsonObj.toString();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to serialize pattern NBT to JSON", e);
            return "{\"error\":\"Failed to encode pattern NBT: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Create an NBTTagCompound from an encoded JSON string for injection.
     *
     * @param encodedNbt JSON string from {@link #encode(PatternDto)}
     * @return the reconstructed NBTTagCompound
     */
    public static NBTTagCompound decode(String encodedNbt) {
        if (encodedNbt == null || encodedNbt.isEmpty()) {
            throw new IllegalArgumentException("encodedNbt must not be null or empty");
        }

        NBTTagCompound root = new NBTTagCompound();

        try {
            JsonObject json = new com.google.gson.Gson().fromJson(encodedNbt, JsonObject.class);
            parseJsonToNbt(json, root);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to decode pattern NBT from JSON", e);
            throw new RuntimeException("Failed to decode pattern NBT: " + e.getMessage(), e);
        }

        return root;
    }

    private static void parseJsonToNbt(JsonObject json, NBTTagCompound target) {
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            com.google.gson.JsonElement value = entry.getValue();

            if (value.isJsonObject()) {
                JsonObject tagObj = value.getAsJsonObject();
                if (tagObj.has("type") && tagObj.has("value")) {
                    String type = tagObj.get("type")
                        .getAsString();
                    com.google.gson.JsonElement val = tagObj.get("value");
                    parseTypedTag(target, key, type, val);
                }
            }
        }
    }

    private static void parseTypedTag(NBTTagCompound target, String key, String type, com.google.gson.JsonElement val) {
        switch (type) {
            case "TAG_Byte":
                target.setByte(key, val.getAsByte());
                break;
            case "TAG_Short":
                target.setShort(key, val.getAsShort());
                break;
            case "TAG_Int":
                target.setInteger(key, val.getAsInt());
                break;
            case "TAG_Long":
                target.setLong(key, val.getAsLong());
                break;
            case "TAG_Float":
                target.setFloat(key, val.getAsFloat());
                break;
            case "TAG_Double":
                target.setDouble(key, val.getAsDouble());
                break;
            case "TAG_String":
                target.setString(key, val.getAsString());
                break;
            case "TAG_Byte_Array": {
                com.google.gson.JsonArray arr = val.getAsJsonArray();
                byte[] bytes = new byte[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    bytes[i] = arr.get(i)
                        .getAsByte();
                }
                target.setByteArray(key, bytes);
                break;
            }
            case "TAG_List": {
                NBTTagList list = new NBTTagList();
                com.google.gson.JsonArray arr = val.getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    com.google.gson.JsonElement elem = arr.get(i);
                    if (elem.isJsonObject()) {
                        JsonObject elemObj = elem.getAsJsonObject();
                        if (elemObj.has("type") && elemObj.has("value")) {
                            String elemType = elemObj.get("type")
                                .getAsString();
                            if ("TAG_Compound".equals(elemType)) {
                                NBTTagCompound subCompound = new NBTTagCompound();
                                if (elemObj.get("value")
                                    .isJsonObject()) {
                                    parseJsonToNbt(
                                        elemObj.get("value")
                                            .getAsJsonObject(),
                                        subCompound);
                                }
                                list.appendTag(subCompound);
                            } else if ("TAG_String".equals(elemType)) {
                                list.appendTag(
                                    new net.minecraft.nbt.NBTTagString(
                                        elemObj.get("value")
                                            .getAsString()));
                            }
                        }
                    }
                }
                target.setTag(key, list);
                break;
            }
            case "TAG_Compound": {
                NBTTagCompound subCompound = new NBTTagCompound();
                if (val.isJsonObject()) {
                    parseJsonToNbt(val.getAsJsonObject(), subCompound);
                }
                target.setTag(key, subCompound);
                break;
            }
            default:
                AdvanceDataMonitor.LOG.warn("[WebAE] Unknown NBT type in pattern decode: {}", type);
                break;
        }
    }

    private static ItemStack toItemStack(PatternItemEntry entry) {
        if (entry == null || entry.registryName == null || entry.registryName.isEmpty()) {
            return null;
        }
        try {
            Object itemObj = net.minecraft.item.Item.itemRegistry.getObject(entry.registryName);
            if (itemObj instanceof net.minecraft.item.Item) {
                net.minecraft.item.Item item = (net.minecraft.item.Item) itemObj;
                return new ItemStack(item, Math.max(1, entry.stackSize), entry.meta);
            }
            String fullName = entry.registryName.contains(":") ? entry.registryName : "minecraft:" + entry.registryName;
            itemObj = net.minecraft.item.Item.itemRegistry.getObject(fullName);
            if (itemObj instanceof net.minecraft.item.Item) {
                net.minecraft.item.Item item = (net.minecraft.item.Item) itemObj;
                return new ItemStack(item, Math.max(1, entry.stackSize), entry.meta);
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to create ItemStack for pattern input: {}", entry.registryName);
        }
        return null;
    }

    private static boolean isEmpty(PatternItemEntry entry) {
        if (entry == null) return true;
        if (entry.registryName == null || entry.registryName.isEmpty()) return true;
        if ("air".equalsIgnoreCase(entry.registryName) || "minecraft:air".equalsIgnoreCase(entry.registryName)) {
            return true;
        }
        return false;
    }
}
