package gosu.gsp.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import gosu.gsp.lexer.GosuTokenTypes;
import org.jetbrains.annotations.NotNull;

import static gosu.gsp.lexer.GosuTokenTypes.BLOCK_COMMENT;
import static gosu.gsp.lexer.GosuTokenTypes.COMMA;
import static gosu.gsp.lexer.GosuTokenTypes.DOT;
import static gosu.gsp.lexer.GosuTokenTypes.IDENTIFIER;
import static gosu.gsp.lexer.GosuTokenTypes.KEYWORD;
import static gosu.gsp.lexer.GosuTokenTypes.LBRACE;
import static gosu.gsp.lexer.GosuTokenTypes.LBRACK;
import static gosu.gsp.lexer.GosuTokenTypes.LPAREN;
import static gosu.gsp.lexer.GosuTokenTypes.RBRACE;
import static gosu.gsp.lexer.GosuTokenTypes.RBRACK;
import static gosu.gsp.lexer.GosuTokenTypes.RPAREN;
import static gosu.gsp.lexer.GosuTokenTypes.SEMICOLON;
import static gosu.gsp.psi.GosuElementTypes.BLOCK;
import static gosu.gsp.psi.GosuElementTypes.CLASS_DECL;
import static gosu.gsp.psi.GosuElementTypes.FUNCTION_DECL;
import static gosu.gsp.psi.GosuElementTypes.PARAM;
import static gosu.gsp.psi.GosuElementTypes.REF_EXPR;
import static gosu.gsp.psi.GosuElementTypes.TYPE_REF;
import static gosu.gsp.psi.GosuElementTypes.USES_DECL;
import static gosu.gsp.psi.GosuElementTypes.VAR_DECL;

/**
 * Tolerant hand-written parser: it never reports errors and always consumes
 * input, so Structure View and Go to Declaration keep working even in
 * half-written files. Only declarations, references and blocks become PSI
 * nodes; everything else stays a flat token stream.
 */
public final class GosuParser implements PsiParser {
  @Override
  public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder b) {
    PsiBuilder.Marker file = b.mark();
    while (!b.eof()) {
      parseStatement(b);
    }
    file.done(root);
    return b.getTreeBuilt();
  }

  private static void parseStatement(PsiBuilder b) {
    IElementType t = b.getTokenType();
    if (t == null) {
      return;
    }
    if (text(b).equals("\\")) {
      parseLambda(b);
    }
    else if (t == LBRACE) {
      parseBlock(b);
    }
    else if (t == IDENTIFIER || t == KEYWORD && followsDot(b)) {
      PsiBuilder.Marker m = b.mark();
      b.advanceLexer();
      m.done(REF_EXPR);
    }
    else if (t == KEYWORD) {
      String kw = text(b);
      switch (kw) {
        case "uses", "package" ->
            parseDotted(b, kw.equals("uses") ? USES_DECL : null);
        case "class", "interface", "enum", "enhancement", "structure" ->
            parseClass(b);
        case "function", "construct" ->
            parseFunction(b);
        case "var", "property", "delegate" ->
            parseVar(b);
        case "for", "foreach" ->
            parseLoop(b);
        case "catch", "using" ->
            parseCatchUsing(b);
        case "new" ->
            parseNew(b);
        default ->
            b.advanceLexer();
      }
    }
    else {
      b.advanceLexer();
    }
  }

  private static boolean followsDot(PsiBuilder b) {
    int i = -1;
    IElementType previous = b.rawLookup(i);
    while (previous == com.intellij.psi.TokenType.WHITE_SPACE || previous == GosuTokenTypes.LINE_COMMENT || previous == BLOCK_COMMENT) previous = b.rawLookup(--i);
    return previous == DOT;
  }

  /** {@code uses a.b.C} (marked) or {@code package a.b} (unmarked, consumed raw). */
  private static void parseDotted(PsiBuilder b, IElementType markType) {
    PsiBuilder.Marker m = markType == null ? null : b.mark();
    b.advanceLexer();
    if (b.getTokenType() == IDENTIFIER) {
      b.advanceLexer();
      while (b.getTokenType() == DOT) {
        b.advanceLexer();
        if (b.getTokenType() == IDENTIFIER || text(b).equals("*")) b.advanceLexer();
        else break;
      }
    }
    if (m != null) {
      m.done(markType);
    }
  }

  private static void parseClass(PsiBuilder b) {
    PsiBuilder.Marker m = b.mark();
    boolean isEnum = b.getTokenType() == KEYWORD && text(b).equals("enum");
    b.advanceLexer(); // class | interface | enum | enhancement | structure
    if (b.getTokenType() == IDENTIFIER) {
      b.advanceLexer(); // name
    }
    while (!b.eof()) {
      IElementType t = b.getTokenType();
      if (t == LBRACE) {
        if (isEnum) {
          parseEnumBody(b);
        }
        else {
          parseBlock(b);
        }
        break;
      }
      if (t == SEMICOLON) {
        b.advanceLexer();
        break;
      }
      if (t == KEYWORD && (text(b).equals("extends") || text(b).equals("implements"))) {
        b.advanceLexer();
        parseDottedTypeRefs(b);
      }
      else if (t == GosuTokenTypes.OPERATOR && text(b).equals(":")) {
        // enhancement MyEnh : SomeType
        b.advanceLexer();
        parseDottedTypeRefs(b);
      }
      else {
        b.advanceLexer();
      }
    }
    m.done(CLASS_DECL);
  }

  /** Marks {@code A.B.C} identifier segments as TYPE_REF. */
  private static void parseDottedTypeRefs(PsiBuilder b) {
    if (b.getTokenType() != IDENTIFIER && !isPrimitive(text(b))) return;
    parseTypeSegment(b);
    while (b.getTokenType() == DOT) {
      b.advanceLexer();
      if (b.getTokenType() != IDENTIFIER) break;
      parseTypeSegment(b);
    }
    if (text(b).equals("<")) {
      b.advanceLexer();
      parseDottedTypeRefs(b);
      while (b.getTokenType() == COMMA) {
        b.advanceLexer();
        parseDottedTypeRefs(b);
      }
      if (text(b).equals(">")) b.advanceLexer();
    }
    while (b.getTokenType() == LBRACK && b.lookAhead(1) == RBRACK) {
      b.advanceLexer();
      b.advanceLexer();
    }
  }

  private static boolean isPrimitive(String text) {
    return java.util.Set.of("void", "boolean", "byte", "short", "int", "long", "float", "double", "char").contains(text);
  }

  private static void parseTypeSegment(PsiBuilder b) {
    PsiBuilder.Marker marker = b.mark();
    b.advanceLexer();
    marker.done(TYPE_REF);
  }

  private static void parseFunction(PsiBuilder b) {
    PsiBuilder.Marker m = b.mark();
    b.advanceLexer(); // function | construct
    if (b.getTokenType() == IDENTIFIER) {
      b.advanceLexer(); // name
    }
    if (b.getTokenType() == LPAREN) {
      b.advanceLexer();
      int depth = 0;
      while (!b.eof()) {
        IElementType t = b.getTokenType();
        if (t == LPAREN || t == LBRACE || t == LBRACK) {
          depth++;
          b.advanceLexer();
        }
        else if (t == RPAREN || t == RBRACE || t == RBRACK) {
          if (depth == 0 && t == RPAREN) {
            b.advanceLexer();
            break;
          }
          if (depth > 0) {
            depth--;
          }
          b.advanceLexer();
        }
        else if (t == IDENTIFIER && depth == 0) {
          parseParam(b);
        }
        else {
          b.advanceLexer();
        }
      }
      if (b.getTokenType() == GosuTokenTypes.OPERATOR && text(b).equals(":")) {
        b.advanceLexer(); // return type colon
        parseDottedTypeRefs(b);
      }
    }
    if (b.getTokenType() == LBRACE) {
      parseBlock(b);
    }
    m.done(FUNCTION_DECL);
  }

  /** {@code name [: Type]} — default values and annotations are skipped raw. */
  private static void parseParam(PsiBuilder b) {
    PsiBuilder.Marker m = b.mark();
    b.advanceLexer(); // name
    if (b.getTokenType() == GosuTokenTypes.OPERATOR && text(b).equals(":")) {
      b.advanceLexer();
      parseDottedTypeRefs(b);
    }
    m.done(PARAM);
  }

  /**
   * {@code var name [: Type]}, {@code property [get|set] name [( )] [: Type]}.
   * Covers {@code var x : String as External} — the {@code as} alias stays raw.
   */
  private static void parseVar(PsiBuilder b) {
    PsiBuilder.Marker m = b.mark();
    b.advanceLexer(); // var | property | delegate
    while (b.getTokenType() == KEYWORD && (text(b).equals("get") || text(b).equals("set"))) {
      b.advanceLexer();
    }
    if (b.getTokenType() == IDENTIFIER) {
      b.advanceLexer(); // name
    }
    if (b.getTokenType() == LPAREN) {
      skipBalanced(b, LPAREN, RPAREN); // property get Name()
    }
    if (b.getTokenType() == GosuTokenTypes.OPERATOR && text(b).equals(":")) {
      b.advanceLexer();
      parseDottedTypeRefs(b);
    }
    m.done(VAR_DECL);
  }

  /**
   * {@code for (x in ... [index i])}: marks the loop variable (and the
   * optional index variable) as declarations, consumes the whole header so
   * the main loop does not re-walk it.
   */
  private static void parseLoop(PsiBuilder b) {
    b.advanceLexer(); // for | foreach
    if (b.getTokenType() != LPAREN) {
      return;
    }
    b.advanceLexer();
    int depth = 1;
    boolean indexNext = false;
    while (!b.eof()) {
      IElementType t = b.getTokenType();
      if (t == LPAREN) {
        depth++;
        b.advanceLexer();
        indexNext = false;
      }
      else if (t == RPAREN) {
        b.advanceLexer();
        if (--depth == 0) {
          break;
        }
        indexNext = false;
      }
      else if (t == IDENTIFIER) {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer();
        if ((b.getTokenType() == KEYWORD && text(b).equals("in")) || indexNext) {
          m.done(VAR_DECL);
        }
        else {
          m.done(REF_EXPR);
        }
        indexNext = false;
      }
      else if (t == KEYWORD && text(b).equals("index")) {
        b.advanceLexer();
        indexNext = true;
      }
      else {
        b.advanceLexer();
        indexNext = false;
      }
    }
  }

  /**
   * {@code catch (e [: Type])} and {@code using ([var] x = ...)}: the caught /
   * declared resource is a declaration, the rest of the header is consumed raw
   * (identifiers become references, {@code new X} a type reference).
   */
  private static void parseCatchUsing(PsiBuilder b) {
    boolean isCatch = text(b).equals("catch");
    b.advanceLexer();
    if (b.getTokenType() != LPAREN) {
      return;
    }
    b.advanceLexer();
    boolean hasVar = false;
    if (b.getTokenType() == KEYWORD && text(b).equals("var")) {
      b.advanceLexer();
      hasVar = true;
    }
    // catch (e ...) always declares; using (...) only after an explicit var.
    if (b.getTokenType() == IDENTIFIER && (isCatch || hasVar)) {
      parseVarRemainder(b);
    }
    int depth = 1;
    while (!b.eof()) {
      IElementType t = b.getTokenType();
      if (t == LPAREN) {
        depth++;
        b.advanceLexer();
      }
      else if (t == RPAREN) {
        b.advanceLexer();
        if (--depth == 0) {
          break;
        }
      }
      else if (t == IDENTIFIER) {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer();
        m.done(REF_EXPR);
      }
      else if (t == KEYWORD && text(b).equals("new")) {
        parseNew(b);
      }
      else {
        b.advanceLexer();
      }
    }
  }

  /** Marks an already-positioned {@code name [: Type]} as VAR_DECL. */
  private static void parseVarRemainder(PsiBuilder b) {
    PsiBuilder.Marker m = b.mark();
    b.advanceLexer(); // name
    if (b.getTokenType() == GosuTokenTypes.OPERATOR && text(b).equals(":")) {
      b.advanceLexer();
      parseDottedTypeRefs(b);
    }
    m.done(VAR_DECL);
  }

  /** {@code new Type(...)}: the type name becomes a TYPE_REF (resolves to the class). */
  private static void parseNew(PsiBuilder b) {
    b.advanceLexer(); // new
    parseDottedTypeRefs(b);
  }

  private static void parseLambda(PsiBuilder b) {
    var lambda = b.mark();
    b.advanceLexer();
    while (!b.eof() && !text(b).equals("->")) {
      if (b.getTokenType() != IDENTIFIER) break;
      parseParam(b);
      if (b.getTokenType() != COMMA) break;
      b.advanceLexer();
    }
    if (text(b).equals("->")) {
      b.advanceLexer();
      if (b.getTokenType() == LBRACE) parseBlock(b);
      else {
        int depth = 0, previous = b.getCurrentOffset();
        while (!b.eof()) {
          var t = b.getTokenType();
          if (depth == 0 && (t == COMMA || t == RPAREN || t == RBRACE || t == SEMICOLON
              || b.getOriginalText().subSequence(previous, b.getCurrentOffset()).toString().contains("\n"))) break;
          if (t == LPAREN || t == LBRACK) depth++;
          if (t == RPAREN || t == RBRACK) depth--;
          previous = b.getCurrentOffset() + text(b).length();
          parseStatement(b);
          previous = Math.min(previous, b.getCurrentOffset());
        }
      }
    }
    lambda.done(GosuElementTypes.LAMBDA);
  }

  private static void parseBlock(PsiBuilder b) {
    PsiBuilder.Marker m = b.mark();
    b.advanceLexer(); // {
    while (!b.eof() && b.getTokenType() != RBRACE) {
      parseStatement(b);
    }
    if (b.getTokenType() == RBRACE) {
      b.advanceLexer();
    }
    m.done(BLOCK);
  }

  /**
   * Enum body: bare {@code RED, GREEN;} constants are declarations (they show
   * in Structure and resolve); nested blocks and members parse generically.
   */
  private static void parseEnumBody(PsiBuilder b) {
    PsiBuilder.Marker m = b.mark();
    b.advanceLexer(); // {
    while (!b.eof() && b.getTokenType() != RBRACE) {
      if (b.getTokenType() == IDENTIFIER) {
        PsiBuilder.Marker c = b.mark();
        b.advanceLexer();
        IElementType next = b.getTokenType();
        if (next == COMMA || next == SEMICOLON || next == RBRACE) {
          c.done(VAR_DECL);
        }
        else {
          c.done(REF_EXPR);
        }
      }
      else {
        parseStatement(b);
      }
    }
    if (b.getTokenType() == RBRACE) {
      b.advanceLexer();
    }
    m.done(BLOCK);
  }

  private static void skipBalanced(PsiBuilder b, IElementType open, IElementType close) {
    b.advanceLexer(); // open
    int depth = 1;
    while (!b.eof() && depth > 0) {
      IElementType t = b.getTokenType();
      if (t == open) {
        depth++;
      }
      else if (t == close) {
        depth--;
      }
      b.advanceLexer();
    }
  }

  private static String text(PsiBuilder b) {
    CharSequence s = b.getTokenText();
    return s == null ? "" : s.toString();
  }
}
