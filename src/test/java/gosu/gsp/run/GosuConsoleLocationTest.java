package gosu.gsp.run;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GosuConsoleLocationTest {
  @Test void parsesActualGosuDiagnostics() {
    String line = "You must explicitly coerce int to String. [line:1 col:18] in\n";
    var result = GosuConsoleLocation.parse(line, "learn.gsp");
    assertEquals("learn.gsp", result.file());
    assertEquals(0, result.line());
    assertEquals(17, result.column());
    assertEquals("[line:1 col:18]", line.substring(result.start(), result.end()));
    assertEquals("learn.gsp", GosuConsoleLocation.diagnosticFile("gw.lang.parser.exceptions.ParseResultsException: learn.gsp\n"));
  }

  @Test void parsesStackAndAbsolutePathsWithSpaces() {
    var stack = GosuConsoleLocation.parse("\tat demo.Foo.run(Foo.gs:42)\n", null);
    assertEquals("Foo.gs", stack.file());
    assertEquals(41, stack.line());
    var path = GosuConsoleLocation.parse("/tmp/my project/learn.gsp:12:3\n", null);
    assertEquals("/tmp/my project/learn.gsp", path.file());
    assertEquals(2, path.column());
    assertEquals("C:\\project\\demo.gs", GosuConsoleLocation.parse("C:\\project\\demo.gs:8", null).file());
  }

  @Test void ignoresUnrelatedOrInvalidLines() {
    assertNull(GosuConsoleLocation.parse("line 1: print(1)", "learn.gsp"));
    assertNull(GosuConsoleLocation.parse("[line:1 col:2]", null));
    assertNull(GosuConsoleLocation.parse("(Foo.gs:0)", null));
    assertNull(GosuConsoleLocation.parse("(Foo.gs:99999999999999999999)", null));
    assertNull(GosuConsoleLocation.parse("(Thread.java:12)", null));
  }
}
