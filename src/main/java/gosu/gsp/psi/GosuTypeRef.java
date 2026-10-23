package gosu.gsp.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import gosu.gsp.lexer.GosuTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An identifier in type position ({@code new FileContainer()}, {@code : String},
 * {@code extends Base}). Resolves through local declarations, imports and project/library indexes.
 */
public class GosuTypeRef extends ASTWrapperPsiElement {
  public GosuTypeRef(@NotNull ASTNode node) {
    super(node);
  }

  public @Nullable String getName() {
    ASTNode id = getNode().findChildByType(GosuTokenTypes.IDENTIFIER);
    return id == null ? null : id.getText();
  }

  @Override
  public @Nullable PsiReference getReference() {
    return getName() == null ? null : new GosuReference(this);
  }

  @Override
  public String toString() {
    return "GosuTypeRef(" + getName() + ")";
  }
}
