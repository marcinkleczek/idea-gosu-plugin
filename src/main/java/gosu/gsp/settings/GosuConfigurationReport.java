package gosu.gsp.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import gosu.gsp.run.GosuLauncher;
import java.util.*;

/** Read-only diagnostic of the values currently entered, including unapplied settings. */
public final class GosuConfigurationReport {
  private GosuConfigurationReport() {}
  public static String describe(Project project, String sdk, String jdk, String classpath) {
    StringBuilder out = new StringBuilder("Gosu configuration (current form values)\n\n");
    out.append("SDK: ").append(sdk.isBlank() ? "not configured; scripts use gosu from PATH" : GosuLauncher.expandHome(sdk)).append('\n');
    validate(out, () -> GosuLauncher.validateSdk(sdk, SystemInfo.isWindows));
    out.append("SDK JARs: ").append(GosuClasspath.sdkJars(sdk).size()).append('\n');
    var projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    String effectiveJdk = jdk;
    if (effectiveJdk.isBlank() && projectSdk != null && projectSdk.getSdkType() instanceof JavaSdk) effectiveJdk = Objects.toString(projectSdk.getHomePath(), "");
    out.append("Project JDK: ").append(projectSdk == null ? "not configured (Java completion unavailable)" : projectSdk.getName()).append('\n');
    out.append("Runtime JDK: ");
    if (effectiveJdk.isBlank()) out.append("inherited launcher environment; compiler checks use IDE Java\n");
    else {
      String selected = effectiveJdk;
      try { out.append(GosuClasspath.validateJdk(selected, SystemInfo.isWindows)).append(" [OK]\n"); }
      catch (IllegalArgumentException ex) { out.append(selected).append("\nERROR: ").append(ex.getMessage()).append('\n'); }
    }
    out.append("\nScript classpath (also indexed):\n");
    try {
      var entries = GosuClasspath.entries(classpath, project.getBasePath());
      if (entries.isEmpty()) out.append("  (empty)\n");
      for (String entry : entries) { out.append("  ").append(entry).append('\n'); validate(out, () -> GosuClasspath.validate(entry, project.getBasePath())); }
    } catch (IllegalArgumentException ex) { out.append("ERROR: ").append(ex.getMessage()).append('\n'); }
    out.append("\nSource roots (compiler checks and tests):\n");
    for (var root : ProjectRootManager.getInstance(project).getContentSourceRoots()) out.append("  ").append(root.getPath()).append('\n');
    out.append("\nLibraries configured only in Project Structure may complete in the editor, but must also be added to Script classpath to run.\n");
    out.append("No program was executed. Apply settings to update indexing and execution.\n");
    return out.toString();
  }
  private static void validate(StringBuilder out, Runnable check) {
    try { check.run(); } catch (IllegalArgumentException ex) { out.append("ERROR: ").append(ex.getMessage()).append('\n'); }
  }
}
