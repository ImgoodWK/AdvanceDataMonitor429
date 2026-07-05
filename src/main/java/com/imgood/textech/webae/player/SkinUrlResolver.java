package com.imgood.textech.webae.player;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;

/**
 * Resolves a player's Mojang skin texture URL from their {@link com.mojang.authlib.GameProfile}
 * textures property.
 *
 * <p>
 * Strategy:
 * </p>
 * <ol>
 * <li>Look up the online player by UUID (offline players cannot be resolved
 * this way because their GameProfile is not retained on the dedicated server
 * after logout; return {@code null}).</li>
 * <li>Read the {@code textures} property from the GameProfile.</li>
 * <li>Base64-decode the property value (which is a JSON payload describing
 * the player's skin/Cape/elytra URLs).</li>
 * <li>Parse the JSON and extract {@code textures.SKIN.url}.</li>
 * </ol>
 *
 * <p>
 * The returned URL is the raw Mojang texture CDN URL (e.g.
 * {@code http://textures.minecraft.net/texture/<hash>}). The web frontend is
 * responsible for fetching it from the browser-side Mojang CDN.
 * </p>
 */
public final class SkinUrlResolver {

    private static final Gson GSON = new GsonBuilder().create();

    private SkinUrlResolver() {}

    /**
     * Resolve the skin URL for an online player by UUID string.
     *
     * @return the skin URL, or {@code null} if the player is offline / has no
     *         textures property / the property is malformed.
     */
    public static String resolveByUuid(String uuidString) {
        if (uuidString == null || uuidString.isEmpty()) return null;
        try {
            EntityPlayerMP player = findOnlinePlayer(uuidString);
            if (player == null) return null;
            return resolveFromGameProfile(player);
        } catch (Throwable t) {
            if (Config.debugWebae) {
                AdvanceDataMonitor.LOG
                    .warn("[WebAE] Failed to resolve skin URL for {}: {}", uuidString, t.getMessage());
            }
            return null;
        }
    }

    /** Resolve the skin URL directly from an online player's GameProfile. */
    public static String resolveFromGameProfile(EntityPlayerMP player) {
        if (player == null) return null;
        try {
            com.mojang.authlib.GameProfile profile = player.getGameProfile();
            if (profile == null) return null;
            com.mojang.authlib.properties.Property textures = findTexturesProperty(profile);
            if (textures == null) return null;
            String value = textures.getValue();
            if (value == null || value.isEmpty()) return null;
            String decoded = decodeBase64(value);
            if (decoded == null) return null;
            return extractSkinUrl(decoded);
        } catch (Throwable t) {
            if (Config.debugWebae) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to resolve skin URL: {}", t.getMessage());
            }
            return null;
        }
    }

    private static com.mojang.authlib.properties.Property findTexturesProperty(com.mojang.authlib.GameProfile profile) {
        if (profile == null) return null;
        try {
            // GameProfile.getProperties() returns a com.mojang.authlib.properties.PropertyMap
            // (Guava ForwardingMultimap<String, Property> in 1.7.10). Use reflection on the
            // Multimap interface so we stay compatible across authlib/Guava versions.
            Object props = profile.getProperties();
            if (props == null) return null;
            // Multimap.get(Object) returns a Collection<V>
            java.lang.reflect.Method getMethod = props.getClass()
                .getMethod("get", Object.class);
            Object coll = getMethod.invoke(props, "textures");
            if (coll instanceof java.util.Collection) {
                for (Object o : (java.util.Collection<?>) coll) {
                    if (o instanceof com.mojang.authlib.properties.Property) {
                        return (com.mojang.authlib.properties.Property) o;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String decodeBase64(String b64) {
        try {
            byte[] decoded = javax.xml.bind.DatatypeConverter.parseBase64Binary(b64);
            if (decoded == null) return null;
            return new String(decoded, "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    private static String extractSkinUrl(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return null;
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures == null) return null;
            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null) return null;
            if (skin.has("url") && !skin.get("url")
                .isJsonNull()) {
                return skin.get("url")
                    .getAsString();
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static EntityPlayerMP findOnlinePlayer(String uuidString) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return null;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP mp = (EntityPlayerMP) obj;
                if (mp.getUniqueID()
                    .toString()
                    .equals(uuidString)) return mp;
            }
        }
        return null;
    }
}
