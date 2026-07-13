package com.imgood.textech.webae.icon;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.items.cell.IDataLoomFluidCell;

import appeng.api.AEApi;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Detects fluid / fluid-cell items and renders registry fluids with in-game tinting
 * (AE2 terminal / AE2FC fluid-drop style), not flat atlas compositing.
 */
@SideOnly(Side.CLIENT)
public final class IconFluidRenderer {

    private static final String AE2FC_FLUID_DROP_ID = "ae2fc:fluid_drop";
    private static final String AE_POST_HOOKS_CLASS = "appeng.client.render.AppEngRenderItem";
    private static final String AE_RENDER_HOOK_CLASS = "appeng.api.storage.IItemDisplayRegistry$ItemRenderHook";

    private static volatile Boolean ae2fcFluidCellPresent;

    private static final String ITEM_FLUID_DROP_CLASS = "com.glodblock.github.common.item.ItemFluidDrop";
    private static volatile Class<?> itemFluidDropClass;

    private IconFluidRenderer() {}

    private static Class<?> fluidDropClass() {
        Class<?> cached = itemFluidDropClass;
        if (cached != null) {
            return cached == Void.class ? null : cached;
        }
        try {
            cached = Class.forName(ITEM_FLUID_DROP_CLASS);
            itemFluidDropClass = cached;
            return cached;
        } catch (Throwable ignored) {
            itemFluidDropClass = Void.class;
            return null;
        }
    }

    /** Item stacks that must not use grid/atlas shortcuts (fluid cells, fluid drops, …). */
    public static boolean needsInGameItemRender(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        if (isFluidDropItem(stack.getItem())) {
            return true;
        }
        if (isFluidStorageCell(stack)) {
            return true;
        }
        if (hasForgeFluidTag(stack)) {
            return true;
        }
        return false;
    }

    public static boolean isFluidDropItem(Item item) {
        if (item == null) {
            return false;
        }
        Class<?> dropCls = fluidDropClass();
        if (dropCls != null && dropCls.isInstance(item)) {
            return true;
        }
        try {
            String registry = Item.itemRegistry.getNameForObject(item);
            return AE2FC_FLUID_DROP_ID.equals(registry);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isFluidDropStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Class<?> dropCls = fluidDropClass();
        if (dropCls == null) {
            return isFluidDropItem(stack.getItem());
        }
        try {
            Object result = dropCls.getMethod("isFluidStack", ItemStack.class)
                .invoke(null, stack);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return isFluidDropItem(stack.getItem());
        }
    }

    public static FluidStack getFluidStack(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        Class<?> dropCls = fluidDropClass();
        if (dropCls != null) {
            try {
                Object result = dropCls.getMethod("getFluidStack", ItemStack.class)
                    .invoke(null, stack);
                if (result instanceof FluidStack) {
                    return (FluidStack) result;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static boolean isFluidStorageCell(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof IDataLoomFluidCell) {
            return true;
        }
        if (isAe2fcFluidCellPresent() && isAe2fcFluidCellItem(item)) {
            return true;
        }
        try {
            IMEInventoryHandler handler = AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(stack, null, StorageChannel.FLUIDS);
            return handler != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isAe2fcFluidCellItem(Item item) {
        if (item instanceof appeng.api.implementations.items.IStorageCell) {
            try {
                return ((appeng.api.implementations.items.IStorageCell) item)
                    .getStackType() == appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean hasForgeFluidTag(ItemStack stack) {
        if (stack == null || stack.getTagCompound() == null) {
            return false;
        }
        try {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(stack.getTagCompound());
            return fluid != null && fluid.getFluid() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * AE2FC {@code ae2fc:fluid_drop} — tinted drop shape matching {@code ItemDropRender} inventory pass.
     */
    public static void drawFluidDropStack(Minecraft mc, ItemStack stack, int x, int y, int size) {
        if (mc == null || stack == null) {
            return;
        }
        IIcon shape = resolveFluidDropShape(stack);
        if (shape == null) {
            return;
        }

        FluidStack fluidStack = getFluidStack(stack);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        mc.getTextureManager()
            .bindTexture(TextureMap.locationItemsTexture);

        if (fluidStack != null && fluidStack.getFluid() != null) {
            int rgb = resolveFluidDropColor(mc, fluidStack);
            GL11.glColor4f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, 1.0F);
        } else {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }

        drawItemIconQuad(shape, x, y, size);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /**
     * Tinted still-fluid quad matching AE2 fluid terminal slots.
     */
    public static void drawTintedFluidIcon(Minecraft mc, Fluid fluid, int x, int y, int size) {
        if (fluid == null || mc == null) {
            return;
        }
        IIcon icon = stillIcon(fluid);
        if (icon == null) {
            return;
        }

        int color = fluid.getColor();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        GL11.glColor4f(r, g, b, 1.0F);

        drawItemIconQuad(icon, x, y, size);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /** AE2 {@code AppEngRenderItem.POST_HOOKS} overlays (cell fill indicators, etc.). */
    public static void invokeAePostRenderHooks(Minecraft mc, ItemStack stack, int x, int y) {
        if (mc == null || stack == null) {
            return;
        }
        try {
            Class<?> renderItemCls = Class.forName(AE_POST_HOOKS_CLASS);
            Field hooksField = renderItemCls.getField("POST_HOOKS");
            Object hooksObj = hooksField.get(null);
            if (!(hooksObj instanceof List)) {
                return;
            }
            List<?> hooks = (List<?>) hooksObj;
            if (hooks.isEmpty()) {
                return;
            }
            Class<?> hookCls = Class.forName(AE_RENDER_HOOK_CLASS);
            Method renderOverlay = hookCls.getMethod(
                "renderOverlay",
                FontRenderer.class,
                TextureManager.class,
                ItemStack.class,
                int.class,
                int.class);
            FontRenderer font = mc.fontRenderer;
            TextureManager tm = mc.getTextureManager();
            for (Object hook : hooks) {
                if (hook == null) {
                    continue;
                }
                renderOverlay.invoke(hook, font, tm, stack, x, y);
            }
        } catch (Throwable ignored) {}
    }

    private static IIcon resolveFluidDropShape(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        try {
            Class<?> dropCls = fluidDropClass();
            if (dropCls != null && dropCls.isInstance(stack.getItem())) {
                Field shapeField = dropCls.getField("shape");
                Object shape = shapeField.get(stack.getItem());
                if (shape instanceof IIcon) {
                    return (IIcon) shape;
                }
            }
        } catch (Throwable ignored) {}
        try {
            IIcon icon = stack.getItem()
                .getIcon(stack, 0);
            if (icon != null) {
                return icon;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int resolveFluidDropColor(Minecraft mc, FluidStack fluidStack) {
        Fluid fluid = fluidStack.getFluid();
        if (fluid == null) {
            return 0xFFFFFF;
        }
        int colour = fluid.getColor(fluidStack);
        if (colour != 0xFFFFFF) {
            return colour;
        }
        return fluid.getColor();
    }

    private static void drawItemIconQuad(IIcon icon, int x, int y, int size) {
        float uMin = icon.getMinU();
        float uMax = icon.getMaxU();
        float vMin = icon.getMinV();
        float vMax = icon.getMaxV();
        Tessellator tess = Tessellator.instance;
        try {
            tess.startDrawingQuads();
            tess.addVertexWithUV(x, y + size, 0.001D, uMin, vMax);
            tess.addVertexWithUV(x + size, y + size, 0.001D, uMax, vMax);
            tess.addVertexWithUV(x + size, y, 0.001D, uMax, vMin);
            tess.addVertexWithUV(x, y, 0.001D, uMin, vMin);
            tess.draw();
        } catch (Throwable t) {
            IconRenderGuard.afterRender(Minecraft.getMinecraft());
        }
    }

    private static IIcon stillIcon(Fluid fluid) {
        try {
            IIcon icon = fluid.getStillIcon();
            if (icon != null) {
                return icon;
            }
        } catch (Throwable ignored) {}
        try {
            return fluid.getIcon();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isAe2fcFluidCellPresent() {
        if (ae2fcFluidCellPresent == null) {
            try {
                Class.forName("appeng.util.item.AEFluidStackType");
                ae2fcFluidCellPresent = Boolean.TRUE;
            } catch (Throwable ignored) {
                ae2fcFluidCellPresent = Boolean.FALSE;
            }
        }
        return ae2fcFluidCellPresent.booleanValue();
    }
}
