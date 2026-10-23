package gosu.gsp.inspection;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import gosu.gsp.completion.GosuAutoImports;
import gosu.gsp.psi.*;
import gosu.gsp.resolve.GosuMembers;
import org.jetbrains.annotations.NotNull;

/** Offers import choices without inventing unresolved-name errors. The compiler owns diagnostics. */
public final class GosuMissingImportInspection extends LocalInspectionTool {
  @Override public @NotNull String getDisplayName() { return "Import Gosu or Java class"; }
  @Override public @NotNull String getGroupDisplayName() { return "Gosu"; }
  @Override public boolean isEnabledByDefault() { return true; }
  @Override public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean onTheFly) {
    return new PsiElementVisitor() {
      @Override public void visitElement(@NotNull PsiElement element) {
        String name = element instanceof GosuTypeRef t ? t.getName() : element instanceof GosuRefExpr r ? r.getName() : null;
        if (name == null || name.isEmpty() || !Character.isUpperCase(name.charAt(0))
            || GosuMembers.isMemberPosition(element.getContainingFile(), element.getTextOffset())
            || element.getReference() == null || element.getReference().resolve() != null) return;
        var candidates = GosuAutoImports.candidates(element, name::equals);
        if (candidates.isEmpty()) return;
        LocalQuickFix[] fixes = candidates.stream().map(c -> new ImportFix(c.qualifiedName())).toArray(LocalQuickFix[]::new);
        holder.registerProblem(element, "Import class '" + name + "'", ProblemHighlightType.INFORMATION, fixes);
      }
    };
  }

  public static final class ImportFix implements LocalQuickFix {
    private final String qualified;
    public ImportFix(String qualified) { this.qualified = qualified; }
    @Override public @NotNull String getName() { return "Import " + qualified; }
    @Override public @NotNull String getFamilyName() { return "Import Gosu or Java class"; }
    @Override public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (!element.isValid()) return;
      GosuAutoImports.insert(element.getContainingFile(), element.getTextRange().getStartOffset(), element.getTextRange().getEndOffset(), qualified);
    }
  }
}
