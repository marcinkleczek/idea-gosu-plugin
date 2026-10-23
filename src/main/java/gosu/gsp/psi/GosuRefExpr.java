package gosu.gsp.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import gosu.gsp.lexer.GosuTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A plain identifier use: variable, call name, qualifier, ... */
public class GosuRefExpr extends ASTWrapperPsiElement {
  public GosuRefExpr(@NotNull ASTNode node) {
    super(node);
  }

  public @Nullable String getName() {
    ASTNode id = getNode().findChildByType(GosuTokenTypes.IDENTIFIER);
    if (id == null) id = getNode().findChildByType(GosuTokenTypes.KEYWORD);
    return id == null ? null : id.getText();
  }

  @Override
  public @Nullable PsiReference getReference() {
    return getName() == null ? null : new GosuReference(this);
  }

  @Override
  public String toString() {
    return "GosuRefExpr(" + getName() + ")";
  }
}
