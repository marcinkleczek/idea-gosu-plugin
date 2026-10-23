package gosu.gsp.run;

import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import gosu.gsp.psi.GosuDecls.FunctionDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Green ▶ in the gutter for classes and functions: runs the whole script. */
public final class GosuRunLineMarkerContributor extends RunLineMarkerContributor {

  @Override
  public @Nullable Info getInfo(@NotNull PsiElement element) {
    if (!element.getContainingFile().getName().endsWith(".gsp")) return null;
    if (element instanceof ClassDecl || element instanceof FunctionDecl) {
      return withExecutorActions(AllIcons.RunConfigurations.TestState.Run);
    }
    return null;
  }
}
