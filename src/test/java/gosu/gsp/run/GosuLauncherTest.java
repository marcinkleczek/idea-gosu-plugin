package gosu.gsp.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GosuLauncherTest {
  @TempDir Path sdk;

  @Test void inheritsSdkForNewAndLegacyConfigurations() {
    String expected = sdk.resolve("bin/gosu").toString();
    assertEquals(expected, GosuLauncher.resolve("", sdk.toString(), false));
    assertEquals(expected, GosuLauncher.resolve("gosu", sdk.toString(), false));
    assertEquals(sdk.resolve("bin/gosu.cmd").toString(),
        GosuLauncher.resolve("", sdk.toString(), true));
  }

  @Test void explicitLauncherOverridesSdkAndEmptySdkUsesPath() {
    assertEquals("/custom/gosu", GosuLauncher.resolve("/custom/gosu", sdk.toString(), false));
    assertEquals("gosu", GosuLauncher.resolve("", "", false));
  }

  @Test void expandsHomeInSdkAndOverride() {
    String home = System.getProperty("user.home");
    assertEquals(Path.of(home, "sdk/bin/gosu").toString(),
        GosuLauncher.resolve("", "~/sdk", false));
    assertEquals(home + "/custom/gosu", GosuLauncher.resolve("~/custom/gosu", "", false));
  }

  @Test void validatesSdkLayoutAndExecutablePermission() throws Exception {
    assertDoesNotThrow(() -> GosuLauncher.validateSdk("", false));
    assertThrows(IllegalArgumentException.class,
        () -> GosuLauncher.validateSdk(sdk.toString(), false));
    Path bin = Files.createDirectory(sdk.resolve("bin"));
    Path launcher = Files.createFile(bin.resolve("gosu"));
    assertThrows(IllegalArgumentException.class,
        () -> GosuLauncher.validateSdk(sdk.toString(), false));
    assertTrue(launcher.toFile().setExecutable(true));
    assertDoesNotThrow(() -> GosuLauncher.validateSdk(sdk.toString(), false));
    Files.createFile(bin.resolve("gosu.cmd"));
    assertDoesNotThrow(() -> GosuLauncher.validateSdk(sdk.toString(), true));
  }

  @Test void rejectsRelativeSdkPaths() {
    assertThrows(IllegalArgumentException.class,
        () -> GosuLauncher.validateSdk("tools/gosu", false));
  }
}
