package gosu.gsp.run;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import gosu.gsp.psi.GosuFile;
import org.jetbrains.annotations.NotNull;

/** Creates a file-based run configuration from a `.gsp` context (gutter, menu). */
public final class GosuRunConfigurationProducer
    extends RunConfigurationProducer<GosuRunConfiguration> {

  public GosuRunConfigurationProducer() {
    super((ConfigurationType) ConfigurationTypeUtil.findConfigurationType(
        GosuRunConfigurationType.class));
  }

  @Override
  public boolean setupConfigurationFromContext(@NotNull GosuRunConfiguration config,
      @NotNull ConfigurationContext context, @NotNull Ref<PsiElement> sourceElement) {
    VirtualFile file = contextFile(context);
    if (file == null) {
      return false;
    }
    config.setScriptPath(file.getPath());
    config.setName(file.getNameWithoutExtension());
    PsiElement location = context.getPsiLocation();
    if (location != null) {
      sourceElement.set(location);
    }
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull GosuRunConfiguration config,
      @NotNull ConfigurationContext context) {
    VirtualFile file = contextFile(context);
    return file != null && file.getPath().equals(config.getScriptPath());
  }

  private static VirtualFile contextFile(ConfigurationContext context) {
    PsiElement location = context.getPsiLocation();
    if (location == null || !(location.getContainingFile() instanceof GosuFile)) {
      return null;
    }
    VirtualFile file = location.getContainingFile().getVirtualFile();
    return file != null && "gsp".equalsIgnoreCase(file.getExtension()) ? file : null;
  }
}
