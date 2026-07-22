package com.imgood.textech.cardbattle.data;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.cardbattle.CardBattleTypes.CardDef;

public final class CardCatalog {

    private static final Gson GSON = new Gson();
    private static List<CardDef> ALL = Collections.emptyList();
    private static Map<String, CardDef> BY_ID = new HashMap<String, CardDef>();

    private CardCatalog() {}

    public static synchronized void ensureLoaded() {
        if (!ALL.isEmpty()) return;
        InputStream in = CardCatalog.class.getResourceAsStream("/assets/textech/cardbattle/cards.json");
        if (in == null) {
            logError("[CardBattle] Missing cards.json in jar resources", null);
            ALL = new ArrayList<CardDef>();
            return;
        }
        try {
            List<CardDef> list = GSON.fromJson(
                new InputStreamReader(in, Charset.forName("UTF-8")),
                new TypeToken<List<CardDef>>() {}.getType());
            if (list == null) list = new ArrayList<CardDef>();
            Map<String, CardDef> map = new HashMap<String, CardDef>();
            for (CardDef c : list) {
                if (c != null && c.id != null) map.put(c.id, c);
            }
            ALL = list;
            BY_ID = map;
            logInfo("[CardBattle] Loaded {} cards", Integer.valueOf(ALL.size()));
        } catch (Throwable t) {
            logError("[CardBattle] Failed loading cards.json", t);
            ALL = new ArrayList<CardDef>();
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {}
        }
    }

    /** Forge logging is unavailable in the plain JVM used by card-engine tests. */
    private static void logInfo(String message, Object value) {
        try {
            AdvanceDataMonitor.LOG.info(message, value);
        } catch (Throwable ignored) {}
    }

    private static void logError(String message, Throwable error) {
        try {
            if (error == null) AdvanceDataMonitor.LOG.error(message);
            else AdvanceDataMonitor.LOG.error(message, error);
        } catch (Throwable ignored) {}
    }

    public static CardDef get(String id) {
        ensureLoaded();
        return BY_ID.get(id);
    }

    public static List<CardDef> all() {
        ensureLoaded();
        return ALL;
    }

    public static List<CardDef> byTheme(String theme) {
        ensureLoaded();
        List<CardDef> out = new ArrayList<CardDef>();
        for (CardDef c : ALL) {
            if (theme != null && theme.equals(c.theme)) out.add(c);
        }
        return out;
    }

    public static List<String> aeOffDeckPool(List<String> deckIds) {
        ensureLoaded();
        java.util.HashSet<String> inDeck = new java.util.HashSet<String>(deckIds);
        List<String> out = new ArrayList<String>();
        for (CardDef c : ALL) {
            if (c.theme != null && !"ae".equals(c.theme) && !inDeck.contains(c.id)) {
                out.add(c.id);
            }
        }
        return out;
    }
}
