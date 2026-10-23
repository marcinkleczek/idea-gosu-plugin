package gosu.gsp.editor;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import gosu.gsp.lexer.GosuLexer;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import gosu.gsp.psi.GosuDecls.FunctionDecl;
import gosu.gsp.psi.GosuDecls.Param;
import gosu.gsp.psi.GosuDecls.VarDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Word scanner for highlight-usages and basic Find Usages on declarations. */
public final class GosuFindUsagesProvider implements FindUsagesProvider {
  @Override
  public @Nullable WordsScanner getWordsScanner() {
    return new DefaultWordsScanner(new GosuLexer(),
        TokenSet.create(GosuTokenTypes.IDENTIFIER),
        TokenSet.create(GosuTokenTypes.LINE_COMMENT, GosuTokenTypes.BLOCK_COMMENT),
        TokenSet.create(GosuTokenTypes.STRING, GosuTokenTypes.CHAR_LITERAL));
  }

  @Override
  public boolean canFindUsagesFor(@NotNull PsiElement element) {
    return element instanceof PsiNamedElement;
  }

  @Override
  public @Nullable String getHelpId(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public @NotNull String getType(@NotNull PsiElement element) {
    if (element instanceof FunctionDecl) {
      return "function";
    }
    if (element instanceof ClassDecl) {
      return "class";
    }
    if (element instanceof Param) {
      return "parameter";
    }
    if (element instanceof VarDecl) {
      return "variable";
    }
    return "identifier";
  }

  @Override
  public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
    if (element instanceof PsiNamedElement named && named.getName() != null) {
      return named.getName();
    }
    return element.getText();
  }

  @Override
  public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }
}
