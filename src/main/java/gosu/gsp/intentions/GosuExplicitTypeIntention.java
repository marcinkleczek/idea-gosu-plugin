package gosu.gsp.intentions;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import org.jetbrains.annotations.NotNull;

public final class GosuExplicitTypeIntention extends PsiElementBaseIntentionAction {
  @Override public @NotNull String getText() { return "Add explicit variable type"; }
  @Override public @NotNull String getFamilyName() { return "Gosu types"; }
  private VarDecl variable(PsiElement element) { return PsiTreeUtil.getParentOfType(element, VarDecl.class, false); }
  public static String typeText(GosuMembers.Type type) {
    if (type == null) return "";
    if (type.javaType() != null) return type.javaType().getCanonicalText();
    return type.declaration() instanceof ClassDecl cls ? GosuTypes.qualifiedName(cls)
        + (type.bindings().isEmpty() ? "" : "<" + String.join(", ", type.bindings().values()) + ">") : "";
  }
  @Override public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    var variable = variable(element);
    return !DumbService.isDumb(project) && variable != null && variable.getText().startsWith("var ")
        && variable.getNameIdentifier() != null && GosuSignatures.declaredType(variable).isBlank()
        && !typeText(GosuMembers.infer(variable)).isBlank();
  }
  @Override public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    var variable = variable(element);
    if (variable == null || !isAvailable(project, editor, element)) return;
    editor.getDocument().insertString(variable.getNameIdentifier().getTextRange().getEndOffset(), " : " + typeText(GosuMembers.infer(variable)));
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
  }
}
