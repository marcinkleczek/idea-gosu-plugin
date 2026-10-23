package gosu.gsp.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GosuCompilerTest {
  @Test void protocolDistinguishesCompilerErrorsAndWorkerFailures() {
    String message = Base64.getEncoder().encodeToString("Type mismatch".getBytes(StandardCharsets.UTF_8));
    var result = GosuCompilerRunner.parseOutput("launcher chatter\nGOSU_DIAG\tERROR\t2\t5\t" + message + "\nGOSU_DONE\n");
    assertNull(result.failure());
    assertEquals(new GosuCompilerRunner.Diagnostic("ERROR", 1, 4, "Type mismatch"), result.diagnostics().getFirst());
    assertNotNull(GosuCompilerRunner.parseOutput("broken worker").failure());
    assertNotNull(GosuCompilerRunner.parseOutput("GOSU_DIAG\tERROR\tx\ty\t!\nGOSU_DONE").failure());
  }

  @Test void explanationsEscapeCompilerTextAndKeepUnknownMessages() {
    String unknown = "Unexpected <Thing> & <script>alert(1)</script>";
    assertNull(GosuDiagnosticExplanation.explain(unknown));
    String tooltip = GosuDiagnosticExplanation.tooltip(unknown);
    assertTrue(tooltip.contains("&lt;Thing&gt; &amp; &lt;script&gt;"));
    assertFalse(tooltip.contains("<script>"));
    assertTrue(tooltip.contains("No detailed explanation"));
    assertNull(GosuDiagnosticExplanation.explain("Custom error mentioning Missing return statement."));
    String known = GosuDiagnosticExplanation.tooltip("\"List<Missing>\" is not a valid type.");
    assertTrue(known.contains("List&lt;Missing&gt;"));
    assertTrue(known.contains("What does it mean?"));
    assertTrue(known.contains("ArrayList&lt;String&gt;"));
  }

  @Test @EnabledIfSystemProperty(named = "gosu.test.sdk", matches = ".+")
  void explanationsRecognizeRealSdkDiagnostics() {
    Map<String, String> examples = new LinkedHashMap<>();
    examples.put("var message : String = 123", "jawnej konwersji");
    examples.put("print(missingName)", "current scope");
    examples.put("var value : MissingType", "wskazanego typu");
    examples.put("function foo(x : String) {}\nfoo()", "Liczba przekazanych");
    examples.put("function foo() : String {}", "path returns");
    examples.put("var x = \"abc\"\nx.length = 2", "nie pozwala na zapis");
    examples.put("var x = \"abc\"\nx.noSuchMethod()", "matching method");
    for (var entry : examples.entrySet()) {
      var result = check("Bad.gsp", "", entry.getKey());
      assertNull(result.failure(), result.failure());
      assertTrue(result.diagnostics().stream().anyMatch(issue -> {
        var hint = GosuDiagnosticExplanation.explain(issue.message());
        return hint != null && hint.cause().contains(entry.getValue());
      }), entry.getKey() + " -> " + result.diagnostics());
    }
    assertNotNull(GosuDiagnosticExplanation.explain("The type \"int\" cannot be converted to \"void\""));
  }

  private GosuCompilerRunner.Result check(String file, String pkg, String source) {
    return GosuCompilerRunner.check(new GosuCompilerRunner.Input(source, file, pkg,
        System.getProperty("gosu.test.sdk"), System.getProperty("gosu.test.jdk"), List.of()));
  }

  @Test @EnabledIfSystemProperty(named = "gosu.test.sdk", matches = ".+")
  void sdkReportsErrorsForUnsavedSnapshot() {
    var bad = check("Bad.gsp", "", "var message : String = 123\n");
    assertNull(bad.failure(), bad.failure());
    assertFalse(bad.diagnostics().isEmpty());
    assertEquals(0, bad.diagnostics().getFirst().line());
    assertFalse(bad.diagnostics().getFirst().message().isBlank());
    var fixed = check("Bad.gsp", "", "var message : String = \"fixed\"\n");
    assertNull(fixed.failure(), fixed.failure());
    assertTrue(fixed.diagnostics().isEmpty(), fixed.diagnostics().toString());
  }

  @Test @EnabledIfSystemProperty(named = "gosu.test.sdk", matches = ".+")
  void checkingDoesNotExecuteScript() throws Exception {
    Path dir = Files.createTempDirectory("gosu-must-not-run-");
    Path marker = dir.resolve("side-effect.txt");
    try {
      String path = marker.toString().replace("\\", "\\\\").replace("\"", "\\\"");
      var result = check("NoRun.gsp", "", "uses java.nio.file.Files\nuses java.nio.file.Path\nFiles.writeString(Path.of(\"" + path + "\"), \"executed\")\nthrow new RuntimeException(\"Do not run\")\n");
      assertNull(result.failure(), result.failure());
      assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
      assertFalse(Files.exists(marker), "Compiler check must never execute the program");
    } finally { Files.deleteIfExists(marker); Files.delete(dir); }
  }

  @Test @EnabledIfSystemProperty(named = "gosu.test.sdk", matches = ".+")
  void checksClassesAndEnhancements() {
    var cls = check("Lesson.gs", "learning", "package learning\nclass Lesson { function text() : String { return 1 } }");
    assertNull(cls.failure(), cls.failure());
    assertFalse(cls.diagnostics().isEmpty());
    var enhancement = check("TextLesson.gsx", "learning", "package learning\nenhancement TextLesson : String { function greeting() : String { return this } }");
    assertNull(enhancement.failure(), enhancement.failure());
    assertTrue(enhancement.diagnostics().isEmpty(), enhancement.diagnostics().toString());
  }
}
