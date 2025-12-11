package it.hiken.i18n;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Handles internationalization (i18n) for the plugin.
 * <p>
 * This class manages loading language files, parsing mixed color codes
 * (MiniMessage, Legacy, Hex), and sending localized messages to command senders.
 * </p>
 */
@SuppressWarnings("unused")
public class I18n {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fk-or])");
    private static final Map<Character, String> LEGACY_MAP = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"), Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"), Map.entry('6', "<gold>"), Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"), Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"), Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"), Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"), Map.entry('r', "<reset>")
    );

    private final JavaPlugin plugin;
    private final String defaultLang;
    private String activeLang;
    private final Map<String, YamlConfiguration> configs = new ConcurrentHashMap<>();
    private final BukkitAudiences bukkitAudiences;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /**
     * Initializes the I18n manager and loads language files asynchronously.
     *
     * @param plugin      The main JavaPlugin instance.
     * @param defaultLang The default language code to use (e.g., "en").
     */
    public I18n(JavaPlugin plugin, String defaultLang) {
        this.plugin = plugin;
        this.defaultLang = defaultLang;
        this.activeLang = defaultLang; // Ensures activeLang is not null on start
        this.bukkitAudiences = BukkitAudiences.create(plugin);
        plugin.getLogger().info("[I18n] Initializing I18n with default language: " + defaultLang);
        loadLanguages();
    }

    private void loadLanguages() {
        Thread.startVirtualThread(() -> {
            Path langDir = plugin.getDataFolder().toPath().resolve("languages");
            try {
                if (!Files.exists(langDir)) {
                    Files.createDirectories(langDir);
                }
                String defaultFile = "lang_" + defaultLang + ".yml";
                Path target = langDir.resolve(defaultFile);
                if (!Files.exists(target) && plugin.getResource("languages/" + defaultFile) != null) {
                    plugin.saveResource("languages/" + defaultFile, false);
                    plugin.getLogger().info("[I18n] Created default language file: " + defaultFile);
                }
                try (Stream<Path> paths = Files.walk(langDir, 1)) {
                    paths.forEach(p -> {
                        if (p.equals(langDir) || !p.toString().endsWith(".yml")) return;
                        try {
                            loadConfig(p);
                        } catch (Exception ex) {
                            plugin.getLogger().warning("[I18n] Failed to load language file: " + p.getFileName());
                        }
                    });
                }
                plugin.getLogger().info("[I18n] Loaded " + configs.size() + " languages.");
            } catch (IOException e) {
                plugin.getLogger().severe("[I18n] Critical error accessing language directory. " + e.getMessage());
            }
        });
    }

    /**
     * Sets the active language for the plugin.
     * <p>
     * If the language code is not found among loaded configurations,
     * it will fallback to the default language.
     * </p>
     *
     * @param langCode The language code to activate (e.g., "it", "es").
     */
    public void setActiveLanguage(String langCode) {
        if (configs.containsKey(langCode)) {
            this.activeLang = langCode;
            plugin.getLogger().info("[I18n] Active language set to: " + langCode);
        } else {
            plugin.getLogger().warning("[I18n] Language code not found: " + langCode + ". Falling back to default: " + defaultLang);
        }
    }

    private void loadConfig(Path path) {
        String fileName = path.getFileName().toString();
        String code = fileName.replace("lang_", "").replace(".yml", "");
        configs.put(code, YamlConfiguration.loadConfiguration(path.toFile()));
    }

    /**
     * Sends a localized message to a CommandSender.
     * <p>
     * This method runs on a Virtual Thread to process the message and placeholders,
     * then schedules the sending on the main Bukkit thread.
     * </p>
     *
     * @param sender       The recipient of the message.
     * @param key          The key identifier in the language file.
     * @param placeholders Key-Value pairs for placeholder replacement (e.g., "{player}", "Steve").
     * @throws IllegalArgumentException If placeholders are not provided in even pairs.
     */
    public void send(CommandSender sender, String key, Object... placeholders) {
        Thread.startVirtualThread(() -> {
            if (placeholders.length % 2 != 0) throw new IllegalArgumentException("Placeholders must be in key , value pairs");

            YamlConfiguration config = configs.getOrDefault(activeLang, configs.get("en"));
            if (config == null) return;

            String raw = config.getString(key);
            if (raw == null) {
                sender.sendMessage("Missing key: " + key);
                return;
            }

            for (int i = 0; i < placeholders.length; i += 2) {
                String placeholderKey = String.valueOf(placeholders[i]);
                String value = String.valueOf(placeholders[i+1]);
                raw = raw.replace(placeholderKey, value);
            }

            raw = convertLegacyToMiniMessage(raw);
            Component finalMessage = miniMessage.deserialize(raw);

            Bukkit.getScheduler().runTask(plugin, () -> bukkitAudiences.sender(sender).sendMessage(finalMessage));
        });
    }

    private String convertLegacyToMiniMessage(String text) {
        if (!text.contains("&")) return text;

        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(sb, "<#" + hexMatcher.group(1) + ">");
        }
        hexMatcher.appendTail(sb);
        text = sb.toString();

        Matcher legacyMatcher = LEGACY_PATTERN.matcher(text);
        sb = new StringBuilder();
        while (legacyMatcher.find()) {
            char code = legacyMatcher.group(1).charAt(0);
            String replacement = LEGACY_MAP.getOrDefault(code, "&" + code);
            legacyMatcher.appendReplacement(sb, replacement);
        }
        legacyMatcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Closes the Adventure audiences provider.
     * <p>
     * This method should be called when the plugin is disabled to ensure resources are freed.
     * </p>
     */
    public void shutdown() {
        if (bukkitAudiences != null) bukkitAudiences.close();
    }
}