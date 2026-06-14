package com.imgood.advancedatamonitor;

import net.minecraftforge.client.ClientCommandHandler;

import com.imgood.advancedatamonitor.client.KeyBindings;
import com.imgood.advancedatamonitor.client.VoiceAssistantKeyHandler;
import com.imgood.advancedatamonitor.command.CommandAIConfig;
import com.imgood.advancedatamonitor.command.CommandAssistant;
import com.imgood.advancedatamonitor.renders.OrangeNameplateRenderer;
import com.imgood.advancedatamonitor.renders.PlannerHudRenderer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    private final VoiceAssistantKeyHandler voiceAssistantKeyHandler = new VoiceAssistantKeyHandler();
    private final KeyBindings keyBindings = new KeyBindings();

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientCommandHandler.instance.registerCommand(new CommandAIConfig());
        ClientCommandHandler.instance.registerCommand(new CommandAssistant());
        this.voiceAssistantKeyHandler.register();
        this.keyBindings.register();
        FMLCommonHandler.instance()
            .bus()
            .register(this.voiceAssistantKeyHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(this.keyBindings);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new PlannerHudRenderer());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new OrangeNameplateRenderer());
    }
}
