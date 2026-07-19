package com.imgood.textech.webae.assistant;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.TeXTechDataDir;
import com.imgood.textech.assistant.ai.AiProviderProfiles;
import com.imgood.textech.assistant.ai.AiProviderProfiles.ProviderProfile;
import com.imgood.textech.assistant.ai.WebSearchService;
import com.imgood.textech.assistant.ai.WebSearchService.SearchRuntime;

/**
 * Server-local WebAE AI configuration: ordered shared LLM profiles plus shared
 * web-search settings. API keys are encrypted before persistence and never
 * exposed by public views.
 */
public final class WebAiConfigStore {

    public static final String PROTOCOL_OPENAI = "openai-compatible";
    public static final String PROTOCOL_ANTHROPIC = "anthropic";
    public static final String PROTOCOL_GEMINI = "gemini";
    public static final String SOURCE_SERVER = "server";
    public static final String SOURCE_BROWSER = "browser";

    private static final String SETTINGS_FILE = "web-ai-settings.json";
    private static final String MASTER_KEY_FILE = "web-ai-master.key";
    private static final String CIPHER_PREFIX = "aes-gcm-v1:";
    private static final int MASTER_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_KEY_LENGTH = 8192;
    private static final int MAX_PROFILES = 32;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final WebAiConfigStore INSTANCE = new WebAiConfigStore();

    private StoredConfig stored;

    private WebAiConfigStore() {}

    public static WebAiConfigStore instance() {
        return INSTANCE;
    }

    public static boolean isServerKeyEnabled() {
        return Config.webAiServerKeyEnabled;
    }

    public static boolean isBrowserKeyEnabled() {
        return Config.webAiBrowserKeyEnabled;
    }

    /** @deprecated use {@link #isBrowserKeyEnabled()} */
    @Deprecated
    public static boolean isBrowserKeyMode() {
        return isBrowserKeyEnabled() && !isServerKeyEnabled();
    }

    public static String normalizeAiSource(String requested) {
        String value = safe(requested).toLowerCase();
        if (SOURCE_BROWSER.equals(value)) {
            if (!isBrowserKeyEnabled()) {
                throw new IllegalStateException("Per-browser AI keys are disabled in server config.");
            }
            return SOURCE_BROWSER;
        }
        if (SOURCE_SERVER.equals(value) || value.isEmpty()) {
            if (!isServerKeyEnabled()) {
                if (isBrowserKeyEnabled()) return SOURCE_BROWSER;
                throw new IllegalStateException("Server-side AI keys are disabled in server config.");
            }
            return SOURCE_SERVER;
        }
        throw new IllegalArgumentException("Unknown AI source: " + requested);
    }

    public synchronized ConfigView view() {
        StoredConfig config = current();
        ConfigView view = new ConfigView();
        view.secretStorage = "server-local-aes-gcm";
        view.updatedAt = config.updatedAt;
        view.updatedBy = config.updatedBy;
        view.providers = providerViews();
        view.profiles = profileViews(config, true);
        view.search = searchView(config.search, true);
        view.configured = hasConfiguredEnabledProfile(config);
        view.enabled = view.configured;
        fillCompatFields(view, config);
        return view;
    }

    /** Secret-free shared status for any authenticated WebAE client. */
    public synchronized PublicSharedView publicSharedView() {
        StoredConfig config = current();
        PublicSharedView view = new PublicSharedView();
        view.serverKeyEnabled = isServerKeyEnabled();
        view.browserKeyEnabled = isBrowserKeyEnabled();
        view.configured = isServerKeyEnabled() && hasConfiguredEnabledProfile(config);
        view.enabledCount = 0;
        view.profiles = new ArrayList<PublicProfileView>();
        List<StoredProfile> ordered = orderedProfiles(config);
        for (StoredProfile profile : ordered) {
            PublicProfileView item = new PublicProfileView();
            item.id = profile.id;
            item.name = profile.name;
            item.enabled = profile.enabled;
            item.order = profile.order;
            item.providerId = profile.providerId;
            item.model = profile.model;
            item.hasApiKey = profile.encryptedApiKey != null && !profile.encryptedApiKey.isEmpty();
            item.apiKeyHint = item.hasApiKey ? profile.apiKeyHint : "";
            item.configured = profile.enabled && item.hasApiKey && !safe(profile.baseUrl).isEmpty()
                && !safe(profile.model).isEmpty();
            if (item.configured) view.enabledCount++;
            view.profiles.add(item);
        }
        view.search = searchView(config.search, false);
        return view;
    }

    public synchronized List<RuntimeConfig> runtimes() {
        if (!isServerKeyEnabled()) return Collections.emptyList();
        StoredConfig config = current();
        List<RuntimeConfig> result = new ArrayList<RuntimeConfig>();
        for (StoredProfile profile : orderedProfiles(config)) {
            if (!profile.enabled) continue;
            String key = decryptApiKey(profile.encryptedApiKey);
            if (key.isEmpty() || safe(profile.baseUrl).isEmpty() || safe(profile.model).isEmpty()) continue;
            RuntimeConfig runtime = new RuntimeConfig();
            runtime.id = profile.id;
            runtime.name = profile.name;
            runtime.providerId = profile.providerId;
            runtime.protocol = protocolFor(profile.providerId);
            runtime.baseUrl = profile.baseUrl;
            runtime.model = profile.model;
            runtime.apiKey = key;
            runtime.timeoutSeconds = profile.timeoutSeconds;
            runtime.temperature = profile.temperature;
            runtime.maxTokens = profile.maxTokens;
            result.add(runtime);
        }
        return result;
    }

    /** First configured runtime, or null. Kept for single-profile callers. */
    public synchronized RuntimeConfig runtime() {
        List<RuntimeConfig> runtimes = runtimes();
        return runtimes.isEmpty() ? null : runtimes.get(0);
    }

    public synchronized RuntimeConfig runtimeById(String profileId) {
        String id = safe(profileId);
        for (RuntimeConfig runtime : runtimes()) {
            if (id.equals(runtime.id)) return runtime;
        }
        return null;
    }

    public synchronized SearchRuntime searchRuntime() {
        StoredConfig config = current();
        StoredSearch search = config.search == null ? defaultSearch() : config.search;
        SearchRuntime runtime = new SearchRuntime();
        runtime.mode = WebSearchService.normalizeProvider(search.mode);
        runtime.apiKey = decryptApiKey(search.encryptedApiKey);
        runtime.baseUrl = safe(search.baseUrl);
        runtime.maxResults = clamp(search.maxResults, 1, 10);
        runtime.fallback = search.fallback;
        return runtime;
    }

    public synchronized boolean isSearchEnabled() {
        StoredConfig config = current();
        return config.search != null && config.search.enabled;
    }

    public static List<ProviderView> publicProviderViews() {
        return providerViews();
    }

    public synchronized ConfigView update(UpdateRequest update, String actorName) {
        if (update == null) throw new IllegalArgumentException("Missing AI settings body.");
        if (!isServerKeyEnabled()) {
            throw new IllegalStateException("Server-side AI key management is disabled.");
        }
        StoredConfig config = copy(current());
        if (update.profiles != null) {
            config.profiles = applyProfileUpdates(config, update.profiles);
        } else if (isLegacySingleUpdate(update)) {
            config.profiles = applyLegacySingleUpdate(config, update);
        }
        if (update.search != null) {
            config.search = applySearchUpdate(config.search, update.search);
        }
        config.updatedAt = System.currentTimeMillis();
        config.updatedBy = truncate(safe(actorName), 64);
        config.version = 2;
        normalize(config);
        validateStored(config);
        stored = config;
        save(config);
        return view();
    }

    public synchronized ConfigView clearApiKey(String actorName) {
        UpdateRequest request = new UpdateRequest();
        request.clearApiKey = Boolean.TRUE;
        return update(request, actorName);
    }

    public synchronized ConfigView clearProfileApiKey(String profileId, String actorName) {
        UpdateRequest request = new UpdateRequest();
        ProfileUpdate update = new ProfileUpdate();
        update.id = profileId;
        update.clearApiKey = Boolean.TRUE;
        request.profiles = Collections.singletonList(update);
        return update(request, actorName);
    }

    public static String validateBaseUrl(String value) {
        String baseUrl = safe(value);
        if (baseUrl.length() > 512) {
            throw new IllegalArgumentException("AI API base URL is too long.");
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        try {
            URI uri = new URI(baseUrl);
            String scheme = safe(uri.getScheme()).toLowerCase();
            String host = safe(uri.getHost()).toLowerCase();
            if (host.isEmpty() || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
                throw new IllegalArgumentException("AI API base URL must not contain credentials, query, or fragment.");
            }
            boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
            if (!"https".equals(scheme) && !("http".equals(scheme) && loopback)) {
                throw new IllegalArgumentException("AI API base URL must use HTTPS; HTTP is allowed only for loopback.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid AI API base URL.");
        }
        return baseUrl;
    }

    private static String validateModel(String value) {
        String model = safe(value);
        if (model.isEmpty() || model.length() > 160 || model.indexOf('\r') >= 0 || model.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid AI model name.");
        }
        return model;
    }

    private static String validateName(String value) {
        String name = safe(value);
        if (name.isEmpty()) name = "Default";
        if (name.length() > 64) name = name.substring(0, 64);
        return name;
    }

    private StoredConfig current() {
        if (stored == null) stored = load();
        return stored;
    }

    private StoredConfig load() {
        File file = settingsFile();
        if (!file.isFile()) return defaults();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String json = readAll(reader);
            StoredConfig loaded = parseStored(json);
            if (loaded == null) return defaults();
            normalize(loaded);
            validateStored(loaded);
            return loaded;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load Web AI settings: {}", e.getMessage());
            return defaults();
        }
    }

    private StoredConfig parseStored(String json) {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        StoredConfig config = new StoredConfig();
        config.version = root.has("version") ? root.get("version").getAsInt() : 1;
        config.updatedAt = root.has("updatedAt") ? root.get("updatedAt").getAsLong() : 0L;
        config.updatedBy = root.has("updatedBy") ? root.get("updatedBy").getAsString() : "";
        if (root.has("profiles") && root.get("profiles").isJsonArray()) {
            config.profiles = new ArrayList<StoredProfile>();
            JsonArray array = root.getAsJsonArray("profiles");
            for (JsonElement element : array) {
                StoredProfile profile = GSON.fromJson(element, StoredProfile.class);
                if (profile != null) config.profiles.add(profile);
            }
        } else {
            // v1 single-object migration
            StoredProfile profile = new StoredProfile();
            profile.id = "default";
            profile.name = "Default";
            profile.enabled = root.has("enabled") ? root.get("enabled").getAsBoolean() : true;
            profile.order = 0;
            profile.providerId = root.has("providerId") ? root.get("providerId").getAsString() : "deepseek";
            profile.baseUrl = root.has("baseUrl") ? root.get("baseUrl").getAsString() : "";
            profile.model = root.has("model") ? root.get("model").getAsString() : "";
            profile.encryptedApiKey = root.has("encryptedApiKey") ? root.get("encryptedApiKey").getAsString() : "";
            profile.apiKeyHint = root.has("apiKeyHint") ? root.get("apiKeyHint").getAsString() : "";
            profile.timeoutSeconds = root.has("timeoutSeconds") ? root.get("timeoutSeconds").getAsInt() : 45;
            profile.temperature = root.has("temperature") ? root.get("temperature").getAsDouble() : 0.1D;
            profile.maxTokens = root.has("maxTokens") ? root.get("maxTokens").getAsInt() : 1200;
            config.profiles = new ArrayList<StoredProfile>();
            config.profiles.add(profile);
            config.version = 2;
        }
        if (root.has("search") && root.get("search").isJsonObject()) {
            config.search = GSON.fromJson(root.get("search"), StoredSearch.class);
        } else {
            config.search = defaultSearch();
        }
        return config;
    }

    private void save(StoredConfig config) {
        File target = settingsFile();
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        target.getParentFile().mkdirs();
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(temp, false), StandardCharsets.UTF_8))) {
            GSON.toJson(config, writer);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save Web AI settings.", e);
        }
        restrictOwnerOnly(temp);
        try {
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            restrictOwnerOnly(target);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to replace Web AI settings.", e);
        }
    }

    private static StoredConfig defaults() {
        StoredConfig config = new StoredConfig();
        config.version = 2;
        config.profiles = new ArrayList<StoredProfile>();
        config.profiles.add(defaultProfile("default", "Default", 0));
        config.search = defaultSearch();
        return config;
    }

    private static StoredProfile defaultProfile(String id, String name, int order) {
        ProviderProfile profile = AiProviderProfiles.findProfile("deepseek");
        StoredProfile stored = new StoredProfile();
        stored.id = id;
        stored.name = name;
        stored.enabled = true;
        stored.order = order;
        stored.providerId = profile == null ? "deepseek" : profile.id;
        stored.baseUrl = profile == null ? "https://api.deepseek.com" : profile.baseUrl;
        stored.model = profile == null ? "deepseek-chat" : profile.defaultModel;
        stored.timeoutSeconds = 45;
        stored.temperature = 0.1D;
        stored.maxTokens = 1200;
        return stored;
    }

    private static StoredSearch defaultSearch() {
        StoredSearch search = new StoredSearch();
        search.enabled = false;
        search.mode = WebSearchService.PROVIDER_AUTO;
        search.maxResults = 5;
        search.fallback = true;
        search.baseUrl = "";
        search.encryptedApiKey = "";
        search.apiKeyHint = "";
        return search;
    }

    private static StoredConfig copy(StoredConfig source) {
        return GSON.fromJson(GSON.toJson(source), StoredConfig.class);
    }

    private List<StoredProfile> applyProfileUpdates(StoredConfig config, List<ProfileUpdate> updates) {
        if (updates.size() > MAX_PROFILES) {
            throw new IllegalArgumentException("Too many AI profiles (max " + MAX_PROFILES + ").");
        }
        List<StoredProfile> existing = orderedProfiles(config);
        List<StoredProfile> next = new ArrayList<StoredProfile>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < updates.size(); i++) {
            ProfileUpdate update = updates.get(i);
            if (update == null) continue;
            StoredProfile profile = findProfile(existing, update.id);
            if (profile == null) {
                profile = defaultProfile(newId(), "Profile " + (i + 1), i);
            } else {
                profile = copyProfile(profile);
            }
            if (update.id != null && !safe(update.id).isEmpty()) profile.id = safe(update.id);
            if (update.name != null) profile.name = validateName(update.name);
            if (update.enabled != null) profile.enabled = update.enabled.booleanValue();
            if (update.order != null) profile.order = update.order.intValue();
            else profile.order = i;
            if (update.providerId != null && !safe(update.providerId).isEmpty()) {
                String providerId = safe(update.providerId);
                if (!isKnownProvider(providerId)) {
                    throw new IllegalArgumentException("Unknown AI provider: " + providerId);
                }
                boolean changed = !providerId.equals(profile.providerId);
                profile.providerId = providerId;
                ProviderProfile catalog = AiProviderProfiles.findProfile(providerId);
                if (changed && catalog != null && update.baseUrl == null && update.model == null) {
                    profile.baseUrl = catalog.baseUrl;
                    profile.model = catalog.defaultModel;
                }
            }
            if (update.baseUrl != null) profile.baseUrl = validateBaseUrl(update.baseUrl);
            if (update.model != null) profile.model = validateModel(update.model);
            if (update.timeoutSeconds != null) {
                profile.timeoutSeconds = clamp(update.timeoutSeconds.intValue(), 5, 120);
            }
            if (update.maxTokens != null) profile.maxTokens = clamp(update.maxTokens.intValue(), 64, 8192);
            if (update.temperature != null) {
                profile.temperature = Math.max(0.0D, Math.min(2.0D, update.temperature.doubleValue()));
            }
            if (Boolean.TRUE.equals(update.clearApiKey)) {
                profile.encryptedApiKey = "";
                profile.apiKeyHint = "";
            } else if (update.apiKey != null && !update.apiKey.trim().isEmpty()) {
                String apiKey = update.apiKey.trim();
                if (apiKey.length() > MAX_KEY_LENGTH) {
                    throw new IllegalArgumentException("API key is too long.");
                }
                profile.encryptedApiKey = encryptApiKey(apiKey);
                profile.apiKeyHint = mask(apiKey);
            }
            if (seen.contains(profile.id)) {
                throw new IllegalArgumentException("Duplicate AI profile id: " + profile.id);
            }
            seen.add(profile.id);
            next.add(profile);
        }
        return next;
    }

    private List<StoredProfile> applyLegacySingleUpdate(StoredConfig config, UpdateRequest update) {
        List<StoredProfile> profiles = orderedProfiles(config);
        StoredProfile profile = profiles.isEmpty() ? defaultProfile("default", "Default", 0)
            : copyProfile(profiles.get(0));
        ProfileUpdate single = new ProfileUpdate();
        single.id = profile.id;
        single.name = profile.name;
        single.enabled = update.enabled;
        single.providerId = update.providerId;
        single.baseUrl = update.baseUrl;
        single.model = update.model;
        single.apiKey = update.apiKey;
        single.clearApiKey = update.clearApiKey;
        single.timeoutSeconds = update.timeoutSeconds;
        single.temperature = update.temperature;
        single.maxTokens = update.maxTokens;
        single.order = 0;
        List<ProfileUpdate> list = new ArrayList<ProfileUpdate>();
        list.add(single);
        for (int i = 1; i < profiles.size(); i++) {
            ProfileUpdate keep = new ProfileUpdate();
            keep.id = profiles.get(i).id;
            keep.name = profiles.get(i).name;
            keep.enabled = Boolean.valueOf(profiles.get(i).enabled);
            keep.order = Integer.valueOf(i);
            keep.providerId = profiles.get(i).providerId;
            keep.baseUrl = profiles.get(i).baseUrl;
            keep.model = profiles.get(i).model;
            keep.timeoutSeconds = Integer.valueOf(profiles.get(i).timeoutSeconds);
            keep.temperature = Double.valueOf(profiles.get(i).temperature);
            keep.maxTokens = Integer.valueOf(profiles.get(i).maxTokens);
            list.add(keep);
        }
        return applyProfileUpdates(config, list);
    }

    private StoredSearch applySearchUpdate(StoredSearch current, SearchUpdate update) {
        StoredSearch search = current == null ? defaultSearch() : GSON.fromJson(GSON.toJson(current), StoredSearch.class);
        if (update.enabled != null) search.enabled = update.enabled.booleanValue();
        if (update.mode != null) search.mode = WebSearchService.normalizeProvider(update.mode);
        if (update.baseUrl != null) {
            String base = safe(update.baseUrl);
            search.baseUrl = base.isEmpty() ? "" : validateBaseUrl(base);
        }
        if (update.maxResults != null) search.maxResults = clamp(update.maxResults.intValue(), 1, 10);
        if (update.fallback != null) search.fallback = update.fallback.booleanValue();
        if (Boolean.TRUE.equals(update.clearApiKey)) {
            search.encryptedApiKey = "";
            search.apiKeyHint = "";
        } else if (update.apiKey != null && !update.apiKey.trim().isEmpty()) {
            String apiKey = update.apiKey.trim();
            if (apiKey.length() > MAX_KEY_LENGTH) {
                throw new IllegalArgumentException("Search API key is too long.");
            }
            search.encryptedApiKey = encryptApiKey(apiKey);
            search.apiKeyHint = mask(apiKey);
        }
        return search;
    }

    private static boolean isLegacySingleUpdate(UpdateRequest update) {
        return update.enabled != null || update.providerId != null || update.baseUrl != null || update.model != null
            || update.apiKey != null || update.clearApiKey != null || update.timeoutSeconds != null
            || update.temperature != null || update.maxTokens != null;
    }

    private static void normalize(StoredConfig config) {
        if (config.profiles == null || config.profiles.isEmpty()) {
            config.profiles = new ArrayList<StoredProfile>();
            config.profiles.add(defaultProfile("default", "Default", 0));
        }
        if (config.search == null) config.search = defaultSearch();
        config.search.mode = WebSearchService.normalizeProvider(config.search.mode);
        config.search.maxResults = clamp(config.search.maxResults, 1, 10);
        config.search.encryptedApiKey = safe(config.search.encryptedApiKey);
        config.search.apiKeyHint = safe(config.search.apiKeyHint);
        config.search.baseUrl = safe(config.search.baseUrl);
        for (int i = 0; i < config.profiles.size(); i++) {
            StoredProfile profile = config.profiles.get(i);
            StoredProfile defaults = defaultProfile("default", "Default", i);
            if (safe(profile.id).isEmpty()) profile.id = newId();
            profile.name = validateName(profile.name);
            if (!isKnownProvider(profile.providerId)) profile.providerId = defaults.providerId;
            if (safe(profile.baseUrl).isEmpty()) profile.baseUrl = defaults.baseUrl;
            if (safe(profile.model).isEmpty()) profile.model = defaults.model;
            profile.timeoutSeconds = clamp(profile.timeoutSeconds <= 0 ? defaults.timeoutSeconds : profile.timeoutSeconds,
                5, 120);
            profile.maxTokens = profile.maxTokens <= 0 ? defaults.maxTokens : clamp(profile.maxTokens, 64, 8192);
            profile.temperature = Math.max(0.0D, Math.min(2.0D, profile.temperature));
            profile.encryptedApiKey = safe(profile.encryptedApiKey);
            profile.apiKeyHint = safe(profile.apiKeyHint);
            profile.order = i;
        }
        config.updatedBy = safe(config.updatedBy);
        config.version = 2;
    }

    private static void validateStored(StoredConfig config) {
        for (StoredProfile profile : config.profiles) {
            if (!isKnownProvider(profile.providerId)) throw new IllegalArgumentException("Unknown AI provider.");
            profile.baseUrl = validateBaseUrl(profile.baseUrl);
            profile.model = validateModel(profile.model);
        }
        if (config.search != null && !safe(config.search.baseUrl).isEmpty()) {
            config.search.baseUrl = validateBaseUrl(config.search.baseUrl);
        }
    }

    private String encryptApiKey(String apiKey) {
        try {
            byte[] key = masterKey();
            byte[] iv = new byte[GCM_IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            return CIPHER_PREFIX + Base64.getEncoder().encodeToString(iv) + ":"
                + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt the API key.", e);
        }
    }

    private String decryptApiKey(String encryptedValue) {
        String encrypted = safe(encryptedValue);
        if (encrypted.isEmpty()) return "";
        try {
            if (!encrypted.startsWith(CIPHER_PREFIX)) throw new IllegalStateException("Unknown secret format.");
            String[] parts = encrypted.substring(CIPHER_PREFIX.length()).split(":", 2);
            if (parts.length != 2) throw new IllegalStateException("Invalid secret format.");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey(), "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to decrypt a configured AI API key; re-enter it in Settings.");
            return "";
        }
    }

    private byte[] masterKey() throws Exception {
        File file = masterKeyFile();
        if (file.isFile()) {
            byte[] encoded = Files.readAllBytes(file.toPath());
            byte[] decoded = Base64.getDecoder().decode(new String(encoded, StandardCharsets.US_ASCII).trim());
            if (decoded.length != MASTER_KEY_BYTES) throw new IllegalStateException("Invalid Web AI master key.");
            return decoded;
        }
        byte[] key = new byte[MASTER_KEY_BYTES];
        RANDOM.nextBytes(key);
        file.getParentFile().mkdirs();
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.write(temp.toPath(), Base64.getEncoder().encode(key));
        restrictOwnerOnly(temp);
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp.toPath(), file.toPath());
        }
        restrictOwnerOnly(file);
        return key;
    }

    private static List<ProviderView> providerViews() {
        List<ProviderView> result = new ArrayList<ProviderView>();
        for (ProviderProfile profile : AiProviderProfiles.allProfiles()) {
            ProviderView view = new ProviderView();
            view.id = profile.id;
            view.displayName = profile.displayName;
            view.defaultBaseUrl = profile.baseUrl;
            view.defaultModel = profile.defaultModel;
            view.models = profile.modelPresets.clone();
            view.protocol = protocolFor(profile.id);
            result.add(view);
        }
        ProviderView custom = new ProviderView();
        custom.id = "custom";
        custom.displayName = "Custom OpenAI-compatible";
        custom.defaultBaseUrl = "https://example.invalid";
        custom.defaultModel = "model-name";
        custom.models = new String[0];
        custom.protocol = PROTOCOL_OPENAI;
        result.add(custom);
        return result;
    }

    private static List<ProfileView> profileViews(StoredConfig config, boolean includeHints) {
        List<ProfileView> result = new ArrayList<ProfileView>();
        for (StoredProfile profile : orderedProfiles(config)) {
            ProfileView view = new ProfileView();
            view.id = profile.id;
            view.name = profile.name;
            view.enabled = profile.enabled;
            view.order = profile.order;
            view.providerId = profile.providerId;
            view.protocol = protocolFor(profile.providerId);
            view.baseUrl = profile.baseUrl;
            view.model = profile.model;
            view.timeoutSeconds = profile.timeoutSeconds;
            view.temperature = profile.temperature;
            view.maxTokens = profile.maxTokens;
            view.hasApiKey = profile.encryptedApiKey != null && !profile.encryptedApiKey.isEmpty();
            view.apiKeyHint = includeHints && view.hasApiKey ? profile.apiKeyHint : "";
            view.configured = profile.enabled && view.hasApiKey && !safe(profile.baseUrl).isEmpty()
                && !safe(profile.model).isEmpty();
            result.add(view);
        }
        return result;
    }

    private static SearchView searchView(StoredSearch search, boolean includeHints) {
        StoredSearch value = search == null ? defaultSearch() : search;
        SearchView view = new SearchView();
        view.enabled = value.enabled;
        view.mode = WebSearchService.normalizeProvider(value.mode);
        view.baseUrl = safe(value.baseUrl);
        view.maxResults = clamp(value.maxResults, 1, 10);
        view.fallback = value.fallback;
        view.hasApiKey = value.encryptedApiKey != null && !value.encryptedApiKey.isEmpty();
        view.apiKeyHint = includeHints && view.hasApiKey ? value.apiKeyHint : "";
        view.providers = WebSearchService.allProviders();
        return view;
    }

    private static void fillCompatFields(ConfigView view, StoredConfig config) {
        List<ProfileView> profiles = view.profiles;
        if (profiles == null || profiles.isEmpty()) return;
        ProfileView first = null;
        for (ProfileView profile : profiles) {
            if (profile.configured) {
                first = profile;
                break;
            }
        }
        if (first == null) first = profiles.get(0);
        view.enabled = first.enabled;
        view.hasApiKey = first.hasApiKey;
        view.apiKeyHint = first.apiKeyHint;
        view.providerId = first.providerId;
        view.protocol = first.protocol;
        view.baseUrl = first.baseUrl;
        view.model = first.model;
        view.timeoutSeconds = first.timeoutSeconds;
        view.temperature = first.temperature;
        view.maxTokens = first.maxTokens;
    }

    private static boolean hasConfiguredEnabledProfile(StoredConfig config) {
        for (StoredProfile profile : orderedProfiles(config)) {
            if (profile.enabled && profile.encryptedApiKey != null && !profile.encryptedApiKey.isEmpty()
                && !safe(profile.baseUrl).isEmpty() && !safe(profile.model).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static List<StoredProfile> orderedProfiles(StoredConfig config) {
        List<StoredProfile> list = new ArrayList<StoredProfile>();
        if (config.profiles != null) list.addAll(config.profiles);
        Collections.sort(list, new Comparator<StoredProfile>() {
            @Override
            public int compare(StoredProfile a, StoredProfile b) {
                if (a.order != b.order) return a.order < b.order ? -1 : 1;
                return safe(a.id).compareTo(safe(b.id));
            }
        });
        return list;
    }

    private static StoredProfile findProfile(List<StoredProfile> profiles, String id) {
        String target = safe(id);
        if (target.isEmpty()) return null;
        for (StoredProfile profile : profiles) {
            if (target.equals(profile.id)) return profile;
        }
        return null;
    }

    private static StoredProfile copyProfile(StoredProfile source) {
        return GSON.fromJson(GSON.toJson(source), StoredProfile.class);
    }

    private static boolean isKnownProvider(String providerId) {
        return "custom".equals(safe(providerId)) || AiProviderProfiles.findProfile(providerId) != null;
    }

    public static String protocolFor(String providerId) {
        if ("anthropic".equals(providerId)) return PROTOCOL_ANTHROPIC;
        if ("gemini".equals(providerId)) return PROTOCOL_GEMINI;
        return PROTOCOL_OPENAI;
    }

    private static String mask(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) return "";
        int visible = Math.min(4, apiKey.length());
        return "••••" + apiKey.substring(apiKey.length() - visible);
    }

    private static void restrictOwnerOnly(File file) {
        if (file == null || !file.exists()) return;
        try {
            file.setReadable(false, false);
            file.setWritable(false, false);
            file.setExecutable(false, false);
            file.setReadable(true, true);
            file.setWritable(true, true);
        } catch (SecurityException ignored) {}
    }

    private static File settingsFile() {
        return TeXTechDataDir.webAeFile(SETTINGS_FILE);
    }

    private static File masterKeyFile() {
        return TeXTechDataDir.webAeFile(MASTER_KEY_FILE);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String readAll(BufferedReader reader) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    private static final class StoredConfig {
        int version = 2;
        List<StoredProfile> profiles;
        StoredSearch search;
        long updatedAt;
        String updatedBy;
    }

    private static final class StoredProfile {
        String id;
        String name;
        boolean enabled;
        int order;
        String providerId;
        String baseUrl;
        String model;
        String encryptedApiKey;
        String apiKeyHint;
        int timeoutSeconds;
        double temperature;
        int maxTokens;
    }

    private static final class StoredSearch {
        boolean enabled;
        String mode;
        String encryptedApiKey;
        String apiKeyHint;
        String baseUrl;
        int maxResults;
        boolean fallback;
    }

    public static final class UpdateRequest {
        public Boolean enabled;
        public String providerId;
        public String baseUrl;
        public String model;
        public String apiKey;
        public Boolean clearApiKey;
        public Integer timeoutSeconds;
        public Double temperature;
        public Integer maxTokens;
        public List<ProfileUpdate> profiles;
        public SearchUpdate search;
    }

    public static final class ProfileUpdate {
        public String id;
        public String name;
        public Boolean enabled;
        public Integer order;
        public String providerId;
        public String baseUrl;
        public String model;
        public String apiKey;
        public Boolean clearApiKey;
        public Integer timeoutSeconds;
        public Double temperature;
        public Integer maxTokens;
    }

    public static final class SearchUpdate {
        public Boolean enabled;
        public String mode;
        public String apiKey;
        public Boolean clearApiKey;
        public String baseUrl;
        public Integer maxResults;
        public Boolean fallback;
    }

    public static final class RuntimeConfig {
        public String id;
        public String name;
        public String providerId;
        public String protocol;
        public String baseUrl;
        public String model;
        public String apiKey;
        public int timeoutSeconds;
        public double temperature;
        public int maxTokens;
    }

    public static final class ConfigView {
        public boolean enabled;
        public boolean configured;
        public boolean hasApiKey;
        public String apiKeyHint;
        public String providerId;
        public String protocol;
        public String baseUrl;
        public String model;
        public int timeoutSeconds;
        public double temperature;
        public int maxTokens;
        public long updatedAt;
        public String updatedBy;
        public String secretStorage;
        public List<ProviderView> providers;
        public List<ProfileView> profiles;
        public SearchView search;
    }

    public static final class ProfileView {
        public String id;
        public String name;
        public boolean enabled;
        public int order;
        public String providerId;
        public String protocol;
        public String baseUrl;
        public String model;
        public int timeoutSeconds;
        public double temperature;
        public int maxTokens;
        public boolean hasApiKey;
        public String apiKeyHint;
        public boolean configured;
    }

    public static final class SearchView {
        public boolean enabled;
        public String mode;
        public boolean hasApiKey;
        public String apiKeyHint;
        public String baseUrl;
        public int maxResults;
        public boolean fallback;
        public String[] providers;
    }

    public static final class PublicSharedView {
        public boolean serverKeyEnabled;
        public boolean browserKeyEnabled;
        public boolean configured;
        public int enabledCount;
        public List<PublicProfileView> profiles;
        public SearchView search;
    }

    public static final class PublicProfileView {
        public String id;
        public String name;
        public boolean enabled;
        public int order;
        public String providerId;
        public String model;
        public boolean hasApiKey;
        public String apiKeyHint;
        public boolean configured;
    }

    public static final class ProviderView {
        public String id;
        public String displayName;
        public String defaultBaseUrl;
        public String defaultModel;
        public String protocol;
        public String[] models;
    }
}
