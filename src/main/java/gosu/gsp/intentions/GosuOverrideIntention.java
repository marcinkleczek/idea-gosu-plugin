package gosu.gsp.intentions;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public final class GosuOverrideIntention extends PsiElementBaseIntentionAction {
  @Override public @NotNull String getText() { return "Implement / override methods…"; }
  @Override public @NotNull String getFamilyName() { return "Gosu methods"; }
  @Override public boolean startInWriteAction() { return false; }
  @Override public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    ClassDecl cls = PsiTreeUtil.getParentOfType(element, ClassDecl.class, false);
    return !DumbService.isDumb(project) && cls != null && GosuOverrideMethods.insertionOffset(cls) >= 0 && !GosuOverrideMethods.candidates(cls).isEmpty();
  }
  @Override public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    ClassDecl cls = PsiTreeUtil.getParentOfType(element, ClassDecl.class, false);
    if (cls == null) return;
    var pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(cls);
    var candidates = GosuOverrideMethods.candidates(cls);
    JBPopupFactory.getInstance().createPopupChooserBuilder(candidates).setTitle("Implement / override method")
        .setItemChosenCallback(candidate -> WriteCommandAction.runWriteCommandAction(project, () -> {
          var current = pointer.getElement();
          if (current == null) return;
          int offset = GosuOverrideMethods.insertionOffset(current);
          if (offset < 0 || GosuOverrideMethods.candidates(current).stream().noneMatch(c -> c.key().equals(candidate.key()))) return;
          editor.getDocument().insertString(offset, candidate.source());
          editor.getCaretModel().moveToOffset(offset + candidate.source().indexOf("throw"));
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        })).createPopup().showInBestPositionFor(editor);
  }
}
