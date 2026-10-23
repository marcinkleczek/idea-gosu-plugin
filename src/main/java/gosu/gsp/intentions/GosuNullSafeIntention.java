package gosu.gsp.intentions;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public final class GosuNullSafeIntention extends PsiElementBaseIntentionAction {
  @Override public @NotNull String getText() { return "Handle null: use ?. for the method call"; }
  @Override public @NotNull String getFamilyName() { return "Gosu null handling"; }
  @Override public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return !DumbService.isDumb(project) && GosuNullSupport.access(element) != null;
  }
  @Override public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    var access = GosuNullSupport.access(element);
    if (access == null) return;
    editor.getDocument().insertString(access.dot(), "?");
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
  }
}
