package gosu.gsp.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GosuLexerTest {
  private record Tok(String text, IElementType type) {
  }

  private static List<Tok> lex(String text) {
    GosuLexer lexer = new GosuLexer();
    lexer.start(text, 0, text.length(), 0);
    List<Tok> out = new ArrayList<>();
    while (lexer.getTokenType() != null) {
      IElementType type = lexer.getTokenType();
      out.add(new Tok(text.substring(lexer.getTokenStart(), lexer.getTokenEnd()), type));
      lexer.advance();
    }
    return out;
  }

  private static List<Tok> significant(String text) {
    return lex(text).stream()
        .filter(t -> t.type() != TokenType.WHITE_SPACE)
        .toList();
  }

  private static void assertTypes(String text, IElementType... expected) {
    List<Tok> toks = significant(text);
    List<IElementType> actual = toks.stream().map(Tok::type).toList();
    assertEquals(List.of(expected), actual, () -> "tokens: " + toks);
  }

  @Test
  void keywordsAndIdentifiers() {
    assertTypes("var x = 1",
        GosuTokenTypes.KEYWORD, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.OPERATOR, GosuTokenTypes.NUMBER);
  }

  @Test
  void softScopeWordsStayIdentifiers() {
    // application/session/request/execution are contextual in Gosu - must not
    // steal keyword color when used as variable names.
    assertTypes("var request = session",
        GosuTokenTypes.KEYWORD, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.OPERATOR, GosuTokenTypes.IDENTIFIER);
  }

  @Test
  void comments() {
    assertTypes("// line\n/* block */ var",
        GosuTokenTypes.LINE_COMMENT, GosuTokenTypes.BLOCK_COMMENT,
        GosuTokenTypes.KEYWORD);
  }

  @Test
  void unterminatedBlockCommentIsStillComment() {
    assertTypes("/* oops", GosuTokenTypes.BLOCK_COMMENT);
  }

  @Test
  void stringsWithInterpolationStayOneToken() {
    List<Tok> toks = significant("var s = \"Hello, ${who}!\"");
    assertEquals(GosuTokenTypes.STRING, toks.get(3).type());
    assertEquals("\"Hello, ${who}!\"", toks.get(3).text());
  }

  @Test
  void dollarStringAndChar() {
    assertTypes("$\"a${b}c\" 'x' 'str'",
        GosuTokenTypes.STRING, GosuTokenTypes.CHAR_LITERAL, GosuTokenTypes.STRING);
  }

  @Test
  void numbers() {
    assertTypes("1 0xFF 0b101 2.5e2 10bi",
        GosuTokenTypes.NUMBER, GosuTokenTypes.NUMBER, GosuTokenTypes.NUMBER,
        GosuTokenTypes.NUMBER, GosuTokenTypes.NUMBER);
  }

  @Test
  void annotationAndOperators() {
    // "?." lexes as OPERATOR("?") + DOT(".") - both color as operators.
    assertTypes("@Stateless a === b <> c ?. d ?: e",
        GosuTokenTypes.ANNOTATION, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.OPERATOR, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.OPERATOR, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.OPERATOR, GosuTokenTypes.DOT, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.OPERATOR, GosuTokenTypes.IDENTIFIER);
  }

  @Test
  void rangeOperator() {
    assertTypes("0..10",
        GosuTokenTypes.NUMBER, GosuTokenTypes.OPERATOR, GosuTokenTypes.NUMBER);
  }

  @Test
  void namedArgumentLabelAtLineStart() {
    assertTypes(":Update = true",
        GosuTokenTypes.LABEL, GosuTokenTypes.OPERATOR, GosuTokenTypes.KEYWORD);
    assertTypes("copyFiles({\n  :BaseDir = dir,\n  :Update = true\n})",
        GosuTokenTypes.IDENTIFIER, GosuTokenTypes.LPAREN, GosuTokenTypes.LBRACE,
        GosuTokenTypes.LABEL, GosuTokenTypes.OPERATOR, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.COMMA,
        GosuTokenTypes.LABEL, GosuTokenTypes.OPERATOR, GosuTokenTypes.KEYWORD,
        GosuTokenTypes.RBRACE, GosuTokenTypes.RPAREN);
  }

  @Test
  void labelsInArgumentSlots() {
    // After '(' or ',' (annotations, single-line initializers) labels count too.
    assertTypes("@Anno(:a = 1, :b = 2)",
        GosuTokenTypes.ANNOTATION, GosuTokenTypes.LPAREN,
        GosuTokenTypes.LABEL, GosuTokenTypes.OPERATOR, GosuTokenTypes.NUMBER,
        GosuTokenTypes.COMMA,
        GosuTokenTypes.LABEL, GosuTokenTypes.OPERATOR, GosuTokenTypes.NUMBER,
        GosuTokenTypes.RPAREN);
    assertTypes("new Test() {:name = \"t\"}",
        GosuTokenTypes.KEYWORD, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.LPAREN, GosuTokenTypes.RPAREN, GosuTokenTypes.LBRACE,
        GosuTokenTypes.LABEL, GosuTokenTypes.OPERATOR, GosuTokenTypes.STRING,
        GosuTokenTypes.RBRACE);
  }

  @Test
  void colonIsNotLabelForTypesAndTernary() {
    // Type ascription: ':' stays OPERATOR even without spaces.
    assertTypes("var x:String = 1",
        GosuTokenTypes.KEYWORD, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.OPERATOR, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.OPERATOR, GosuTokenTypes.NUMBER);
    // Return type after ')' and 'case' colons are operators as well.
    assertTypes("function f() : String",
        GosuTokenTypes.KEYWORD, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.LPAREN, GosuTokenTypes.RPAREN,
        GosuTokenTypes.OPERATOR, GosuTokenTypes.IDENTIFIER);
    assertTypes("case \"a\":",
        GosuTokenTypes.KEYWORD, GosuTokenTypes.STRING, GosuTokenTypes.OPERATOR);
    // Ternary is one operator, not a label.
    assertTypes("a ?: b",
        GosuTokenTypes.IDENTIFIER, GosuTokenTypes.OPERATOR, GosuTokenTypes.IDENTIFIER);
    // ':Name' without '=' is not a label either.
    assertTypes("foo(:Name)",
        GosuTokenTypes.IDENTIFIER, GosuTokenTypes.LPAREN,
        GosuTokenTypes.OPERATOR, GosuTokenTypes.IDENTIFIER, GosuTokenTypes.RPAREN);
  }

  @Test
  void controlKeywords() {
    assertTypes("if (a and b) { return } else { foreach (x in y) { break } }",
        GosuTokenTypes.KEYWORD, GosuTokenTypes.LPAREN,
        GosuTokenTypes.IDENTIFIER, GosuTokenTypes.KEYWORD, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.RPAREN, GosuTokenTypes.LBRACE, GosuTokenTypes.KEYWORD,
        GosuTokenTypes.RBRACE, GosuTokenTypes.KEYWORD, GosuTokenTypes.LBRACE,
        GosuTokenTypes.KEYWORD, GosuTokenTypes.LPAREN, GosuTokenTypes.IDENTIFIER,
        GosuTokenTypes.KEYWORD, GosuTokenTypes.IDENTIFIER, GosuTokenTypes.RPAREN,
        GosuTokenTypes.LBRACE, GosuTokenTypes.KEYWORD,
        GosuTokenTypes.RBRACE, GosuTokenTypes.RBRACE);
  }
}
