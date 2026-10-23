package gosu.gsp.refactor;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.move.*;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.GosuTypes;

public final class GosuMoveHandler extends MoveHandlerDelegate {
  private static PsiElement declaration(PsiElement element) {
    if (element instanceof GosuFile file) return PsiTreeUtil.getChildOfType(file, ClassDecl.class);
    var ref = PsiTreeUtil.getParentOfType(element, GosuRefExpr.class, false);
    if (ref != null && ref.getReference() != null) element = ref.getReference().resolve();
    if (element == null) return null;
    var fn = PsiTreeUtil.getParentOfType(element, FunctionDecl.class, false);
    return fn == null ? PsiTreeUtil.getParentOfType(element, ClassDecl.class, false) : fn;
  }
  @Override public boolean canMove(PsiElement[] elements, PsiElement targetContainer) { return elements.length == 1 && declaration(elements[0]) != null; }
  @Override public boolean tryToMove(PsiElement element, Project project, DataContext context, PsiReference reference, Editor editor) {
    var target = declaration(element);
    if (target == null) return false;
    move(project, target); return true;
  }
  @Override public void doMove(Project project, PsiElement[] elements, PsiElement target, MoveCallback callback) {
    if (elements.length == 1) move(project, declaration(elements[0]));
  }
  private static void move(Project project, PsiElement element) {
    if (DumbService.isDumb(project) || element == null) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    boolean isClass = element instanceof ClassDecl;
    String destination = Messages.showInputDialog(project, isClass ? "Destination package (for example app.services):" : "Fully qualified name of the destination class in the same package. Static methods are supported:",
        isClass ? "Move Class (Gosu)" : "Move Method (Gosu)", Messages.getQuestionIcon());
    if (destination == null) return;
    try {
      if (isClass) {
        var plan = GosuMove.classPlan((ClassDecl) element, destination.strip());
        if (Messages.showOkCancelDialog(project, "Move " + ((ClassDecl) element).getName() + " to " + destination + "\n\nFiles to change:\n" + plan.edits().summary(), "Move Class (Gosu)", Messages.getQuestionIcon()) == Messages.OK) plan.apply();
      } else {
        var target = GosuTypes.find(destination.strip(), element);
        if (!(target instanceof ClassDecl cls)) throw new IllegalArgumentException("The destination Gosu class was not found.");
        var plan = GosuMove.methodPlan((FunctionDecl) element, cls);
        if (Messages.showOkCancelDialog(project, "Move the static method to " + destination + "\n\nFiles to change:\n" + plan.summary(), "Move Method (Gosu)", Messages.getQuestionIcon()) == Messages.OK) plan.apply("Move Method (Gosu)");
      }
    } catch (IllegalArgumentException | com.intellij.util.IncorrectOperationException failure) { Messages.showErrorDialog(project, failure.getMessage(), "Move (Gosu)"); }
  }
}
