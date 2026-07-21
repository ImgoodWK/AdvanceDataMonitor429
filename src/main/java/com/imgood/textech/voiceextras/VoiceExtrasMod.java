package com.imgood.textech.voiceextras;

import cpw.mods.fml.common.Mod;

/**
 * Marker mod that ships the optional Vosk offline speech model assets.
 * Players place the {@code *-voice.jar} next to TeXTech in {@code mods/}.
 * The class is compiled with the main sources but excluded from the published
 * TeXTech jar; only the voice classifier artifact includes it.
 * Development keeps Vosk assets under {@code src/main/resources}, so
 * {@code runClient} does not need the companion jar.
 */
@Mod(
    modid = VoiceExtrasMod.MODID,
    name = "TeXTech Voice Extras",
    version = VoiceExtrasMod.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:textech")
public class VoiceExtrasMod {

    public static final String MODID = "textechvoice";
    /** Filled at jar build time via resource filtering when available; fallback for IDE. */
    public static final String VERSION = "1.0";
}
