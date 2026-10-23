package gosu.gsp.format;

import com.intellij.psi.tree.IElementType;
import gosu.gsp.lexer.GosuTokenTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Headless tests for the pure spacing rules (no IDE runtime needed). */
public class GosuFormattingTest {
  private static GosuSpacing.Gap gap(String leftText, IElementType left,
      String rightText, IElementType right) {
    return GosuSpacing.gapFor(left, leftText, right, rightText);
  }

  @Test
  void commaAndSemicolon() {
    assertEquals(new GosuSpacing.Gap(1, 1, 0, true, 0),
        gap(",", GosuTokenTypes.COMMA, "a", GosuTokenTypes.IDENTIFIER));
    assertEquals(new GosuSpacing.Gap(0, 0, 0, true, 0),
        gap("a", GosuTokenTypes.IDENTIFIER, ",", GosuTokenTypes.COMMA));
    assertEquals(new GosuSpacing.Gap(1, 1, 0, true, 0),
        gap(";", GosuTokenTypes.SEMICOLON, "b", GosuTokenTypes.IDENTIFIER));
  }

  @Test
  void plainEqualsGetsGaps() {
    assertEquals(new GosuSpacing.Gap(1, 1, 0, true, 0),
        gap("x", GosuTokenTypes.IDENTIFIER, "=", GosuTokenTypes.OPERATOR));
    assertEquals(new GosuSpacing.Gap(1, 1, 0, true, 0),
        gap("=", GosuTokenTypes.OPERATOR, "1", GosuTokenTypes.NUMBER));
    // '==', '?:', '..' and ':' stay exactly as typed.
    assertNull(gap("a", GosuTokenTypes.IDENTIFIER, "==", GosuTokenTypes.OPERATOR));
    assertNull(gap("a", GosuTokenTypes.IDENTIFIER, "?:", GosuTokenTypes.OPERATOR));
    assertNull(gap("1", GosuTokenTypes.NUMBER, "..", GosuTokenTypes.OPERATOR));
    assertNull(gap("x", GosuTokenTypes.IDENTIFIER, ":", GosuTokenTypes.OPERATOR));
  }

  @Test
  void keywordParenVsCallParen() {
    assertEquals(new GosuSpacing.Gap(1, 1, 0, true, 0),
        gap("if", GosuTokenTypes.KEYWORD, "(", GosuTokenTypes.LPAREN));
    assertNull(gap("foo", GosuTokenTypes.IDENTIFIER, "(", GosuTokenTypes.LPAREN));
    assertNull(gap("return", GosuTokenTypes.KEYWORD, "(", GosuTokenTypes.LPAREN));
  }

  @Test
  void bracesAndDots() {
    assertEquals(new GosuSpacing.Gap(1, 1, 0, true, 0),
        gap(")", GosuTokenTypes.RPAREN, "{", GosuTokenTypes.LBRACE));
    assertEquals(new GosuSpacing.Gap(1, 1, 0, true, 0),
        gap("}", GosuTokenTypes.RBRACE, "else", GosuTokenTypes.KEYWORD));
    assertEquals(new GosuSpacing.Gap(0, 0, 0, true, 0),
        gap("a", GosuTokenTypes.IDENTIFIER, ".", GosuTokenTypes.DOT));
    assertEquals(new GosuSpacing.Gap(0, 0, 0, true, 0),
        gap(".", GosuTokenTypes.DOT, "b", GosuTokenTypes.IDENTIFIER));
    assertEquals(new GosuSpacing.Gap(0, 0, 0, true, 0),
        gap("(", GosuTokenTypes.LPAREN, "a", GosuTokenTypes.IDENTIFIER));
  }
}
