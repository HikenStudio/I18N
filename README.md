

# 🌐 I18n - Internationalization Library for Spigot/Paper

[![GitHub release](https://img.shields.io/github/v/release/HikenStudio/I18N?style=flat-square)](

[](https://jitpack.io/#HikenStudio/I18N
)[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT))

**I18n** is a lightweight, asynchronous, and modern internationalization library for Spigot/Paper plugins. Built on top of **Adventure**, it provides seamless support for MiniMessage, Legacy colors, and Hex colors within the same message.

## ✨ Features

* **Hybrid Color Support:** Mix **MiniMessage** (`<red>`), **Legacy** (`&c`), and **Hex** (`&#ff0000`) formats in a single string.
* **Asynchronous Loading:** Uses Java 21 **Virtual Threads** to load and process language files without blocking the main thread.
* **Adventure Powered:** Full integration with `net.kyori.adventure`.
* **Simple Placeholder System:** Easy key-value replacement.
* **Hot Swapping:** Change the active language at runtime.

## 📦 Installation

You can import this library using **JitPack** (recommended) or by building it locally.

### Method 1: JitPack (Recommended)

1.  **Add the Repository** to your `build.gradle.kts`:

<!-- end list -->

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
```

2.  **Add the Dependency**:
    Replace `Tag` with the latest version (e.g., `v1.0.0`).

<!-- end list -->

```kotlin
dependencies {
    implementation("com.github.HikenStudio:I18N-Library:Tag")
    // Ensure you shade/shadow the library into your plugin jar
}
```

### Method 2: Local Build

1.  Clone the repository and publish to Maven Local:

    ```bash
    ./gradlew publishToMavenLocal
    ```

2.  Add `mavenLocal()` to your repositories and depend on the snapshot:

    ```kotlin
    repositories {
        mavenLocal()
    }

    dependencies {
        implementation("it.hiken.i18n:i18n-lib:1.0-SNAPSHOT")
    }
    ```

## 🚀 Usage

### Initialization

Initialize the `I18n` class in your plugin's main class.

```java
import it.hiken.i18n.I18n;

public class MyPlugin extends JavaPlugin {
    
    private I18n i18n;

    @Override
    public void onEnable() {
        // Initialize with plugin instance and default language code (e.g., "en")
        this.i18n = new I18n(this, "en");
    }

    @Override
    public void onDisable() {
        // Clean up Adventure audiences
        if (this.i18n != null) {
            this.i18n.shutdown();
        }
    }
    
    public I18n getI18n() {
        return i18n;
    }
}
```

### Sending Messages

Send localized messages to any `CommandSender` (Players, Console).

```java
// Simple message
plugin.getI18n().send(player, "welcome-message");

// Message with placeholders (Key, Value pairs)
plugin.getI18n().send(player, "money-received", 
    "{amount}", "500", 
    "{currency}", "$"
);
```

### Changing Language

You can switch the active language globally at runtime.

```java
plugin.getI18n().setActiveLanguage("it");
```

## 📂 Configuration & Formatting

The library looks for files in `plugins/YourPlugin/languages/`.
File names must follow the format: `lang_{code}.yml` (e.g., `lang_en.yml`, `lang_it.yml`).

### Hybrid Formatting Example

You can mix all color formats. The library automatically converts Legacy and Hex to MiniMessage tags before parsing.

**`lang_en.yml`**:

```yaml
welcome-message: "&cWarning! <gray>You have entered a &#00ff00Safe Zone."
money-received: "<green>You received &6{amount} <bold>{currency}</bold>!"
```

* `&c` → Legacy Red
* `<gray>` → MiniMessage Gray
* `&#00ff00` → Hex Green
* `<bold>` → MiniMessage Decoration

## 🛠 API Reference

| Method | Description |
| :--- | :--- |
| `new I18n(plugin, defaultLang)` | Initializes the system and creates default files if missing. |
| `setActiveLanguage(code)` | Sets the global language (e.g., "it"). Fallbacks to default if invalid. |
| `send(sender, key, placeholders...)` | Sends a parsed message. Placeholders must be in pairs. |
| `shutdown()` | Closes the Adventure audience provider. |

## 📄 License

This project is licensed under the MIT License.