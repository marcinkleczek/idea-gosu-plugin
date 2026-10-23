package gosu.gsp.intentions;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public final class GosuNullFallbackIntention extends PsiElementBaseIntentionAction {
  @Override public @NotNull String getText() { return "Handle null: add a fallback value (?:)"; }
  @Override public @NotNull String getFamilyName() { return "Gosu null handling"; }
  @Override public boolean startInWriteAction() { return false; }
  @Override public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return !DumbService.isDumb(project) && GosuNullSupport.fallbackTarget(element) != null;
  }
  @Override public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    var ref = GosuNullSupport.fallbackTarget(element);
    if (ref == null) return;
    var range = ref.getTextRange();
    var file = ref.getContainingFile();
    String snapshot = editor.getDocument().getText();
    String value = Messages.showInputDialog(project, "Expression used only when " + ref.getText()
        + " is null. Choose a value compatible with the variable's type:", "Gosu Fallback Value", null, "", new InputValidator() {
      public boolean checkInput(String input) { return GosuNullSupport.validFallback(input); }
      public boolean canClose(String input) { return checkInput(input); }
    });
    if (value == null || !snapshot.equals(editor.getDocument().getText()) || !FileModificationService.getInstance().preparePsiElementForWrite(file)) return;
    String replacement = GosuNullSupport.fallback(ref, value);
    WriteCommandAction.runWriteCommandAction(project, getText(), null, () -> {
      editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), replacement);
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    }, file);
  }
}
