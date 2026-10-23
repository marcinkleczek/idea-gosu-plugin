package gosu.gsp.format;

import com.intellij.lang.Language;
import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.psi.codeStyle.CodeStyleConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import gosu.gsp.GosuLanguage;
import org.jetbrains.annotations.NotNull;

/** Gosu style: 2-space indent, no tabs (matches the reference-guide examples). */
public final class GosuCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new SmartIndentOptionsEditor();
  }

  @Override
  public @NotNull CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings settings,
      @NotNull CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, "Gosu") {
      @Override
      protected CodeStyleAbstractPanel createPanel(CodeStyleSettings currentSettings) {
        return new TabbedLanguageCodeStylePanel(GosuLanguage.INSTANCE,
            currentSettings, getCurrentSettings()) {
          @Override
          protected void initTabs(CodeStyleSettings settings) {
            addIndentOptionsTab(settings);
          }
        };
      }
    };
  }

  @Override
  public @NotNull Language getLanguage() {
    return GosuLanguage.INSTANCE;
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    return "uses java.util.ArrayList\n"
        + "\n"
        + "class QuotePolicy {\n"
        + "  var premium = 100\n"
        + "\n"
        + "  function rate(policy: String): String {\n"
        + "    if (policy == null) {\n"
        + "      return 'none'\n"
        + "    }\n"
        + "    return policy\n"
        + "  }\n"
        + "}\n";
  }

  @Override
  protected void customizeDefaults(@NotNull CommonCodeStyleSettings common,
      @NotNull CommonCodeStyleSettings.IndentOptions indent) {
    indent.INDENT_SIZE = 2;
    indent.TAB_SIZE = 2;
    indent.USE_TAB_CHARACTER = false;
    indent.CONTINUATION_INDENT_SIZE = 4;
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer,
      @NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.INDENT_SETTINGS) {
      consumer.showStandardOptions("Indent", "Continuation Indent", "Tab Size", "Use Tab");
    }
  }
}
