package it.hiken.i18n;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class I18n {

    private final JavaPlugin plugin;
    private final String defaultLang;
    private final Map<String, YamlConfiguration> configs = new ConcurrentHashMap<>();
    private final BukkitAudiences bukkitAudiences;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public I18n(JavaPlugin plugin, String defaultLang) {
        this.plugin = plugin;
        this.defaultLang = defaultLang;
        this.bukkitAudiences = BukkitAudiences.create(plugin);
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
                if (!Files.exists(target) && plugin.getResource(defaultFile) != null) {
                    plugin.saveResource("languages/" + defaultFile, false);
                }

                try (Stream<Path> paths = Files.walk(langDir, 1)) {
                    paths.filter(p -> p.toString().endsWith(".yml")).forEach(this::loadConfig);
                }

                plugin.getLogger().info("[I18n] Loaded " + configs.size() + " languages.");

            } catch (IOException e) {
                plugin.getLogger().severe("Failed to load languages: " + e.getMessage());
            }
        });
    }

    private void loadConfig(Path path) {
        String fileName = path.getFileName().toString();
        String code = fileName.replace("lang_", "").replace(".yml", "");
        configs.put(code, YamlConfiguration.loadConfiguration(path.toFile()));
    }

    public void send(CommandSender sender, String key, Object... args) {
        if (args.length % 2 != 0) throw new IllegalArgumentException("Placeholders must be in key , value pairs");

        YamlConfiguration config = configs.getOrDefault(defaultLang, configs.get("en"));
        if (config == null) return;

        String raw = config.getString(key);
        if (raw == null) {
            sender.sendMessage("Missing key: " + key);
            return;
        }

        List<TagResolver> resolvers = new ArrayList<>();
        for (int i = 0; i < args.length; i += 2) {
            String placeholderKey = String.valueOf(args[i]);
            placeholderKey = placeholderKey.replaceAll("[%{}]", "");

            String value = String.valueOf(args[i+1]);
            resolvers.add(Placeholder.component(placeholderKey, Component.text(value)));
        }

        Component finalMessage;
        if (raw.contains("&")) {
            finalMessage = legacySerializer.deserialize(raw);
        } else {
            finalMessage = miniMessage.deserialize(raw, TagResolver.resolver(resolvers));
        }

        bukkitAudiences.sender(sender).sendMessage(finalMessage);
    }

    public void shutdown() {
        if (bukkitAudiences != null) bukkitAudiences.close();
    }
}