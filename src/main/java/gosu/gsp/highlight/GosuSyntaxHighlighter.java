package gosu.gsp.highlight;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import gosu.gsp.lexer.GosuLexer;
import gosu.gsp.lexer.GosuTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class GosuSyntaxHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> KEYS = new HashMap<>();

  static {
    KEYS.put(GosuTokenTypes.KEYWORD, DefaultLanguageHighlighterColors.KEYWORD);
    KEYS.put(GosuTokenTypes.STRING, DefaultLanguageHighlighterColors.STRING);
    KEYS.put(GosuTokenTypes.CHAR_LITERAL, DefaultLanguageHighlighterColors.STRING);
    KEYS.put(GosuTokenTypes.NUMBER, DefaultLanguageHighlighterColors.NUMBER);
    KEYS.put(GosuTokenTypes.LINE_COMMENT, DefaultLanguageHighlighterColors.LINE_COMMENT);
    KEYS.put(GosuTokenTypes.BLOCK_COMMENT, DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    KEYS.put(GosuTokenTypes.ANNOTATION, DefaultLanguageHighlighterColors.METADATA);
    KEYS.put(GosuTokenTypes.LABEL, DefaultLanguageHighlighterColors.LABEL);
    KEYS.put(GosuTokenTypes.OPERATOR, DefaultLanguageHighlighterColors.OPERATION_SIGN);
    KEYS.put(GosuTokenTypes.LPAREN, DefaultLanguageHighlighterColors.PARENTHESES);
    KEYS.put(GosuTokenTypes.RPAREN, DefaultLanguageHighlighterColors.PARENTHESES);
    KEYS.put(GosuTokenTypes.LBRACE, DefaultLanguageHighlighterColors.BRACES);
    KEYS.put(GosuTokenTypes.RBRACE, DefaultLanguageHighlighterColors.BRACES);
    KEYS.put(GosuTokenTypes.LBRACK, DefaultLanguageHighlighterColors.BRACKETS);
    KEYS.put(GosuTokenTypes.RBRACK, DefaultLanguageHighlighterColors.BRACKETS);
    KEYS.put(GosuTokenTypes.SEMICOLON, DefaultLanguageHighlighterColors.SEMICOLON);
    KEYS.put(GosuTokenTypes.COMMA, DefaultLanguageHighlighterColors.COMMA);
    KEYS.put(GosuTokenTypes.DOT, DefaultLanguageHighlighterColors.DOT);
    KEYS.put(GosuTokenTypes.COLON, DefaultLanguageHighlighterColors.DOT);
    KEYS.put(GosuTokenTypes.IDENTIFIER, DefaultLanguageHighlighterColors.IDENTIFIER);
  }

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new GosuLexer();
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    return pack(KEYS.get(tokenType));
  }
}
