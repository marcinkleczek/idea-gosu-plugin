package gosu.gsp.intentions;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import gosu.gsp.completion.GosuTypeExplanation;
import gosu.gsp.psi.GosuFile;
import org.jetbrains.annotations.NotNull;

public final class GosuExplainTypeIntention extends PsiElementBaseIntentionAction {
  @Override public @NotNull String getText() { return "Explain where the type comes from"; }
  @Override public @NotNull String getFamilyName() { return "Gosu types"; }
  @Override public boolean startInWriteAction() { return false; }
  @Override public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return element.getContainingFile() instanceof GosuFile && !DumbService.isDumb(project) && GosuTypeExplanation.declaration(element) != null;
  }
  @Override public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiElement declaration = GosuTypeExplanation.declaration(element);
    if (declaration != null) Messages.showInfoMessage(project, GosuTypeExplanation.explain(declaration).text(), "Where Does the Type Come From?");
  }
}
