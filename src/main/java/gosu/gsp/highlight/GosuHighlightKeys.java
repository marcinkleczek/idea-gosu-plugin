package gosu.gsp.highlight;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/** Semantic colors applied by {@link GosuAnnotator} on top of the lexer. */
public final class GosuHighlightKeys {
  private GosuHighlightKeys() {
  }

  private static TextAttributesKey key(String name, TextAttributesKey fallback) {
    return TextAttributesKey.createTextAttributesKey("GOSU_" + name, fallback);
  }

  public static final TextAttributesKey CLASS_NAME =
      key("CLASS_NAME", DefaultLanguageHighlighterColors.CLASS_NAME);
  public static final TextAttributesKey FUNCTION_DECL =
      key("FUNCTION_DECL", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
  public static final TextAttributesKey FUNCTION_CALL =
      key("FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL);
  public static final TextAttributesKey PARAMETER =
      key("PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);
  public static final TextAttributesKey LOCAL_VARIABLE =
      key("LOCAL_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
  public static final TextAttributesKey FIELD =
      key("FIELD", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
  public static final TextAttributesKey LABEL_ARG =
      key("LABEL_ARG", DefaultLanguageHighlighterColors.LABEL);
}
