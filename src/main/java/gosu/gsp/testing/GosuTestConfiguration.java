package gosu.gsp.testing;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.FormBuilder;
import gosu.gsp.settings.*;
import gosu.gsp.run.GosuConsoleFilter;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import java.nio.file.*;
import java.util.*;

public final class GosuTestConfiguration extends LocatableConfigurationBase<GosuTestOptions> {
  public GosuTestConfiguration(Project project, ConfigurationFactory factory) { super(project, factory); }
  @Override protected @NotNull Class<? extends RunConfigurationOptions> getOptionsClass() { return GosuTestOptions.class; }
  public GosuTestOptions values() { return (GosuTestOptions)getOptions(); }
  @Override public @NotNull SettingsEditor<GosuTestConfiguration> getConfigurationEditor() {
    return new SettingsEditor<>() {
      final JTextField cls = new JTextField(), method = new JTextField(), root = new JTextField();
      @Override protected void resetEditorFrom(@NotNull GosuTestConfiguration c) { cls.setText(c.values().className); method.setText(c.values().method); root.setText(c.values().sourceRoot); }
      @Override protected void applyEditorTo(@NotNull GosuTestConfiguration c) { c.values().className = cls.getText().trim(); c.values().method = method.getText().trim(); c.values().sourceRoot = root.getText().trim(); }
      @Override protected @NotNull JComponent createEditor() { return FormBuilder.createFormBuilder()
          .addLabeledComponent("Test class:", cls).addLabeledComponent("Method (empty = all):", method)
          .addLabeledComponent("Source root:", root).addTooltip("Uses Gosu SDK, JDK and Script classpath from the project settings.").getPanel(); }
    };
  }
  @Override public void checkConfiguration() throws RuntimeConfigurationError {
    if (values().className.isBlank()) throw new RuntimeConfigurationError("Select a Gosu test class");
    if (!values().method.isBlank() && !values().method.startsWith("test")) throw new RuntimeConfigurationError("Test method names must start with test");
    if (!Files.isDirectory(Path.of(values().sourceRoot))) throw new RuntimeConfigurationError("Test source root does not exist");
    var settings = GosuProjectSettings.getInstance(getProject());
    if (GosuClasspath.sdkJars(settings.getSdkHome()).isEmpty()) throw new RuntimeConfigurationError("Configure Gosu SDK in Languages & Frameworks | Gosu");
    try { GosuClasspath.validateJdk(javaHome(), SystemInfo.isWindows); GosuClasspath.validate(settings.getClasspath(), getProject().getBasePath()); }
    catch (IllegalArgumentException e) { throw new RuntimeConfigurationError(e.getMessage()); }
  }
  private String javaHome() {
    var settings = GosuProjectSettings.getInstance(getProject());
    if (!settings.getJdkHome().isBlank()) return settings.getJdkHome();
    var sdk = ProjectRootManager.getInstance(getProject()).getProjectSdk();
    return sdk != null && sdk.getHomePath() != null ? sdk.getHomePath() : System.getProperty("java.home");
  }
  public GeneralCommandLine createCommandLine() throws ExecutionException {
    var settings = GosuProjectSettings.getInstance(getProject());
    List<String> jars = new ArrayList<>(GosuClasspath.sdkJars(settings.getSdkHome()));
    try { jars.add(PathManager.getJarPathForClass(Class.forName("gosu.gsp.compilerbridge.GosuTestBridge"))); }
    catch (ClassNotFoundException e) { throw new ExecutionException("Gosu test runner is missing", e); }
    Set<String> roots = new LinkedHashSet<>();
    roots.add(values().sourceRoot);
    roots.addAll(GosuClasspath.entries(settings.getClasspath(), getProject().getBasePath()));
    for (var root : ProjectRootManager.getInstance(getProject()).getContentSourceRoots()) roots.add(root.getPath());
    jars.addAll(roots);
    String jdk = GosuClasspath.validateJdk(javaHome(), SystemInfo.isWindows);
    GeneralCommandLine command = new GeneralCommandLine(Path.of(jdk, "bin", SystemInfo.isWindows ? "java.exe" : "java").toString());
    command.addParameters("-ea", "-cp", String.join(java.io.File.pathSeparator, jars), "gosu.gsp.compilerbridge.GosuTestBridge", values().className, values().method);
    command.addParameters(List.copyOf(roots));
    command.setWorkDirectory(getProject().getBasePath() == null ? values().sourceRoot : getProject().getBasePath());
    return command;
  }
  @Override public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
    return new CommandLineState(environment) {
      @Override protected @NotNull ProcessHandler startProcess() throws ExecutionException { return new KillableColoredProcessHandler(createCommandLine()); }
      @Override public @NotNull ExecutionResult execute(@NotNull Executor executor, @NotNull com.intellij.execution.runners.ProgramRunner<?> runner) throws ExecutionException {
        var process = startProcess();
        var properties = new SMTRunnerConsoleProperties(GosuTestConfiguration.this, "Gosu", executor) {
          @Override public com.intellij.execution.testframework.sm.runner.SMTestLocator getTestLocator() { return new GosuTestLocator(); }
        };
        properties.addStackTraceFilter(new GosuConsoleFilter(getProject(), "", values().sourceRoot));
        var console = SMTestRunnerConnectionUtil.createAndAttachConsole("Gosu", process, properties);
        return new DefaultExecutionResult(console, process, createActions(console, process));
      }
    };
  }
}
