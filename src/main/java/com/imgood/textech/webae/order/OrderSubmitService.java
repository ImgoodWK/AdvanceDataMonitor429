package com.imgood.textech.webae.order;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.imgood.textech.assistant.CraftingCandidate;
import com.imgood.textech.webae.alerts.WebAlertsConfig;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.craft.WebAeCraftService;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.dto.StorageDto.CpuEntry;
import com.imgood.textech.webae.pattern.InterfaceLocator;

/**
 * Server-thread order submission for WebAE automation (Phase 3.3).
 * Extracted from {@link com.imgood.textech.webae.api.handler.OrderHandler} to avoid HTTP coupling.
 */
public final class OrderSubmitService {

    private OrderSubmitService() {}

    /**
     * @return {@code true} when no CPU is busy (or no CPUs registered).
     */
    public static boolean isCpuIdle(StorageDto storage) {
        if (storage == null || storage.cpus == null || storage.cpus.isEmpty()) {
            return true;
        }
        for (CpuEntry cpu : storage.cpus) {
            if (cpu != null && cpu.isBusy) {
                return false;
            }
        }
        return true;
    }

    /**
     * Submit a craft order for an automation rule. Must run on the server main thread.
     *
     * @return result message; empty or null on hard failure
     */
    public static AutomationSubmitResult submitAutomationCraft(String ownerUuid, WebAlertsConfig.AutomationRule rule,
        long currentAmount) {
        AutomationSubmitResult result = new AutomationSubmitResult();
        if (rule == null || !rule.enabled || !"craft_when_below".equals(rule.type)) {
            result.success = false;
            result.message = "Rule disabled or unsupported type";
            return result;
        }
        if (rule.itemId == null || rule.itemId.trim()
            .isEmpty()) {
            result.success = false;
            result.message = "Missing itemId";
            return result;
        }
        int networkId = rule.networkId >= 0 ? rule.networkId : 0;
        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        if (player == null) {
            result.success = false;
            result.message = "Owner context unavailable";
            return result;
        }

        long craftAmount = rule.craftAmount > 0 ? rule.craftAmount : Math.max(rule.threshold - currentAmount, 64L);
        if (craftAmount <= 0) {
            craftAmount = 1;
        }

        CraftingCandidate candidate = null;
        if (rule.patternId != null && !rule.patternId.trim()
            .isEmpty()) {
            candidate = resolvePatternCandidate(rule.patternId.trim(), (int) Math.min(craftAmount, Integer.MAX_VALUE));
        }
        if (candidate == null) {
            List<CraftingCandidate> candidates = WebAeCraftService
                .craftingCandidates(ownerUuid, networkId, rule.itemId, rule.itemId, craftAmount);
            if (candidates != null && !candidates.isEmpty()) {
                candidate = candidates.get(0);
            }
        }
        if (candidate == null) {
            result.success = false;
            result.message = "No craft candidate for " + rule.itemId;
            return result;
        }

        String cpuName = normalizeCpuName(rule.cpuName);
        String message = WebAeCraftService
            .submitCraft(ownerUuid, networkId, candidate, craftAmount, rule.itemId, "en_US", cpuName);

        result.success = message != null && !message.isEmpty();
        result.message = message != null ? message : "Submit returned empty";
        result.craftAmount = craftAmount;
        result.jobId = UUID.randomUUID()
            .toString()
            .substring(0, 8);
        return result;
    }

    private static String normalizeCpuName(String cpuName) {
        if (cpuName == null) {
            return null;
        }
        String trimmed = cpuName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static CraftingCandidate resolvePatternCandidate(String patternId, int amount) {
        if (patternId == null || patternId.isEmpty()) {
            return null;
        }
        int hashIdx = patternId.indexOf('#');
        if (hashIdx < 0) {
            return null;
        }
        String coords = patternId.substring(0, hashIdx);
        String slotStr = patternId.substring(hashIdx + 1);
        String[] parts = coords.split(":");
        if (parts.length != 4) {
            return null;
        }
        int x;
        int y;
        int z;
        int dim;
        int slot;
        try {
            x = Integer.parseInt(parts[0]);
            y = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
            dim = Integer.parseInt(parts[3]);
            slot = Integer.parseInt(slotStr);
        } catch (NumberFormatException e) {
            return null;
        }
        World world = DimensionManager.getWorld(dim);
        if (world == null || !world.blockExists(x, y, z)) {
            return null;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null || !InterfaceLocator.isInterface(te)) {
            return null;
        }
        IInventory patterns = InterfaceLocator.getPatterns(te);
        if (patterns == null) {
            return null;
        }
        ItemStack patternStack = patterns.getStackInSlot(slot);
        if (patternStack == null || patternStack.getItem() == null) {
            return null;
        }
        NBTTagCompound nbt = patternStack.getTagCompound();
        if (nbt == null) {
            return null;
        }
        NBTTagList outList = nbt.getTagList("out", 10);
        if (outList == null || outList.tagCount() == 0) {
            return null;
        }
        for (int i = 0; i < outList.tagCount(); i++) {
            NBTTagCompound stackTag = outList.getCompoundTagAt(i);
            ItemStack output = ItemStack.loadItemStackFromNBT(stackTag);
            if (output == null || output.getItem() == null) {
                continue;
            }
            return new CraftingCandidate(0, output, amount);
        }
        return null;
    }

    public static final class AutomationSubmitResult {

        public boolean success;
        public String message = "";
        public String jobId = "";
        public long craftAmount;
    }
}
