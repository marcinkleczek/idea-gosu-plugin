package gosu.gsp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuFile;
import gosu.gsp.psi.GosuRefExpr;
import gosu.gsp.psi.GosuTypeRef;
import gosu.gsp.psi.GosuUsesDecl;
import org.jetbrains.annotations.NotNull;

/** `uses a.b.C` whose last segment never appears as a name in the file. */
public final class GosuUnusedUsesInspection extends LocalInspectionTool {
  @Override
  public @NotNull String getShortName() {
    return "GosuUnusedUses";
  }

  @Override
  public @NotNull String getDisplayName() {
    return "Unused uses import";
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return "Gosu";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
      boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof GosuUsesDecl uses) {
          if (gosu.gsp.completion.GosuAutoImports.preserveImport(uses)) return;
          String qualified = uses.getQualifiedName();
          int dot = qualified.lastIndexOf('.');
          String last = dot < 0 ? qualified : qualified.substring(dot + 1);
          if (!last.isEmpty() && !isNameUsed(uses, last)) {
            TextRange line = GosuInspectionUtil.safeLineRange(uses);
            if (line != null) {
              holder.registerProblem(uses, "Unused import '" + qualified + "'",
                  ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                  new GosuUnusedVariableInspection.RemoveLineFix(line));
            }
            else {
              holder.registerProblem(uses, "Unused import '" + qualified + "'",
                  ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            }
          }
        }
        super.visitElement(element);
      }

      private boolean isNameUsed(GosuUsesDecl uses, String name) {
        if (!(uses.getContainingFile() instanceof GosuFile file)) {
          return true;
        }
        for (GosuRefExpr ref : PsiTreeUtil.findChildrenOfType(file, GosuRefExpr.class)) {
          if (name.equals(ref.getName())) {
            return true;
          }
        }
        for (GosuTypeRef ref : PsiTreeUtil.findChildrenOfType(file, GosuTypeRef.class)) {
          if (name.equals(ref.getName())) {
            return true;
          }
        }
        return false;
      }
    };
  }
}
