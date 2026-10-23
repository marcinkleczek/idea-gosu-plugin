package gosu.gsp.testing;

import com.intellij.execution.actions.*;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;

public final class GosuTestProducer extends RunConfigurationProducer<GosuTestConfiguration> {
  public GosuTestProducer() { super((ConfigurationType)ConfigurationTypeUtil.findConfigurationType(GosuTestConfigurationType.class)); }
  @Override protected boolean setupConfigurationFromContext(@NotNull GosuTestConfiguration config, @NotNull ConfigurationContext context, @NotNull Ref<PsiElement> sourceElement) {
    PsiElement location = context.getPsiLocation();
    ClassDecl cls = location == null ? null : GosuTests.testClass(location);
    if (cls == null || cls.getContainingFile().getVirtualFile() == null) return false;
    FunctionDecl fn = PsiTreeUtil.getParentOfType(location, FunctionDecl.class, false);
    config.values().className = GosuTypes.qualifiedName(cls);
    config.values().method = fn != null && GosuTests.isTest(fn) ? fn.getName() : "";
    Path root = Path.of(cls.getContainingFile().getVirtualFile().getPath()).getParent();
    String pkg = GosuSource.packageName(cls.getContainingFile());
    if (!pkg.isBlank()) for (String part : pkg.split("\\.")) { if (root != null) root = root.getParent(); }
    if (root == null) return false;
    config.values().sourceRoot = root.toString();
    config.setName(cls.getName() + (config.values().method.isEmpty() ? "" : "." + config.values().method));
    sourceElement.set(fn != null && GosuTests.isTest(fn) ? fn : cls);
    return true;
  }
  @Override public boolean isConfigurationFromContext(@NotNull GosuTestConfiguration config, @NotNull ConfigurationContext context) {
    PsiElement location = context.getPsiLocation();
    ClassDecl cls = location == null ? null : GosuTests.testClass(location);
    if (cls == null || !config.values().className.equals(GosuTypes.qualifiedName(cls))) return false;
    FunctionDecl fn = PsiTreeUtil.getParentOfType(location, FunctionDecl.class, false);
    return config.values().method.equals(fn != null && GosuTests.isTest(fn) ? fn.getName() : "");
  }
}
