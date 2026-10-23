package gosu.gsp.compiler;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import gosu.gsp.settings.GosuClasspath;
import gosu.gsp.run.GosuLauncher;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Cancellable, bounded compiler worker. Every check uses an isolated snapshot directory. */
public final class GosuCompilerRunner {
  private GosuCompilerRunner() {}
  public record Input(String source, String fileName, String packageName, String sdkHome,
                      String javaHome, List<String> classpath) {}
  public record Diagnostic(String severity, int line, int column, String message) {}
  public record Result(List<Diagnostic> diagnostics, String failure) {}

  public static Result check(Input input) {
    Path snapshot = null;
    try {
      String home = GosuClasspath.validateJdk(input.javaHome(), SystemInfo.isWindows);
      if (home.isBlank()) return new Result(List.of(), "Select a JDK in Settings | Languages & Frameworks | Gosu to check compilation.");
      GosuLauncher.validateSdk(input.sdkHome(), SystemInfo.isWindows);
      List<String> sdkJars = GosuClasspath.sdkJars(input.sdkHome());
      if (sdkJars.isEmpty()) return new Result(List.of(), "The selected Gosu SDK contains no compiler JARs.");
      snapshot = Files.createTempDirectory("gosu-check-");
      Path source = snapshot.resolve(input.packageName().replace('.', '/')).resolve(input.fileName()).normalize();
      if (!source.startsWith(snapshot)) throw new IllegalArgumentException("Invalid source package");
      Files.createDirectories(source.getParent());
      Files.writeString(source, input.source(), StandardCharsets.UTF_8);
      String className = input.fileName().replaceFirst("\\.[^.]+$", "");
      if (!input.packageName().isBlank()) className = input.packageName() + "." + className;
      String bridge = PathManager.getJarPathForClass(Class.forName("gosu.gsp.compilerbridge.GosuCompilerBridge"));
      List<String> javaClasspath = new ArrayList<>(sdkJars);
      javaClasspath.add(bridge);
      GeneralCommandLine command = new GeneralCommandLine(Path.of(home, "bin", SystemInfo.isWindows ? "java.exe" : "java").toString());
      command.withCharset(StandardCharsets.UTF_8);
      command.addParameters("-Dfile.encoding=UTF-8", "-Xmx256m", "-cp", String.join(File.pathSeparator, javaClasspath),
          "gosu.gsp.compilerbridge.GosuCompilerBridge", source.toString(), className, snapshot.toString());
      command.addParameters(input.classpath());
      CapturingProcessHandler handler = new CapturingProcessHandler(command);
      try {
        var indicator = com.intellij.openapi.application.ApplicationManager.getApplication() == null
            ? null : ProgressManager.getInstance().getProgressIndicator();
        var output = indicator == null ? handler.runProcess(20_000, true)
            : handler.runProcessWithProgressIndicator(indicator, 20_000, true);
        ProgressManager.checkCanceled();
        if (output.isTimeout()) return new Result(List.of(), "Gosu compiler check timed out (20 seconds).");
        Result result = parseOutput(output.getStdout());
        if (result.failure() != null && !output.getStderr().isBlank()) {
          return new Result(result.diagnostics(), result.failure() + " " + output.getStderr().lines().findFirst().orElse(""));
        }
        return result;
      } finally { if (!handler.isProcessTerminated()) handler.destroyProcess(); }
    } catch (com.intellij.openapi.progress.ProcessCanceledException canceled) {
      throw canceled;
    } catch (Exception failure) {
      return new Result(List.of(), "Cannot check Gosu compilation: " + failure.getMessage());
    } finally {
      if (snapshot != null) FileUtil.delete(snapshot.toFile());
    }
  }

  public static Result parseOutput(String output) {
    List<Diagnostic> diagnostics = new ArrayList<>();
    boolean complete = false;
    String failure = null;
    for (String line : output.split("\\R")) {
      try {
        if (line.equals("GOSU_DONE")) complete = true;
        if (line.startsWith("GOSU_FAILURE\t")) failure = decode(line.substring(13));
        if (line.startsWith("GOSU_DIAG\t")) {
          String[] parts = line.split("\t", 5);
          int row = Integer.parseInt(parts[2]), col = Integer.parseInt(parts[3]);
          if (row > 0 && col > 0) diagnostics.add(new Diagnostic(parts[1], row - 1, col - 1, decode(parts[4])));
        }
      } catch (IllegalArgumentException malformed) { failure = "Invalid diagnostic from the Gosu SDK"; }
    }
    if (!complete && failure == null) failure = "Gosu compiler did not finish. Check the SDK and JDK settings.";
    return new Result(List.copyOf(diagnostics), failure);
  }
  private static String decode(String text) { return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8); }
}
