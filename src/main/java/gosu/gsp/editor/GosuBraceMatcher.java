package gosu.gsp.editor;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import gosu.gsp.psi.GosuDecls.FunctionDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GosuBraceMatcher implements PairedBraceMatcher {
  private static final BracePair[] PAIRS = {
      new BracePair(GosuTokenTypes.LPAREN, GosuTokenTypes.RPAREN, false),
      new BracePair(GosuTokenTypes.LBRACE, GosuTokenTypes.RBRACE, true),
      new BracePair(GosuTokenTypes.LBRACK, GosuTokenTypes.RBRACK, false),
  };

  @Override
  public BracePair @NotNull [] getPairs() {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType openingBraceType,
      @Nullable IElementType beforeType) {
    return beforeType == null
        || !(beforeType == GosuTokenTypes.IDENTIFIER
        || beforeType == GosuTokenTypes.KEYWORD
        || beforeType == GosuTokenTypes.STRING
        || beforeType == GosuTokenTypes.CHAR_LITERAL
        || beforeType == GosuTokenTypes.NUMBER);
  }

  @Override
  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    PsiElement at = file.findElementAt(openingBraceOffset);
    FunctionDecl fn = PsiTreeUtil.getParentOfType(at, FunctionDecl.class, false);
    if (fn != null) {
      return fn.getTextOffset();
    }
    ClassDecl cls = PsiTreeUtil.getParentOfType(at, ClassDecl.class, false);
    if (cls != null) {
      return cls.getTextOffset();
    }
    return openingBraceOffset;
  }
}
