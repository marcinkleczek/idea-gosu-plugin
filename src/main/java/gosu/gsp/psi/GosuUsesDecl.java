package gosu.gsp.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange;
import gosu.gsp.resolve.GosuTypes;
import org.jetbrains.annotations.NotNull;

/** {@code uses a.b.C}: qualified name, last segment is the imported type. */
public class GosuUsesDecl extends ASTWrapperPsiElement {
  public GosuUsesDecl(@NotNull ASTNode node) {
    super(node);
  }

  public @NotNull String getQualifiedName() {
    return getText().replaceFirst("^uses\\s+", "");
  }

  @Override
  public PsiReference getReference() {
    String name = getQualifiedName().trim();
    if (name.endsWith(".*") || name.isEmpty()) return null;
    int start = getText().indexOf(name);
    return new PsiReferenceBase<GosuUsesDecl>(this, new TextRange(start, start + name.length())) {
      @Override public PsiElement resolve() { return GosuTypes.find(name, getElement()); }
      @Override public Object @NotNull [] getVariants() { return new Object[0]; }
    };
  }

  @Override
  public String toString() {
    return "GosuUsesDecl(" + getQualifiedName() + ")";
  }
}
