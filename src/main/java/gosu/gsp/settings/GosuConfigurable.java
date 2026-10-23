package gosu.gsp.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.FormBuilder;
import gosu.gsp.run.GosuLauncher;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;

public final class GosuConfigurable implements Configurable {
  private final Project project;
  private TextFieldWithBrowseButton sdkHome;
  private TextFieldWithBrowseButton jdkHome;
  private JTextArea classpath;
  private JCheckBox compilerChecks;

  public GosuConfigurable(Project project) { this.project = project; }

  @Override
  public @Nls String getDisplayName() { return "Gosu"; }

  @Override
  public JComponent createComponent() {
    sdkHome = new TextFieldWithBrowseButton();
    sdkHome.addBrowseFolderListener(project,
        FileChooserDescriptorFactory.createSingleFolderDescriptor());
    jdkHome = new TextFieldWithBrowseButton();
    jdkHome.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    classpath = new JTextArea(6, 50);
    compilerChecks = new JCheckBox("Check compilation in the editor (does not run scripts)");
    JButton add = new JButton("Add directory or JAR…");
    add.addActionListener(event -> {
      var descriptor = new com.intellij.openapi.fileChooser.FileChooserDescriptor(true, true, true, false, false, true);
      for (var file : com.intellij.openapi.fileChooser.FileChooser.chooseFiles(descriptor, project, null)) {
        classpath.append((classpath.getText().isBlank() ? "" : "\n") + file.getPath());
      }
    });
    JButton inspect = new JButton("Review / check configuration…");
    inspect.addActionListener(event -> {
      JTextArea report = new JTextArea(GosuConfigurationReport.describe(project,
          sdkHome.getText().trim(), jdkHome.getText().trim(), classpath.getText()), 22, 85);
      report.setEditable(false);
      report.setCaretPosition(0);
      JOptionPane.showMessageDialog(sdkHome, new JScrollPane(report), "Gosu configuration", JOptionPane.INFORMATION_MESSAGE);
    });
    return FormBuilder.createFormBuilder()
        .addLabeledComponent("Gosu SDK home:", sdkHome)
        .addTooltip("Select the directory containing bin/gosu (bin/gosu.cmd on Windows) and lib.")
        .addTooltip("Used by Gosu Script configurations. Leave empty to use gosu from PATH.")
        .addLabeledComponent("JDK home (optional):", jdkHome)
        .addTooltip("Sets the project JDK and the Java used by Gosu. Empty: use the current project JDK.")
        .addLabeledComponent("Script classpath:", new JScrollPane(classpath))
        .addComponent(add)
        .addTooltip("One JAR or directory per line; relative paths use the project directory. Used for completion and running.")
        .addTooltip("Gosu SDK JARs are indexed automatically. JDK libraries come from the project JDK.")
        .addComponent(compilerChecks)
        .addComponent(inspect)
        .getPanel();
  }

  @Override
  public boolean isModified() {
    return sdkHome != null && (!sdkHome.getText().trim().equals(settings().getSdkHome())
        || !jdkHome.getText().trim().equals(settings().getJdkHome())
        || !classpath.getText().trim().equals(settings().getClasspath())
        || compilerChecks.isSelected() != settings().isCompilerChecks());
  }

  @Override
  public void apply() throws ConfigurationException {
    if (sdkHome == null) return;
    String value = sdkHome.getText().trim();
    String javaHome;
    try {
      javaHome = GosuClasspath.validateJdk(jdkHome.getText().trim(), SystemInfo.isWindows);
      GosuClasspath.validate(classpath.getText(), project.getBasePath());
      GosuLauncher.validateSdk(value, SystemInfo.isWindows);
    } catch (IllegalArgumentException e) {
      throw new ConfigurationException(e.getMessage());
    }
    Sdk sdk = null;
    if (!javaHome.isEmpty()) {
      for (Sdk existing : ProjectJdkTable.getInstance().getAllJdks()) {
        if (existing.getSdkType() instanceof JavaSdk && javaHome.equals(existing.getHomePath())) sdk = existing;
      }
      if (sdk == null) {
        String name = "Gosu JDK (" + javaHome + ")";
        sdk = JavaSdk.getInstance().createJdk(name, javaHome, false);
      }
    }
    final Sdk selectedSdk = sdk;
    LocalFileSystem.getInstance().refresh(false);
    WriteAction.run(() -> {
      if (selectedSdk != null) {
        if (ProjectJdkTable.getInstance().findJdk(selectedSdk.getName()) == null) {
          ProjectJdkTable.getInstance().addJdk(selectedSdk);
        }
        ProjectRootManager.getInstance(project).setProjectSdk(selectedSdk);
      }
      ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(() -> {
        settings().setSdkHome(value);
        settings().setJdkHome(javaHome);
        settings().setClasspath(classpath.getText().trim());
        settings().setCompilerChecks(compilerChecks.isSelected());
      }, false, true);
    });
    jdkHome.setText(javaHome);
    com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project).restart();
  }

  @Override
  public void reset() {
    if (sdkHome != null) {
      sdkHome.setText(settings().getSdkHome());
      jdkHome.setText(settings().getJdkHome());
      classpath.setText(settings().getClasspath());
      compilerChecks.setSelected(settings().isCompilerChecks());
    }
  }

  @Override
  public void disposeUIResources() {
    if (sdkHome != null) sdkHome.dispose();
    if (jdkHome != null) jdkHome.dispose();
    sdkHome = null;
    jdkHome = null;
    classpath = null;
    compilerChecks = null;
  }

  private GosuProjectSettings settings() { return GosuProjectSettings.getInstance(project); }
}
