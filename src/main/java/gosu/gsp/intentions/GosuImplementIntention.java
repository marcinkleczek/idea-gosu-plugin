package gosu.gsp.intentions;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import org.jetbrains.annotations.NotNull;

public final class GosuImplementIntention extends PsiElementBaseIntentionAction {
  @Override public @NotNull String getText() { return "Implement all missing methods"; }
  @Override public @NotNull String getFamilyName() { return "Gosu methods"; }
  @Override public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    ClassDecl cls = PsiTreeUtil.getParentOfType(element, ClassDecl.class, false);
    return !DumbService.isDumb(project) && cls != null && GosuOverrideMethods.insertionOffset(cls) >= 0
        && GosuOverrideMethods.candidates(cls).stream().anyMatch(GosuOverrideMethods.Candidate::required);
  }
  @Override public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    ClassDecl cls = PsiTreeUtil.getParentOfType(element, ClassDecl.class, false);
    if (cls == null) return;
    int offset = GosuOverrideMethods.insertionOffset(cls);
    if (offset < 0) return;
    String source = GosuOverrideMethods.candidates(cls).stream().filter(GosuOverrideMethods.Candidate::required)
        .map(GosuOverrideMethods.Candidate::source).reduce("", String::concat);
    editor.getDocument().insertString(offset, source);
    editor.getCaretModel().moveToOffset(offset);
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
  }
}
