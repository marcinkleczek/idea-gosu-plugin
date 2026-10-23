package gosu.gsp.inspection;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import gosu.gsp.psi.GosuRefExpr;
import gosu.gsp.intentions.GosuNullSupport;
import org.jetbrains.annotations.NotNull;

public final class GosuNullDereferenceInspection extends LocalInspectionTool {
  @Override public @NotNull String getShortName() { return "GosuNullDereference"; }
  @Override public @NotNull String getDisplayName() { return "Data flow and null"; }
  @Override public @NotNull String getGroupDisplayName() { return "Gosu"; }
  @Override public boolean isEnabledByDefault() { return true; }
  @Override public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean onTheFly) {
    if (DumbService.isDumb(holder.getProject())) return PsiElementVisitor.EMPTY_VISITOR;
    return new PsiElementVisitor() {
      @Override public void visitFile(@NotNull PsiFile file) {
        for (var finding : GosuDataFlow.analyze(file)) {
          var element = file.findElementAt(finding.start());
          if (element == null) continue;
          boolean nullable = finding.kind() == GosuDataFlow.Kind.NULL_ACCESS || finding.kind() == GosuDataFlow.Kind.MAYBE_NULL_ACCESS;
          var fixes = nullable && GosuNullSupport.access(element) != null ? new LocalQuickFix[]{new SafeCallFix()} : LocalQuickFix.EMPTY_ARRAY;
          holder.registerProblem(element, finding.message(), nullable ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.WEAK_WARNING, fixes);
        }
      }
    };
  }
  private static final class SafeCallFix implements LocalQuickFix {
    @Override public @NotNull String getFamilyName() { return "Handle null: use ?. for the method call"; }
    @Override public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      var element = descriptor.getPsiElement();
      boolean stillNullable = GosuDataFlow.analyze(element.getContainingFile()).stream().anyMatch(f -> f.start() == element.getTextOffset()
          && (f.kind() == GosuDataFlow.Kind.NULL_ACCESS || f.kind() == GosuDataFlow.Kind.MAYBE_NULL_ACCESS));
      if (!stillNullable) return;
      var access = GosuNullSupport.access(element);
      var document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
      if (document != null && access != null) {
        document.insertString(access.dot(), "?");
        PsiDocumentManager.getInstance(project).commitDocument(document);
      }
    }
  }
}
