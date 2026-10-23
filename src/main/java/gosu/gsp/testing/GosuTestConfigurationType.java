package gosu.gsp.testing;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

public final class GosuTestConfigurationType extends SimpleConfigurationType {
  public GosuTestConfigurationType() { super("GosuTests", "Gosu Tests", "Run plain Gosu test classes", NotNullLazyValue.createValue(() -> AllIcons.RunConfigurations.Junit)); }
  @Override public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) { return new GosuTestConfiguration(project, this); }
}
