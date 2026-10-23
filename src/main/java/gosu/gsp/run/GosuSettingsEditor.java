package gosu.gsp.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/** Script file, gosu launcher, program arguments, working directory. */
final class GosuSettingsEditor extends SettingsEditor<GosuRunConfiguration> {
  private final TextFieldWithBrowseButton scriptField = new TextFieldWithBrowseButton();
  private final TextFieldWithBrowseButton exeField = new TextFieldWithBrowseButton();
  private final JTextField argsField = new JTextField();
  private final TextFieldWithBrowseButton workDirField = new TextFieldWithBrowseButton();
  private final JPanel panel;
  private boolean browsersBound;

  GosuSettingsEditor() {
    panel = FormBuilder.createFormBuilder()
        .addLabeledComponent("Script file:", scriptField)
        .addLabeledComponent("Gosu executable (optional):", exeField)
        .addTooltip("Empty or gosu: use the SDK from Settings | Languages & Frameworks | Gosu, then PATH.")
        .addLabeledComponent("Program arguments:", argsField)
        .addLabeledComponent("Working directory:", workDirField)
        .getPanel();
  }

  private void bindBrowsers(Project project) {
    if (browsersBound) return;
    browsersBound = true;
    scriptField.addBrowseFolderListener(project,
        FileChooserDescriptorFactory.createSingleFileDescriptor());
    exeField.addBrowseFolderListener(project,
        FileChooserDescriptorFactory.createSingleFileDescriptor());
    workDirField.addBrowseFolderListener(project,
        FileChooserDescriptorFactory.createSingleFolderDescriptor());
  }

  @Override
  protected void resetEditorFrom(@NotNull GosuRunConfiguration config) {
    bindBrowsers(config.getProject());
    scriptField.setText(config.getScriptPath());
    exeField.setText(config.getGosuExecutable());
    argsField.setText(config.getProgramArgs());
    workDirField.setText(config.getWorkingDir());
  }

  @Override
  protected void applyEditorTo(@NotNull GosuRunConfiguration config) {
    config.setScriptPath(scriptField.getText().trim());
    config.setGosuExecutable(exeField.getText().trim());
    config.setProgramArgs(argsField.getText().trim());
    config.setWorkingDir(workDirField.getText().trim());
  }

  @Override
  protected @NotNull JComponent createEditor() {
    return panel;
  }
}
