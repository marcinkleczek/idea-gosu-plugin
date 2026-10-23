package gosu.gsp.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import gosu.gsp.lexer.GosuTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Base for named declarations: classes, functions, variables, parameters. */
public abstract class GosuNamedDecl extends ASTWrapperPsiElement implements PsiNameIdentifierOwner {
  protected GosuNamedDecl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable String getName() {
    PsiElement id = getNameIdentifier();
    return id == null ? null : id.getText();
  }

  @Override
  public @Nullable PsiElement getNameIdentifier() {
    ASTNode id = getNode().findChildByType(GosuTokenTypes.IDENTIFIER);
    return id == null ? null : id.getPsi();
  }

  @Override
  public PsiElement setName(@NotNull String name) {
    PsiElement id = getNameIdentifier();
    if (id != null) {
      id.replace(GosuElementFactory.createIdentifier(getProject(), name));
    }
    return this;
  }
}
