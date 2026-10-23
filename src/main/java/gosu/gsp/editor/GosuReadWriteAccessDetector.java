package gosu.gsp.editor;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.psi.GosuDecls.Param;
import gosu.gsp.psi.GosuDecls.VarDecl;
import gosu.gsp.psi.GosuRefExpr;
import org.jetbrains.annotations.NotNull;

/**
 * Read vs write coloring for highlighted usages: a reference directly
 * followed by a single {@code =} counts as a write, everything else as read.
 */
public final class GosuReadWriteAccessDetector extends ReadWriteAccessDetector {

  @Override
  public boolean isReadWriteAccessible(@NotNull PsiElement element) {
    return element instanceof VarDecl
        || element instanceof Param
        || element instanceof GosuRefExpr;
  }

  @Override
  public boolean isDeclarationWriteAccess(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public Access getReferenceAccess(@NotNull PsiElement element,
      @NotNull PsiReference reference) {
    if (isWriteTarget(reference.getElement())) {
      return Access.Write;
    }
    return Access.Read;
  }

  @Override
  public Access getExpressionAccess(@NotNull PsiElement element) {
    return Access.Read;
  }

  private static boolean isWriteTarget(PsiElement ref) {
    PsiElement next = ref.getNextSibling();
    while (next instanceof PsiWhiteSpace
        || (next != null && next.getNode() != null
        && (next.getNode().getElementType() == GosuTokenTypes.LINE_COMMENT
        || next.getNode().getElementType() == GosuTokenTypes.BLOCK_COMMENT))) {
      next = next.getNextSibling();
    }
    return next != null
        && next.getNode() != null
        && next.getNode().getElementType() == GosuTokenTypes.OPERATOR
        && "=".equals(next.getText());
  }
}
