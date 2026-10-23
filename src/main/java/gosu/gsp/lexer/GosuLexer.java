package gosu.gsp.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Hand-written lexer for Gosu (script mode). Covers the full keyword set from
 * the official gosu-lang grammar (Gosu.g), line and block comments,
 * double/single-quoted strings with {@code ${...}} interpolation, char literals,
 * numbers (hex, binary, exponents, type suffixes), {@code @Annotations} and operators.
 */
public final class GosuLexer extends LexerBase {
  // Practical keyword set from the official grammar. Only the scope words
  // (application, session, request, execution) stay identifiers on purpose -
  // they are commonly used as variable names.
  private static final Set<String> KEYWORDS = Set.of(
      "abstract", "and", "as", "assert", "block", "break", "case", "catch",
      "class", "classpath", "construct", "contains", "continue", "default",
      "delegate", "do", "else", "enhancement", "enum", "eval", "except",
      "exists", "extends", "false",
      "final", "finally", "find", "for", "foreach", "function", "get", "hide",
      "if", "implements", "in", "index", "Infinity", "interface", "internal",
      "iterator", "length", "NaN", "new", "not", "null", "or", "outer",
      "override", "package", "private", "property", "protected", "public",
      "readonly", "represents", "return", "set", "startswith", "static",
      "statictypeof", "structure", "super", "switch", "this", "throw",
      "transient", "true", "try", "typeas", "typeis", "typeof", "typeloader",
      "unless", "uses", "using", "var", "void", "where", "while");

  private CharSequence buffer;
  private int endOffset;
  private int position;
  private int tokenStart;
  private int tokenEnd;
  private IElementType tokenType;

  /** Keyword set for completion contributors. */
  public static java.util.Set<String> getKeywords() {
    return KEYWORDS;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    this.buffer = buffer;
    this.endOffset = endOffset;
    this.position = startOffset;
    advance();
  }

  @Override
  public int getState() {
    return 0;
  }

  @Override
  public @Nullable IElementType getTokenType() {
    return tokenType;
  }

  @Override
  public int getTokenStart() {
    return tokenStart;
  }

  @Override
  public int getTokenEnd() {
    return tokenEnd;
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return buffer;
  }

  @Override
  public int getBufferEnd() {
    return endOffset;
  }

  @Override
  public void advance() {
    tokenStart = position;
    if (position >= endOffset) {
      tokenEnd = position;
      tokenType = null;
      return;
    }
    char c = buffer.charAt(position);
    if (isWhitespace(c)) {
      scanWhile(GosuLexer::isWhitespace);
      tokenType = TokenType.WHITE_SPACE;
    }
    else if (c == '/' && peek(1) == '/') {
      scanUntil('\n');
      tokenType = GosuTokenTypes.LINE_COMMENT;
    }
    else if (c == '/' && peek(1) == '*') {
      scanBlockComment();
      tokenType = GosuTokenTypes.BLOCK_COMMENT;
    }
    else if (c == '"' || (c == '$' && peek(1) == '"')) {
      scanString();
      tokenType = GosuTokenTypes.STRING;
    }
    else if (c == '\'') {
      tokenType = scanQuoted();
    }
    else if (isDigit(c) || (c == '.' && isDigit(peek(1)))) {
      scanNumber();
      tokenType = GosuTokenTypes.NUMBER;
    }
    else if (c == '@') {
      tokenType = scanAnnotation();
    }
    else if (isIdentStart(c)) {
      scanWord();
    }
    else if (c == '.' && peek(1) == '.') {
      scanWhile(ch -> ch == '.');
      tokenType = GosuTokenTypes.OPERATOR;
    }
    else if (isSinglePunct(c)) {
      position++;
      tokenType = punctType(c);
    }
    else if (c == ':' && isLabelStart()) {
      position++;
      scanWhile(GosuLexer::isIdentPart);
      tokenType = GosuTokenTypes.LABEL;
    }
    else if (isOpChar(c)) {
      scanWhile(GosuLexer::isOpChar);
      tokenType = GosuTokenTypes.OPERATOR;
    }
    else {
      position++;
      tokenType = TokenType.BAD_CHARACTER;
    }
    tokenEnd = position;
  }

  private void scanWord() {
    scanWhile(GosuLexer::isIdentPart);
    String word = buffer.subSequence(tokenStart, position).toString();
    tokenType = KEYWORDS.contains(word) ? GosuTokenTypes.KEYWORD : GosuTokenTypes.IDENTIFIER;
  }

  /**
   * {@code :Name} is a named-argument / object-initializer label only when an
   * {@code =} follows the name AND the colon opens an argument slot: it starts
   * a line ({@code :Update = true}) or directly follows {@code (}, {@code {},
   * or {@code,} ({@code @Anno(:a = 1)}, {@code new T() {:name = "x"}}).
   * Everything else ({@code x:String}, {@code (): String}, {@code case "a":},
   * {@code a ?: b}) stays an operator.
   */
  private boolean isLabelStart() {
    if (!isIdentStart(peek(1))) {
      return false;
    }
    int q = position + 1;
    while (q < endOffset && isIdentPart(buffer.charAt(q))) {
      q++;
    }
    while (q < endOffset && (buffer.charAt(q) == ' ' || buffer.charAt(q) == '\t')) {
      q++;
    }
    if (q >= endOffset || buffer.charAt(q) != '=') {
      return false;
    }
    int p = tokenStart - 1;
    while (p >= 0 && (buffer.charAt(p) == ' ' || buffer.charAt(p) == '\t')) {
      p--;
    }
    if (p < 0 || buffer.charAt(p) == '\n' || buffer.charAt(p) == '\r') {
      return true;
    }
    char prev = buffer.charAt(p);
    return prev == '(' || prev == '{' || prev == ',';
  }

  private IElementType scanAnnotation() {
    int p = position + 1;
    if (p < endOffset && isIdentStart(buffer.charAt(p))) {
      while (p < endOffset && isIdentPart(buffer.charAt(p))) {
        p++;
      }
      position = p;
      return GosuTokenTypes.ANNOTATION;
    }
    position++;
    return GosuTokenTypes.OPERATOR;
  }

  private void scanBlockComment() {
    position += 2;
    while (position < endOffset) {
      if (buffer.charAt(position) == '*' && peekFrom(position, 1) == '/') {
        position += 2;
        return;
      }
      position++;
    }
  }

  private void scanString() {
    if (buffer.charAt(position) == '$') {
      position++;
    }
    position++;
    while (position < endOffset) {
      char c = buffer.charAt(position);
      if (c == '\\') {
        position = Math.min(position + 2, endOffset);
      }
      else if (c == '$' && peekFrom(position, 1) == '{') {
        position = skipInterpolation(position + 2);
      }
      else if (c == '"') {
        position++;
        return;
      }
      else if (c == '\n' || c == '\r') {
        return;
      }
      else {
        position++;
      }
    }
  }

  private IElementType scanQuoted() {
    int contentStart = position + 1;
    position++;
    while (position < endOffset) {
      char c = buffer.charAt(position);
      if (c == '\\') {
        position = Math.min(position + 2, endOffset);
      }
      else if (c == '$' && peekFrom(position, 1) == '{') {
        position = skipInterpolation(position + 2);
      }
      else if (c == '\'') {
        position++;
        return isSingleChar(contentStart, position - 1)
            ? GosuTokenTypes.CHAR_LITERAL
            : GosuTokenTypes.STRING;
      }
      else if (c == '\n' || c == '\r') {
        return GosuTokenTypes.STRING;
      }
      else {
        position++;
      }
    }
    return GosuTokenTypes.STRING;
  }

  private boolean isSingleChar(int contentStart, int contentEnd) {
    int len = contentEnd - contentStart;
    if (len == 1) {
      return true;
    }
    return len == 2 && buffer.charAt(contentStart) == '\\';
  }

  private int skipInterpolation(int from) {
    int depth = 1;
    int p = from;
    while (p < endOffset && depth > 0) {
      char c = buffer.charAt(p);
      if (c == '\\') {
        p = Math.min(p + 2, endOffset);
      }
      else if (c == '"' || c == '\'') {
        p = skipNestedString(p);
      }
      else if (c == '{') {
        depth++;
        p++;
      }
      else if (c == '}') {
        depth--;
        p++;
      }
      else {
        p++;
      }
    }
    return p;
  }

  private int skipNestedString(int from) {
    char quote = buffer.charAt(from);
    int p = from + 1;
    while (p < endOffset) {
      char c = buffer.charAt(p);
      if (c == '\\') {
        p = Math.min(p + 2, endOffset);
      }
      else if (c == quote) {
        return p + 1;
      }
      else if (c == '\n' || c == '\r') {
        return p;
      }
      else {
        p++;
      }
    }
    return p;
  }

  private void scanNumber() {
    if (buffer.charAt(position) == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
      position += 2;
      scanWhile(GosuLexer::isHexDigit);
    }
    else if (buffer.charAt(position) == '0' && (peek(1) == 'b' || peek(1) == 'B')) {
      position += 2;
      scanWhile(c -> c == '0' || c == '1');
    }
    else {
      scanWhile(GosuLexer::isDigit);
      if (peek(0) == '.' && peek(1) != '.') {
        position++;
        scanWhile(GosuLexer::isDigit);
      }
      if (peek(0) == 'e' || peek(0) == 'E') {
        int save = position;
        position++;
        if (peek(0) == '+' || peek(0) == '-') {
          position++;
        }
        if (position < endOffset && isDigit(buffer.charAt(position))) {
          scanWhile(GosuLexer::isDigit);
        }
        else {
          position = save;
        }
      }
    }
    scanWhile(GosuLexer::isLetter);
  }

  private char peek(int ahead) {
    return peekFrom(position, ahead);
  }

  private char peekFrom(int base, int ahead) {
    int i = base + ahead;
    return i < endOffset ? buffer.charAt(i) : 0;
  }

  private void scanWhile(CharPredicate test) {
    while (position < endOffset && test.test(buffer.charAt(position))) {
      position++;
    }
  }

  private interface CharPredicate {
    boolean test(char c);
  }

  private void scanUntil(char stop) {
    while (position < endOffset && buffer.charAt(position) != stop) {
      position++;
    }
  }

  private static boolean isWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isHexDigit(char c) {
    return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private static boolean isLetter(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private static boolean isIdentStart(char c) {
    return isLetter(c) || c == '_' || c == '$';
  }

  private static boolean isIdentPart(char c) {
    return isIdentStart(c) || isDigit(c);
  }

  private static boolean isOpChar(char c) {
    return "=<>!+-*/%&|^~?:".indexOf(c) >= 0;
  }

  private static boolean isSinglePunct(char c) {
    return c == '(' || c == ')' || c == '{' || c == '}' || c == '['
        || c == ']' || c == ';' || c == ',' || c == '.';
  }

  private static IElementType punctType(char c) {
    switch (c) {
      case '(':
        return GosuTokenTypes.LPAREN;
      case ')':
        return GosuTokenTypes.RPAREN;
      case '{':
        return GosuTokenTypes.LBRACE;
      case '}':
        return GosuTokenTypes.RBRACE;
      case '[':
        return GosuTokenTypes.LBRACK;
      case ']':
        return GosuTokenTypes.RBRACK;
      case ';':
        return GosuTokenTypes.SEMICOLON;
      case ',':
        return GosuTokenTypes.COMMA;
      case '.':
        return GosuTokenTypes.DOT;
      default:
        return GosuTokenTypes.OPERATOR;
    }
  }
}
