package gosu.gsp.highlight;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import gosu.gsp.GosuFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public final class GosuColorSettingsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
      new AttributesDescriptor("Keyword", DefaultLanguageHighlighterColors.KEYWORD),
      new AttributesDescriptor("String", DefaultLanguageHighlighterColors.STRING),
      new AttributesDescriptor("Number", DefaultLanguageHighlighterColors.NUMBER),
      new AttributesDescriptor("Line comment", DefaultLanguageHighlighterColors.LINE_COMMENT),
      new AttributesDescriptor("Block comment", DefaultLanguageHighlighterColors.BLOCK_COMMENT),
      new AttributesDescriptor("Annotation", DefaultLanguageHighlighterColors.METADATA),
      new AttributesDescriptor("Operator", DefaultLanguageHighlighterColors.OPERATION_SIGN),
      new AttributesDescriptor("Class name", GosuHighlightKeys.CLASS_NAME),
      new AttributesDescriptor("Function declaration", GosuHighlightKeys.FUNCTION_DECL),
      new AttributesDescriptor("Function call", GosuHighlightKeys.FUNCTION_CALL),
      new AttributesDescriptor("Parameter", GosuHighlightKeys.PARAMETER),
      new AttributesDescriptor("Local variable", GosuHighlightKeys.LOCAL_VARIABLE),
      new AttributesDescriptor("Field / property", GosuHighlightKeys.FIELD),
      new AttributesDescriptor("Named argument", GosuHighlightKeys.LABEL_ARG),
  };

  private static final Map<String, TextAttributesKey> TAGS = Map.of(
      "cls", GosuHighlightKeys.CLASS_NAME,
      "func", GosuHighlightKeys.FUNCTION_DECL,
      "call", GosuHighlightKeys.FUNCTION_CALL,
      "param", GosuHighlightKeys.PARAMETER,
      "local", GosuHighlightKeys.LOCAL_VARIABLE,
      "field", GosuHighlightKeys.FIELD,
      "label", GosuHighlightKeys.LABEL_ARG);

  @Override
  public @Nullable Icon getIcon() {
    return GosuFileType.INSTANCE.getIcon();
  }

  @Override
  public @NotNull SyntaxHighlighter getHighlighter() {
    return new GosuSyntaxHighlighter();
  }

  @Override
  public @NotNull String getDemoText() {
    return "package demo\n"
        + "\n"
        + "uses java.util.ArrayList\n"
        + "\n"
        + "// single-line comment\n"
        + "/* block comment\n"
        + "   spanning lines */\n"
        + "@Stateless\n"
        + "class <cls>QuotePolicy</cls> {\n"
        + "  var <field>premium</field> = 42 + 0xFF + 1.5e3\n"
        + "  var <field>code</field> = 'x'\n"
        + "\n"
        + "  function <func>rate</func>(<param>policy</param>: String): String {\n"
        + "    var <local>msg</local> = \"Hello, ${<local>policy</local>}!\"\n"
        + "    for (<local>n</local> in names) {\n"
        + "      <call>print</call>(<local>n</local>)\n"
        + "    }\n"
        + "    if (<param>policy</param> == null or <param>policy</param> == \"\") {\n"
        + "      return 'none'\n"
        + "    }\n"
        + "    return <local>msg</local>\n"
        + "  }\n"
        + "}\n"
        + "\n"
        + "var <local>myFileContainer</local> = new <cls>FileContainer</cls>() {\n"
        + "  <label>:DestFile</label> = jarFile,\n"
        + "  <label>:Update</label> = true\n"
        + "}\n"
        + "<call>print</call>(<local>myFileContainer</local>.<field>DestFile</field>)\n";
  }

  @Override
  public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return TAGS;
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  public @NotNull String getDisplayName() {
    return "Gosu";
  }
}
