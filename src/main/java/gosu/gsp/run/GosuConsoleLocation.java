package gosu.gsp.run;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses launcher diagnostics without confusing line numbers in source excerpts. */
public final class GosuConsoleLocation {
  private GosuConsoleLocation() {}
  public record Location(String file, int line, int column, int start, int end) {}
  private static final Pattern STACK = Pattern.compile("\\(([^()]+\\.(?:gsp|gsx|gs)):(\\d+)\\)");
  private static final Pattern POSITION = Pattern.compile("\\[line:\\s*(\\d+)\\s+col:\\s*(\\d+)\\]");
  private static final Pattern SUMMARY = Pattern.compile("Line Number:\\s*(\\d+)\\s+Column:\\s*(\\d+)");
  private static final Pattern PATH = Pattern.compile("((?:[A-Za-z]:)?[^\\r\\n]*?\\.(?:gsp|gsx|gs)):(\\d+)(?::(\\d+))?");

  public static String diagnosticFile(String line) {
    String marker = "ParseResultsException:";
    int start = line.indexOf(marker);
    return start < 0 ? null : line.substring(start + marker.length()).trim();
  }

  public static Location parse(String text, String diagnosticFile) {
    Matcher stack = STACK.matcher(text);
    if (stack.find()) return location(stack.group(1), stack.group(2), "1", stack.start(1), stack.end(2));
    Matcher path = PATH.matcher(text);
    if (path.find()) return location(path.group(1).trim(), path.group(2), path.group(3), path.start(1), path.end());
    if (diagnosticFile == null) return null;
    Matcher position = POSITION.matcher(text);
    if (!position.find()) {
      position = SUMMARY.matcher(text);
      if (!position.find()) return null;
    }
    return location(diagnosticFile, position.group(1), position.group(2), position.start(), position.end());
  }

  private static Location location(String file, String line, String column, int start, int end) {
    try {
      int l = Integer.parseInt(line), c = column == null ? 1 : Integer.parseInt(column);
      return l > 0 && c > 0 ? new Location(file, l - 1, c - 1, start, end) : null;
    } catch (NumberFormatException e) { return null; }
  }
}
