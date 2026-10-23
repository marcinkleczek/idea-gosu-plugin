package gosu.gsp.testing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "gosu.test.sdk", matches = ".+")
class GosuTestBridgeTest {
  @Test void runsTestsReportsFailureAndLifecycleWithoutSkippingRemainingTests() throws Exception {
    Path root = Files.createTempDirectory("gosu tests ");
    try {
      Files.writeString(root.resolve("LessonTest.gs"), """
          class LessonTest {
            var _ready : boolean = false
            function beforeTest() { _ready = true }
            function afterTest() { print("cleanup") }
            function testA() { if (not _ready) throw new RuntimeException("setup missing") }
            function testB() { throw new RuntimeException("expected failure") }
            function testC() { print("##teamcity[testFailed name='fake']") }
          }
          """);
      Result all = run(root, "");
      assertEquals(1, all.code(), all.output());
      assertTrue(all.output().contains("testFailed name='testB'"), all.output());
      assertFalse(all.output().contains("testFailed name='testA'"), all.output());
      assertTrue(all.output().contains("testFinished name='testC'"), all.output());
      assertTrue(all.output().contains("testStdOut name='testC'"), all.output());
      assertTrue(all.output().contains("cleanup"), all.output());
      Result one = run(root, "testA");
      assertEquals(0, one.code(), one.output());
      assertFalse(one.output().contains("testStarted name='testB'"), one.output());
      assertTrue(one.output().contains("gosutest://LessonTest/testA"));
      Result missing = run(root, "testMissing");
      assertEquals(1, missing.code());
      assertTrue(missing.output().contains("Test configuration"));
    } finally { try (var files = Files.walk(root)) { for (var path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); } }
  }
  private record Result(int code, String output) {}
  private Result run(Path root, String method) throws Exception {
    List<String> cp = new ArrayList<>(gosu.gsp.settings.GosuClasspath.sdkJars(System.getProperty("gosu.test.sdk")));
    cp.add(com.intellij.openapi.application.PathManager.getJarPathForClass(Class.forName("gosu.gsp.compilerbridge.GosuTestBridge")));
    Path log = root.resolve("output.txt");
    Process process = new ProcessBuilder(Path.of(System.getProperty("gosu.test.jdk"), "bin", "java").toString(), "-ea", "-cp", String.join(java.io.File.pathSeparator, cp),
        "gosu.gsp.compilerbridge.GosuTestBridge", "LessonTest", method, root.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try { assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Test process timed out"); return new Result(process.exitValue(), Files.readString(log)); }
    finally { process.destroyForcibly(); }
  }
}
