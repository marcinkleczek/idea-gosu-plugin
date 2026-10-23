package gosu.gsp.psi;

import com.intellij.psi.tree.IElementType;
import gosu.gsp.GosuLanguage;

/**
 * Composite PSI element types built by {@link GosuParser}.
 * Tokens (keywords, identifiers, ...) live in the lexer package.
 */
public final class GosuElementTypes {
  private GosuElementTypes() {
  }

  private static IElementType element(String debugName) {
    return new IElementType(debugName, GosuLanguage.INSTANCE);
  }

  /**
   * Stub-backed element types (see {@code stubElementTypeHolder}): the parser
   * marks declarations with these so class/function names land in the stub
   * index for project-wide Goto Symbol.
   */
  public static final IElementType CLASS_DECL = gosu.gsp.stubs.GosuStubElementTypes.CLASS;
  public static final IElementType FUNCTION_DECL = gosu.gsp.stubs.GosuStubElementTypes.FUNCTION;
  public static final IElementType VAR_DECL = element("GOSU_VAR_DECL");
  public static final IElementType PARAM = element("GOSU_PARAM");
  public static final IElementType USES_DECL = element("GOSU_USES_DECL");
  /** A plain identifier use: variable, function call, qualifier, ... */
  public static final IElementType REF_EXPR = element("GOSU_REF_EXPR");
  /** An identifier in type position: after {@code new}, {@code extends}, {@code :}. */
  public static final IElementType TYPE_REF = element("GOSU_TYPE_REF");
  public static final IElementType LAMBDA = element("GOSU_LAMBDA");
  public static final IElementType BLOCK = element("GOSU_BLOCK");
}
