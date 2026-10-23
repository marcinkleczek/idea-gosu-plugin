package gosu.gsp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import gosu.gsp.psi.GosuDecls.Param;
import gosu.gsp.psi.GosuDecls.VarDecl;
import org.jetbrains.annotations.NotNull;

/**
 * Unused local variable / parameter. Members are skipped (usable from other
 * files); functions and classes are skipped (entry points / API surface).
 */
public final class GosuUnusedVariableInspection extends LocalInspectionTool {
  @Override
  public @NotNull String getShortName() {
    return "GosuUnusedVariable";
  }

  @Override
  public @NotNull String getDisplayName() {
    return "Unused variable or parameter";
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
        if (element instanceof VarDecl decl) {
          checkVar(decl);
        }
        else if (element instanceof Param param) {
          checkParam(param);
        }
        super.visitElement(element);
      }

      private void checkVar(VarDecl decl) {
        if (decl.getName() == null || decl.getNameIdentifier() == null || decl.isMember()) {
          return;
        }
        if (GosuInspectionUtil.hasResolvingReference(decl)) {
          return;
        }
        TextRange line = GosuInspectionUtil.safeLineRange(decl);
        if (line != null) {
          holder.registerProblem(decl.getNameIdentifier(), "Unused variable ''" + decl.getName() + "''",
              ProblemHighlightType.LIKE_UNUSED_SYMBOL, new RemoveLineFix(line));
        }
        else {
          holder.registerProblem(decl.getNameIdentifier(), "Unused variable ''" + decl.getName() + "''",
              ProblemHighlightType.LIKE_UNUSED_SYMBOL);
        }
      }

      private void checkParam(Param param) {
        if (param.getName() == null || param.getNameIdentifier() == null) {
          return;
        }
        if (GosuInspectionUtil.hasResolvingReference(param)) {
          return;
        }
        holder.registerProblem(param.getNameIdentifier(), "Unused parameter ''" + param.getName() + "''",
            ProblemHighlightType.WEAK_WARNING);
      }
    };
  }

  /** Deletes the whole line when it holds nothing but this statement. */
  static final class RemoveLineFix implements LocalQuickFix {
    private final TextRange range;

    RemoveLineFix(TextRange range) {
      this.range = range;
    }

    @Override
    public @NotNull String getName() {
      return "Remove unused declaration";
    }

    @Override
    public @NotNull String getFamilyName() {
      return "Gosu";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiFile file = descriptor.getPsiElement().getContainingFile();
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document == null || range.getEndOffset() > document.getTextLength()) {
        return;
      }
      WriteAction.run(() -> document.deleteString(range.getStartOffset(), range.getEndOffset()));
    }
  }
}
