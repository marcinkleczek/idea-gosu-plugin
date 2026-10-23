package gosu.gsp.editor;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import gosu.gsp.psi.GosuDecls.FunctionDecl;
import gosu.gsp.psi.GosuDecls.Param;
import gosu.gsp.psi.GosuDecls.VarDecl;

/**
 * Rename (dialog and in-place for locals) and safe delete.
 * Rename itself works through {@code GosuReference.bindToElement} and
 * {@code setName}; this provider only unlocks the UI entry points.
 */
public final class GosuRefactoringSupportProvider extends RefactoringSupportProvider {

  @Override public com.intellij.refactoring.RefactoringActionHandler getIntroduceVariableHandler() {
    return new gosu.gsp.refactor.GosuExtractHandler();
  }

  @Override public com.intellij.refactoring.RefactoringActionHandler getExtractMethodHandler() {
    return new gosu.gsp.refactor.GosuExtractHandler(true);
  }

  @Override public com.intellij.refactoring.changeSignature.ChangeSignatureHandler getChangeSignatureHandler() { return new gosu.gsp.refactor.GosuChangeSignatureHandler(); }

  private static boolean isNamedDecl(PsiElement element) {
    return element instanceof ClassDecl
        || element instanceof FunctionDecl
        || element instanceof VarDecl
        || element instanceof Param;
  }

  @Override
  public boolean isSafeDeleteAvailable(PsiElement element) {
    return isNamedDecl(element);
  }

  @Override
  public boolean isInplaceRenameAvailable(PsiElement element, PsiElement context) {
    return element instanceof VarDecl || element instanceof Param;
  }

  @Override
  public boolean isMemberInplaceRenameAvailable(PsiElement element, PsiElement context) {
    return element instanceof ClassDecl
        || element instanceof FunctionDecl
        || (element instanceof VarDecl var && var.isMember());
  }
}
