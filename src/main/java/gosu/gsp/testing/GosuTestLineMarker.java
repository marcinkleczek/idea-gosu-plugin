package gosu.gsp.testing;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import org.jetbrains.annotations.NotNull;
public final class GosuTestLineMarker extends RunLineMarkerContributor {
  @Override public Info getInfo(@NotNull PsiElement element) {
    // Mark name leaves only: stable gutter placement and no duplicate icons.
    if (element.getParent() instanceof PsiNameIdentifierOwner owner && owner.getNameIdentifier() == element
        && GosuTests.testClass(element) != null && (owner instanceof ClassDecl || owner instanceof FunctionDecl fn && GosuTests.isTest(fn)))
      return withExecutorActions(AllIcons.RunConfigurations.TestState.Run);
    return null;
  }
}
