package gosu.gsp.run;

import gosu.gsp.settings.GosuClasspath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GosuClasspathTest {
  @TempDir Path temp;

  @Test void resolvesRelativePathsSpacesAndDuplicates() throws Exception {
    Files.createDirectory(temp.resolve("my classes"));
    Files.createFile(temp.resolve("library.jar"));
    String input = "my classes\nlibrary.jar\nmy classes\n";
    assertEquals(List.of(temp.resolve("my classes").toString(), temp.resolve("library.jar").toString()),
        GosuClasspath.entries(input, temp.toString()));
    assertDoesNotThrow(() -> GosuClasspath.validate(input, temp.toString()));
    assertThrows(IllegalArgumentException.class, () -> GosuClasspath.validate("missing.jar", temp.toString()));
    assertThrows(IllegalArgumentException.class, () -> GosuClasspath.validate("my,classes", temp.toString()));
  }

  @Test void validatesJdkAndAcceptsMacBundle() throws Exception {
    Path home = Files.createDirectories(temp.resolve("jdk/Contents/Home/bin"));
    Files.createFile(home.resolve("java"));
    assertThrows(IllegalArgumentException.class, () -> GosuClasspath.validateJdk(temp.resolve("jdk").toString(), false));
    Files.createFile(home.resolve("javac"));
    assertEquals(home.getParent().toString(), GosuClasspath.validateJdk(temp.resolve("jdk").toString(), false));
  }
}
