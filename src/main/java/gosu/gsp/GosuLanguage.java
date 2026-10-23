package gosu.gsp;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public final class GosuLanguage extends Language {
  public static final GosuLanguage INSTANCE = new GosuLanguage();

  private GosuLanguage() {
    super("Gosu");
  }

  @Override
  public @NotNull String getDisplayName() {
    return "Gosu";
  }
}
