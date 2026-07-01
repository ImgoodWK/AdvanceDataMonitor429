package com.imgood.textech.loader;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.items.ItemAdvanceLinkScanner;
import com.imgood.textech.items.ItemAdvancePlanner;
import com.imgood.textech.items.ItemAdvanceStorageLinkCell;
import com.imgood.textech.items.ItemDataImprint;
import com.imgood.textech.items.ItemDimensionalPocket;
import com.imgood.textech.items.ItemGrappleHook;
import com.imgood.textech.items.ItemInfiniteStackUpgradeCard;
import com.imgood.textech.items.ItemManual;
import com.imgood.textech.items.ItemPageUpgradeCard;
import com.imgood.textech.items.ItemSpaceUpgradeCard;
import com.imgood.textech.items.ItemStackUpgradeCard;
import com.imgood.textech.items.ItemHolyJudgment;
import com.imgood.textech.items.ItemStarryCosmosSword;
import com.imgood.textech.items.ItemSuperOrange;
import com.imgood.textech.items.cell.ItemDataDustLoomCell;
import com.imgood.textech.items.cell.ItemDataFlowCell;
import com.imgood.textech.items.cell.ItemDataFormLoomCell;
import com.imgood.textech.items.cell.ItemDataSourceLoomCell;
import com.imgood.textech.items.cell.ItemDataTideLoomCell;
import com.imgood.textech.items.cell.ItemSuperWeaveAmplifier;
import com.imgood.textech.items.cell.ItemWeaveAmplifier;

import appeng.api.config.Upgrades;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-08 11:12
 **/
public class LoaderItem {

    public static Item dataImprint;
    public static Item advanceStorageLinkCell;
    public static Item advancePlanner;
    public static Item advanceLinkScanner;
    public static Item orange;
    public static Item manual;
    public static Item dataDustLoomCell;
    public static Item dataFormLoomCell;
    public static Item dataFlowCell;
    public static Item dataTideLoomCell;
    public static Item dataSourceLoomCell;
    public static Item weaveAmplifier;
    public static Item superWeaveAmplifier;
    public static Item grappleHook;
    public static Item starryCosmosSword;
    public static Item holyJudgment;
    public static Item dimensionalPocket;
    public static Item spaceUpgradeCard;
    public static Item pageUpgradeCard;
    public static Item stackUpgradeCard;
    public static Item infiniteStackUpgradeCard;

    public static void registerItems() {
        dataImprint = new ItemDataImprint().setUnlocalizedName("dataImprint")
            .setTextureName(AdvanceDataMonitor.MODID + ":data_weave");
        advanceStorageLinkCell = new ItemAdvanceStorageLinkCell().setUnlocalizedName("advanceStorageLinkCell")
            .setTextureName(AdvanceDataMonitor.MODID + ":advance_storage_link_cell");
        advancePlanner = new ItemAdvancePlanner().setUnlocalizedName("advancePlanner")
            .setTextureName(AdvanceDataMonitor.MODID + ":advance_planner");
        advanceLinkScanner = new ItemAdvanceLinkScanner();
        orange = new ItemSuperOrange().setUnlocalizedName("orange")
            .setTextureName(AdvanceDataMonitor.MODID + ":orange");
        manual = new ItemManual().setUnlocalizedName("manual")
            .setTextureName(AdvanceDataMonitor.MODID + ":manual");
        dataDustLoomCell = new ItemDataDustLoomCell().setUnlocalizedName("dataDustLoomCell")
            .setTextureName(AdvanceDataMonitor.MODID + ":data_dust_loom_cell");
        dataFormLoomCell = new ItemDataFormLoomCell().setUnlocalizedName("dataFormLoomCell")
            .setTextureName(AdvanceDataMonitor.MODID + ":data_form_loom_cell");
        dataFlowCell = new ItemDataFlowCell().setUnlocalizedName("dataFlowCell")
            .setTextureName(AdvanceDataMonitor.MODID + ":data_flow_cell");
        dataTideLoomCell = new ItemDataTideLoomCell().setUnlocalizedName("dataTideLoomCell")
            .setTextureName(AdvanceDataMonitor.MODID + ":data_tide_loom_cell");
        dataSourceLoomCell = new ItemDataSourceLoomCell().setUnlocalizedName("dataSourceLoomCell")
            .setTextureName(AdvanceDataMonitor.MODID + ":data_source_loom_cell");
        weaveAmplifier = new ItemWeaveAmplifier().setUnlocalizedName("weaveAmplifier")
            .setTextureName(AdvanceDataMonitor.MODID + ":weave_amplifier");
        superWeaveAmplifier = new ItemSuperWeaveAmplifier().setUnlocalizedName("superWeaveAmplifier")
            .setTextureName(AdvanceDataMonitor.MODID + ":super_weave_amplifier");
        starryCosmosSword = new ItemStarryCosmosSword();
        holyJudgment = new ItemHolyJudgment();
        grappleHook = new ItemGrappleHook();
        dimensionalPocket = new ItemDimensionalPocket().setUnlocalizedName("dimensionalPocket")
            .setTextureName(AdvanceDataMonitor.MODID + ":dimensional_pocket");
        spaceUpgradeCard = new ItemSpaceUpgradeCard().setUnlocalizedName("spaceUpgradeCard")
            .setTextureName(AdvanceDataMonitor.MODID + ":space_upgrade_card");
        pageUpgradeCard = new ItemPageUpgradeCard().setUnlocalizedName("pageUpgradeCard")
            .setTextureName(AdvanceDataMonitor.MODID + ":page_upgrade_card");
        stackUpgradeCard = new ItemStackUpgradeCard().setUnlocalizedName("stackUpgradeCard")
            .setTextureName(AdvanceDataMonitor.MODID + ":stack_upgrade_card");
        infiniteStackUpgradeCard = new ItemInfiniteStackUpgradeCard().setUnlocalizedName("infiniteStackUpgradeCard")
            .setTextureName(AdvanceDataMonitor.MODID + ":infinite_stack_upgrade_card");

        GameRegistry.registerItem(dataImprint, "data_imprint");
        GameRegistry.registerItem(advanceStorageLinkCell, "advance_storage_link_cell");
        GameRegistry.registerItem(advancePlanner, "advance_planner");
        GameRegistry.registerItem(advanceLinkScanner, "advance_link_scanner");
        GameRegistry.registerItem(orange, "orange");
        GameRegistry.registerItem(manual, "manual");
        GameRegistry.registerItem(dataDustLoomCell, "data_dust_loom_cell");
        GameRegistry.registerItem(dataFormLoomCell, "data_form_loom_cell");
        GameRegistry.registerItem(dataFlowCell, "data_flow_cell");
        GameRegistry.registerItem(dataTideLoomCell, "data_tide_loom_cell");
        GameRegistry.registerItem(dataSourceLoomCell, "data_source_loom_cell");
        GameRegistry.registerItem(weaveAmplifier, "weave_amplifier");
        GameRegistry.registerItem(superWeaveAmplifier, "super_weave_amplifier");
        GameRegistry.registerItem(starryCosmosSword, "starry_cosmos_sword");
        GameRegistry.registerItem(holyJudgment, "holy_judgment");
        GameRegistry.registerItem(grappleHook, "grapple_hook");
        GameRegistry.registerItem(dimensionalPocket, "dimensional_pocket");
        GameRegistry.registerItem(spaceUpgradeCard, "space_upgrade_card");
        GameRegistry.registerItem(pageUpgradeCard, "page_upgrade_card");
        GameRegistry.registerItem(stackUpgradeCard, "stack_upgrade_card");
        GameRegistry.registerItem(infiniteStackUpgradeCard, "infinite_stack_upgrade_card");

        Upgrades.FUZZY.registerItem(new ItemStack(advanceStorageLinkCell), 1);
        Upgrades.INVERTER.registerItem(new ItemStack(advanceStorageLinkCell), 1);

        Upgrades.SPEED.registerItem(new ItemStack(weaveAmplifier), 1);
        Upgrades.SUPERSPEED.registerItem(new ItemStack(superWeaveAmplifier), 1);

        ItemStack decompressor = new ItemStack(Item.getItemFromBlock(LoaderBlock.matterBallDecompressor));
        Upgrades.SPEED.registerItem(decompressor, 4);
        Upgrades.SUPERSPEED.registerItem(decompressor, 4);
    }
}
