package gosu.gsp.run;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import gosu.gsp.GosuFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * Run Configuration type for `.gsp` scripts.
 * A {@link SimpleConfigurationType} is its own single factory.
 */
public final class GosuRunConfigurationType extends SimpleConfigurationType {

  static final String ID = "GosuScriptRunConfiguration";

  public GosuRunConfigurationType() {
    super(ID, "Gosu Script", "Run a Gosu (.gsp) script with the gosu launcher",
        new NotNullLazyValue<Icon>() {
          @Override
          protected @NotNull Icon compute() {
            return GosuFileType.INSTANCE.getIcon();
          }
        });
  }

  @Override
  public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new GosuRunConfiguration(project, this);
  }

  @Override
  public @NotNull String getHelpTopic() {
    return "reference.run.gosu";
  }
}
