package com.imgood.advancedatamonitor.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularui.api.GlStateManager;
import com.imgood.advancedatamonitor.AdvanceDataMonitor;

public class RenderAdvanceNetworkLinkBlockItem implements IItemRenderer {

    private static ResourceLocation advanceDataMonitorModelTexture = new ResourceLocation(
        AdvanceDataMonitor.MODID + ":textures/model/AdvanceDataMonitor.png");
    private static IModelCustom advanceDataMonitorModel = AdvancedModelLoader
        .loadModel(new ResourceLocation(AdvanceDataMonitor.MODID + ":model/AdvanceNetworkLink.obj"));

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        // 指定需要处理的渲染类型：物品栏、手持、掉落物等
        return type == ItemRenderType.INVENTORY || type == ItemRenderType.ENTITY
            || type == ItemRenderType.EQUIPPED_FIRST_PERSON
            || type == ItemRenderType.EQUIPPED;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        // 绑定纹理
        Minecraft.getMinecraft().renderEngine.bindTexture(advanceDataMonitorModelTexture);

        // 根据渲染类型调整变换
        GL11.glPushMatrix();
        if (type == ItemRenderType.ENTITY) {
            GL11.glTranslatef(0F, 0.5F, 0F); // 调整掉落物位置
            renderBaseModel();
        }
        if (type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
            GL11.glRotatef(180, 0, 1, 0);
            GL11.glTranslatef(0.5F, 0.5F, 0.5F);
            GL11.glTranslatef(-1F, -0.2F, -0.5F); // 调整掉落物位置
            renderBaseModel();
        }
        if (type == ItemRenderType.EQUIPPED) {
            GL11.glTranslatef(0F, 0F, 0.5F);
            renderBaseModel();
        }
        if (type == ItemRenderType.INVENTORY) {
            GL11.glTranslatef(0F, -0.5F, 0F);
            GL11.glRotatef(-90, 0, 1, 0);
            renderBaseModel();
        }
        // 渲染模型

        GL11.glPopMatrix();
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return true; // 允许 Forge 的渲染辅助（如旋转）
    }

    private void renderBaseModel() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_LIGHTING);

        // 排除发光部件和RollLighting
        advanceDataMonitorModel.renderAllExcept("Lighting", "CableLighting", "RollLighting", "Roll");

        // 渲染Roll部件并应用旋转

        GL11.glPushMatrix();
        GL11.glTranslated(-0.1, 0, 0.05);
        advanceDataMonitorModel.renderOnly("Roll");
        GL11.glPopMatrix();

        // 发光部件处理
        GL11.glDepthMask(false);
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 渲染原有发光部件
        advanceDataMonitorModel.renderOnly("Lighting", "CableLighting");

        // 渲染RollLighting并应用旋转
        GL11.glPushMatrix();
        GL11.glTranslated(-0.1, 0, 0.05);
        advanceDataMonitorModel.renderOnly("RollLighting");
        GL11.glPopMatrix();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_LIGHTING);
    }
}
