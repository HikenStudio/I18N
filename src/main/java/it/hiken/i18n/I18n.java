package it.hiken.i18n;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        this.activeLang = defaultLang;
        this.bukkitAudiences = BukkitAudiences.create(plugin);
        plugin.getLogger().info("[I18n] Initializing I18n with default language: " + defaultLang);
        loadLanguages();
    }

    private void loadLanguages() {
        Thread.startVirtualThread(() -> {
            Path langDir = plugin.getDataFolder().toPath().resolve("languages");
            try {
                CodeSource src = plugin.getClass().getProtectionDomain().getCodeSource();
                if (src != null) {
                    try (ZipInputStream zip = new ZipInputStream(src.getLocation().openStream())) {
                        ZipEntry entry;
                        while ((entry = zip.getNextEntry()) != null) {
                            String name = entry.getName();
                            if (name.startsWith("languages/") && name.endsWith(".yml") && !entry.isDirectory()) {
                                String fileName = name.replace("languages/", "");
                                if (fileName.isEmpty()) continue;
                                Path target = langDir.resolve(fileName);
                                if (!Files.exists(target)) {
                                    plugin.saveResource(name, false);
                                    plugin.getLogger().info("[I18n] Extracted language file: " + fileName);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[I18n] Could not scan JAR for languages: " + e.getMessage());
            }
            try {
                if (!Files.exists(langDir)) Files.createDirectories(langDir);

                try (Stream<Path> paths = Files.walk(langDir, 1)) {
                    paths.filter(p -> !p.equals(langDir) && p.toString().endsWith(".yml"))
                            .forEach(p -> {
                                try {
                                    loadConfig(p);
                                    plugin.getLogger().info("[I18n] Loaded language file: " + p.getFileName());
                                } catch (Exception ex) {
                                    plugin.getLogger().warning("[I18n] Failed to load: " + p.getFileName());
                                }
                            });
                }
                plugin.getLogger().info("[I18n] Loaded " + configs.size() + " languages.");
            } catch (IOException e) {
                plugin.getLogger().severe("[I18n] Critical error accessing language directory.");
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
        plugin.getLogger().info("[I18n] Loading configuration from: " + path.getFileName());
        String fileName = path.getFileName().toString();
        String code = fileName.replace("lang_", "").replace(".yml", "");
        configs.put(code, YamlConfiguration.loadConfiguration(path.toFile()));
    }

    /**
     * Sends a localized, formatted message to the specified {@link CommandSender}.
     * <p>
     * This method handles message processing asynchronously using a <b>Virtual Thread</b> to avoid
     * blocking the main server thread during configuration lookups and string parsing.
     * The final packet sending is synchronized back to the main Bukkit thread.
     * </p>
     *
     * <h3>Locale Resolution Logic:</h3>
     * <ol>
     * <li>If the sender is a {@link Player}, attempts to resolve their client locale (e.g., "en", "it").</li>
     * <li>If the specific locale is not loaded, falls back to the plugin's default {@code activeLang}.</li>
     * <li>Finally, defaults to the "en" configuration if the target language file is missing.</li>
     * </ol>
     *
     * @param sender       The recipient of the message (Player or Console).
     * @param key          The unique configuration key for the message path.
     * @param placeholders A varargs array of key-value pairs for string replacement.
     * <br>Format: {@code "placeholder_key", value, "placeholder_key2", value2}.
     * @throws IllegalArgumentException If the {@code placeholders} array length is not even.
     */
    public void send(CommandSender sender, String key, Object... placeholders) {
        if (placeholders.length % 2 != 0) throw new IllegalArgumentException("Placeholders must be in key, value pairs");

        Thread.startVirtualThread(() -> {
            String lang = activeLang;

            if (sender instanceof Player player) {
                String playerLocale = player.getLocale().split("_")[0].toLowerCase(Locale.ROOT);
                if (configs.containsKey(playerLocale)) {
                    lang = playerLocale;
                }
            }

            YamlConfiguration config = configs.getOrDefault(lang, configs.get(defaultLang));
            if (config == null) return;

            String raw = config.getString(key);
            if (raw == null) {
                sender.sendMessage("Missing key: " + key);
                return;
            }

            for (int i = 0; i < placeholders.length; i += 2) {
                raw = raw.replace(String.valueOf(placeholders[i]), String.valueOf(placeholders[i + 1]));
            }

            final Component finalMessage = miniMessage.deserialize(convertLegacyToMiniMessage(raw));
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