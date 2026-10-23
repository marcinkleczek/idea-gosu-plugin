package gosu.gsp.run;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import gosu.gsp.settings.GosuProjectSettings;
import gosu.gsp.settings.GosuClasspath;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runs a `.gsp` script with an external `gosu` launcher
 * (e.g. `tools/gosu-1.18.11/bin/gosu`): `gosu file.gsp args`.
 */
public final class GosuRunConfiguration extends LocatableConfigurationBase {

  public GosuRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  protected @NotNull Class<? extends RunConfigurationOptions> getOptionsClass() {
    return GosuRunConfigurationOptions.class;
  }

  private @NotNull GosuRunConfigurationOptions options() {
    return (GosuRunConfigurationOptions) getOptions();
  }

  public @NotNull String getScriptPath() {
    return options().scriptPath;
  }

  public void setScriptPath(@NotNull String path) {
    options().scriptPath = path;
  }

  public @NotNull String getGosuExecutable() {
    return options().gosuExecutable;
  }

  public void setGosuExecutable(@NotNull String exe) {
    options().gosuExecutable = exe;
  }

  public @NotNull String getProgramArgs() {
    return options().programArgs;
  }

  public void setProgramArgs(@NotNull String args) {
    options().programArgs = args;
  }

  public @NotNull String getWorkingDir() {
    return options().workingDir;
  }

  public void setWorkingDir(@NotNull String dir) {
    options().workingDir = dir;
  }

  @Override
  public @NotNull SettingsEditor<GosuRunConfiguration> getConfigurationEditor() {
    return new GosuSettingsEditor();
  }

  @Override
  public @Nullable RunProfileState getState(@NotNull Executor executor,
      @NotNull ExecutionEnvironment environment) {
    GosuRunConfigurationOptions options = options();
    String script = GosuLauncher.expandHome(options.scriptPath);
    String workDir = options.workingDir.isEmpty()
        ? new java.io.File(script).getAbsoluteFile().getParent() : GosuLauncher.expandHome(options.workingDir);
    CommandLineState state = new CommandLineState(environment) {
      @Override
      protected @NotNull ProcessHandler startProcess()
          throws com.intellij.execution.ExecutionException {
        GeneralCommandLine command = createCommandLine();
        return new OSProcessHandler(command);
      }
    };
    state.addConsoleFilters(new GosuConsoleFilter(getProject(), script, workDir));
    return state;
  }

  /** Shared builder so runtime JDK/classpath/argument behavior can be tested directly. */
  public GeneralCommandLine createCommandLine() {
    GosuProjectSettings settings = GosuProjectSettings.getInstance(getProject());
    GeneralCommandLine command = new GeneralCommandLine();
    command.setExePath(GosuLauncher.resolve(getGosuExecutable(), settings.getSdkHome(), SystemInfo.isWindows));
    String javaHome = settings.getJdkHome();
    if (javaHome.isBlank()) {
      Sdk sdk = ProjectRootManager.getInstance(getProject()).getProjectSdk();
      if (sdk != null && sdk.getSdkType() instanceof com.intellij.openapi.projectRoots.JavaSdk && sdk.getHomePath() != null) {
        javaHome = sdk.getHomePath();
      }
    }
    if (!javaHome.isBlank()) {
      javaHome = GosuLauncher.expandHome(javaHome);
      command.withEnvironment("JAVA_HOME", javaHome);
      // The supplied launcher honors JAVACMD before JAVA_HOME. Quote paths with spaces
      // because its Unix implementation expands JAVACMD through eval.
      String javaExecutable = java.nio.file.Path.of(javaHome, "bin", SystemInfo.isWindows ? "java.exe" : "java").toString();
      command.withEnvironment("JAVACMD", SystemInfo.isWindows ? javaExecutable : "'" + javaExecutable.replace("'", "'\"'\"'") + "'");
    }
    var classpath = GosuClasspath.entries(settings.getClasspath(), getProject().getBasePath());
    if (!classpath.isEmpty()) command.addParameters("-classpath", String.join(",", classpath));
    command.addParameter(new java.io.File(GosuLauncher.expandHome(getScriptPath())).getAbsolutePath());
    command.getParametersList().addParametersString(getProgramArgs());
    String work = getWorkingDir().isBlank()
        ? new java.io.File(GosuLauncher.expandHome(getScriptPath())).getAbsoluteFile().getParent()
        : GosuLauncher.expandHome(getWorkingDir());
    if (work != null) command.setWorkDirectory(work);
    return command;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationError {
    GosuRunConfigurationOptions options = options();
    GosuProjectSettings settings = GosuProjectSettings.getInstance(getProject());
    try {
      GosuClasspath.validate(settings.getClasspath(), getProject().getBasePath());
      GosuClasspath.validateJdk(settings.getJdkHome(), SystemInfo.isWindows);
    } catch (IllegalArgumentException e) {
      throw new RuntimeConfigurationError(e.getMessage());
    }
    if (options.scriptPath.isBlank()) {
      throw new RuntimeConfigurationError("Script file is not set");
    }
    if (!new java.io.File(GosuLauncher.expandHome(options.scriptPath)).isFile()) {
      throw new RuntimeConfigurationError("Script file does not exist: " + options.scriptPath);
    }
    if (options.gosuExecutable.isBlank() || options.gosuExecutable.equals("gosu")) {
      try {
        GosuLauncher.validateSdk(GosuProjectSettings.getInstance(getProject()).getSdkHome(),
            SystemInfo.isWindows);
      } catch (IllegalArgumentException e) {
        throw new RuntimeConfigurationError(e.getMessage()
            + " Configure it in Settings | Languages & Frameworks | Gosu.");
      }
    }
  }
}
