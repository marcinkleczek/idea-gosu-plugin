package gosu.gsp.settings;

import gosu.gsp.run.GosuLauncher;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** The same paths feed editor indexing and the Gosu launcher's -classpath argument. */
public final class GosuClasspath {
  private GosuClasspath() {}

  public static List<String> entries(String text, String basePath) {
    Set<String> paths = new LinkedHashSet<>();
    for (String line : text.split("\\R")) {
      if (line.isBlank()) continue;
      Path path = Path.of(GosuLauncher.expandHome(line));
      if (!path.isAbsolute()) {
        if (basePath == null) throw new IllegalArgumentException("Relative classpath requires a project directory");
        path = Path.of(basePath).resolve(path);
      }
      paths.add(path.normalize().toString());
    }
    return List.copyOf(paths);
  }

  public static void validate(String text, String basePath) {
    for (String entry : entries(text, basePath)) {
      Path path = Path.of(entry);
      if (entry.contains(",")) throw new IllegalArgumentException("Gosu classpath paths cannot contain commas: " + entry);
      if (!Files.isDirectory(path) && !(Files.isRegularFile(path) && entry.toLowerCase(Locale.ROOT).endsWith(".jar"))) {
        throw new IllegalArgumentException("Classpath entry must be an existing directory or JAR: " + entry);
      }
    }
  }

  public static List<String> sdkJars(String sdkHome) {
    if (sdkHome.isBlank()) return List.of();
    Path lib = Path.of(GosuLauncher.expandHome(sdkHome), "lib");
    if (!Files.isDirectory(lib)) return List.of();
    try (var files = Files.list(lib)) {
      return files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".jar"))
          .sorted().map(Path::toString).toList();
    } catch (IOException e) { return List.of(); }
  }

  public static String validateJdk(String home, boolean windows) {
    if (home.isBlank()) return "";
    Path path = Path.of(GosuLauncher.expandHome(home));
    if (Files.isDirectory(path.resolve("Contents/Home"))) path = path.resolve("Contents/Home");
    String suffix = windows ? ".exe" : "";
    if (!path.isAbsolute() || !Files.isRegularFile(path.resolve("bin/java" + suffix))
        || !Files.isRegularFile(path.resolve("bin/javac" + suffix))) {
      throw new IllegalArgumentException("Select a JDK home containing bin/java and bin/javac: " + home);
    }
    // Homebrew's prefix has bin/java symlinks but no JDK module roots.
    if (!Files.isRegularFile(path.resolve("release"))) {
      try {
        Path actual = path.resolve("bin/java" + suffix).toRealPath().getParent().getParent();
        if (Files.isRegularFile(actual.resolve("release"))) path = actual;
      } catch (IOException e) {
        throw new IllegalArgumentException("Cannot read JDK home: " + path, e);
      }
    }
    return path.normalize().toString();
  }
}
