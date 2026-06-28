package com.imgood.advancedatamonitor.loader;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.imgood.advancedatamonitor.items.ItemAdvanceLinkScanner;
import com.imgood.advancedatamonitor.items.ItemAdvancePlanner;
import com.imgood.advancedatamonitor.items.ItemAdvanceStorageLinkCell;
import com.imgood.advancedatamonitor.items.ItemDataImprint;
import com.imgood.advancedatamonitor.items.ItemDimensionalPocket;
import com.imgood.advancedatamonitor.items.ItemGrappleHook;
import com.imgood.advancedatamonitor.items.ItemInfiniteStackUpgradeCard;
import com.imgood.advancedatamonitor.items.ItemManual;
import com.imgood.advancedatamonitor.items.ItemPageUpgradeCard;
import com.imgood.advancedatamonitor.items.ItemSpaceUpgradeCard;
import com.imgood.advancedatamonitor.items.ItemStackUpgradeCard;
import com.imgood.advancedatamonitor.items.ItemStarryCosmosSword;
import com.imgood.advancedatamonitor.items.ItemSuperOrange;
import com.imgood.advancedatamonitor.items.cell.ItemDataDustLoomCell;
import com.imgood.advancedatamonitor.items.cell.ItemDataFlowCell;
import com.imgood.advancedatamonitor.items.cell.ItemDataFormLoomCell;
import com.imgood.advancedatamonitor.items.cell.ItemDataSourceLoomCell;
import com.imgood.advancedatamonitor.items.cell.ItemDataTideLoomCell;
import com.imgood.advancedatamonitor.items.cell.ItemSuperWeaveAmplifier;
import com.imgood.advancedatamonitor.items.cell.ItemWeaveAmplifier;

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
    public static Item dimensionalPocket;
    public static Item spaceUpgradeCard;
    public static Item pageUpgradeCard;
    public static Item stackUpgradeCard;
    public static Item infiniteStackUpgradeCard;

    public static void registerItems() {
        dataImprint = new ItemDataImprint().setUnlocalizedName("dataImprint")
            .setTextureName("advancedatamonitor:data_weave");
        advanceStorageLinkCell = new ItemAdvanceStorageLinkCell().setUnlocalizedName("advanceStorageLinkCell")
            .setTextureName("advancedatamonitor:advance_storage_link_cell");
        advancePlanner = new ItemAdvancePlanner().setUnlocalizedName("advancePlanner")
            .setTextureName("advancedatamonitor:advance_planner");
        advanceLinkScanner = new ItemAdvanceLinkScanner();
        orange = new ItemSuperOrange().setUnlocalizedName("orange")
            .setTextureName("advancedatamonitor:orange");
        manual = new ItemManual().setUnlocalizedName("manual")
            .setTextureName("advancedatamonitor:manual");
        dataDustLoomCell = new ItemDataDustLoomCell().setUnlocalizedName("dataDustLoomCell")
            .setTextureName("advancedatamonitor:data_dust_loom_cell");
        dataFormLoomCell = new ItemDataFormLoomCell().setUnlocalizedName("dataFormLoomCell")
            .setTextureName("advancedatamonitor:data_form_loom_cell");
        dataFlowCell = new ItemDataFlowCell().setUnlocalizedName("dataFlowCell")
            .setTextureName("advancedatamonitor:data_flow_cell");
        dataTideLoomCell = new ItemDataTideLoomCell().setUnlocalizedName("dataTideLoomCell")
            .setTextureName("advancedatamonitor:data_tide_loom_cell");
        dataSourceLoomCell = new ItemDataSourceLoomCell().setUnlocalizedName("dataSourceLoomCell")
            .setTextureName("advancedatamonitor:data_source_loom_cell");
        weaveAmplifier = new ItemWeaveAmplifier().setUnlocalizedName("weaveAmplifier")
            .setTextureName("advancedatamonitor:weave_amplifier");
        superWeaveAmplifier = new ItemSuperWeaveAmplifier().setUnlocalizedName("superWeaveAmplifier")
            .setTextureName("advancedatamonitor:super_weave_amplifier");
        starryCosmosSword = new ItemStarryCosmosSword();
        grappleHook = new ItemGrappleHook();
        dimensionalPocket = new ItemDimensionalPocket().setUnlocalizedName("dimensionalPocket")
            .setTextureName("advancedatamonitor:dimensional_pocket");
        spaceUpgradeCard = new ItemSpaceUpgradeCard().setUnlocalizedName("spaceUpgradeCard")
            .setTextureName("advancedatamonitor:space_upgrade_card");
        pageUpgradeCard = new ItemPageUpgradeCard().setUnlocalizedName("pageUpgradeCard")
            .setTextureName("advancedatamonitor:page_upgrade_card");
        stackUpgradeCard = new ItemStackUpgradeCard().setUnlocalizedName("stackUpgradeCard")
            .setTextureName("advancedatamonitor:stack_upgrade_card");
        infiniteStackUpgradeCard = new ItemInfiniteStackUpgradeCard().setUnlocalizedName("infiniteStackUpgradeCard")
            .setTextureName("advancedatamonitor:infinite_stack_upgrade_card");

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
    }
}
