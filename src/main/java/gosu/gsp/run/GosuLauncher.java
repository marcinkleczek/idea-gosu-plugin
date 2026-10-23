package gosu.gsp.run;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves the shared SDK unless a run configuration overrides its launcher. */
public final class GosuLauncher {
  private GosuLauncher() {}

  public static String expandHome(String path) {
    String value = path.trim();
    if (value.equals("~")) return System.getProperty("user.home");
    if (value.startsWith("~/") || value.startsWith("~\\")) {
      return System.getProperty("user.home") + value.substring(1);
    }
    return value;
  }

  public static String resolve(String executable, String sdkHome, boolean windows) {
    String override = expandHome(executable);
    // "gosu" was the default in configurations saved before SDK settings existed.
    if (!override.isEmpty() && !override.equals("gosu")) return override;
    if (sdkHome.isBlank()) return "gosu";
    return Path.of(expandHome(sdkHome), "bin", windows ? "gosu.cmd" : "gosu").toString();
  }

  public static void validateSdk(String sdkHome, boolean windows) {
    if (sdkHome.isBlank()) return;
    Path launcher = Path.of(resolve("", sdkHome, windows));
    if (!launcher.isAbsolute()) {
      throw new IllegalArgumentException("Gosu SDK home must be an absolute path (or start with ~/)");
    }
    if (!Files.isRegularFile(launcher)) {
      throw new IllegalArgumentException("Gosu launcher not found: " + launcher
          + ". Select the SDK directory containing bin and lib.");
    }
    if (!windows && !Files.isExecutable(launcher)) {
      throw new IllegalArgumentException("Gosu launcher is not executable: " + launcher);
    }
  }
}
