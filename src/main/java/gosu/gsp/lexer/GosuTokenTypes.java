package gosu.gsp.lexer;

import com.intellij.psi.tree.IElementType;
import gosu.gsp.GosuLanguage;

public final class GosuTokenTypes {
  private GosuTokenTypes() {
  }

  private static IElementType token(String debugName) {
    return new IElementType(debugName, GosuLanguage.INSTANCE);
  }

  public static final IElementType KEYWORD = token("GOSU_KEYWORD");
  public static final IElementType IDENTIFIER = token("GOSU_IDENTIFIER");
  public static final IElementType STRING = token("GOSU_STRING");
  public static final IElementType CHAR_LITERAL = token("GOSU_CHAR");
  public static final IElementType NUMBER = token("GOSU_NUMBER");
  public static final IElementType LINE_COMMENT = token("GOSU_LINE_COMMENT");
  public static final IElementType BLOCK_COMMENT = token("GOSU_BLOCK_COMMENT");
  public static final IElementType ANNOTATION = token("GOSU_ANNOTATION");
  public static final IElementType OPERATOR = token("GOSU_OPERATOR");
  /**
   * Named-argument / object-initializer label ({@code :Name = value}).
   * Type ascriptions ({@code x:String}), case colons and ternaries ({@code ?:})
   * stay operators.
   */
  public static final IElementType LABEL = token("GOSU_LABEL");

  public static final IElementType LPAREN = token("GOSU_LPAREN");
  public static final IElementType RPAREN = token("GOSU_RPAREN");
  public static final IElementType LBRACE = token("GOSU_LBRACE");
  public static final IElementType RBRACE = token("GOSU_RBRACE");
  public static final IElementType LBRACK = token("GOSU_LBRACK");
  public static final IElementType RBRACK = token("GOSU_RBRACK");
  public static final IElementType SEMICOLON = token("GOSU_SEMICOLON");
  public static final IElementType COMMA = token("GOSU_COMMA");
  public static final IElementType DOT = token("GOSU_DOT");
  public static final IElementType COLON = token("GOSU_COLON");
}
