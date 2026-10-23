package gosu.gsp.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;

public final class GosuLambda extends ASTWrapperPsiElement {
  public GosuLambda(ASTNode node) { super(node); }
}
