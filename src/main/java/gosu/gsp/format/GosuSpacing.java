package gosu.gsp.format;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static gosu.gsp.lexer.GosuTokenTypes.COMMA;
import static gosu.gsp.lexer.GosuTokenTypes.DOT;
import static gosu.gsp.lexer.GosuTokenTypes.KEYWORD;
import static gosu.gsp.lexer.GosuTokenTypes.LBRACE;
import static gosu.gsp.lexer.GosuTokenTypes.LBRACK;
import static gosu.gsp.lexer.GosuTokenTypes.LPAREN;
import static gosu.gsp.lexer.GosuTokenTypes.OPERATOR;
import static gosu.gsp.lexer.GosuTokenTypes.RBRACE;
import static gosu.gsp.lexer.GosuTokenTypes.RBRACK;
import static gosu.gsp.lexer.GosuTokenTypes.RPAREN;
import static gosu.gsp.lexer.GosuTokenTypes.SEMICOLON;

/**
 * Pure spacing rules (no IDE runtime needed): which gaps the formatter may
 * normalize. Anything not listed here keeps the user's original text.
 */
final class GosuSpacing {
  private GosuSpacing() {
  }

  record Gap(int minSpaces, int maxSpaces, int minFeeds, boolean keepBreaks, int keepBlank) {
  }

  static @Nullable Gap gapFor(@NotNull IElementType left, @NotNull String leftText,
      @NotNull IElementType right, @NotNull String rightText) {
    // No space before closers and punctuation; keep the user's line breaks.
    if (right == COMMA || right == SEMICOLON || right == RPAREN || right == RBRACK
        || right == RBRACE || right == DOT) {
      return new Gap(0, 0, 0, true, 0);
    }
    if (left == LPAREN || left == LBRACK || left == DOT) {
      return new Gap(0, 0, 0, true, 0);
    }
    if (left == COMMA || left == SEMICOLON) {
      return new Gap(1, 1, 0, true, 0);
    }
    if (right == LBRACE) {
      return new Gap(1, 1, 0, true, 0);
    }
    if (left == RBRACE && right == KEYWORD
        && (rightText.equals("else") || rightText.equals("catch")
        || rightText.equals("finally") || rightText.equals("while"))) {
      return new Gap(1, 1, 0, true, 0);
    }
    if (left == RBRACE) {
      return new Gap(1, 1, 0, true, 1);
    }
    // Plain '=' (assignment, label, default value) gets gaps; '==', '=>', … untouched.
    if ((left == OPERATOR && leftText.equals("=")) || (right == OPERATOR && rightText.equals("="))) {
      return new Gap(1, 1, 0, true, 0);
    }
    // 'if (' but 'foo(': space only after control keywords, never removed elsewhere.
    if (right == LPAREN && left == KEYWORD && isControlKeyword(leftText)) {
      return new Gap(1, 1, 0, true, 0);
    }
    return null;
  }

  private static boolean isControlKeyword(String word) {
    return word.equals("if") || word.equals("for") || word.equals("foreach")
        || word.equals("while") || word.equals("switch") || word.equals("catch")
        || word.equals("using") || word.equals("unless");
  }
}
