package gosu.gsp;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class GosuFileType extends LanguageFileType {
  public static final GosuFileType INSTANCE = new GosuFileType();

  private GosuFileType() {
    super(GosuLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "Gosu GSP";
  }

  @Override
  public @NotNull String getDescription() {
    return "Gosu scripts, classes and enhancements";
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "gsp";
  }

  @Override
  public @Nullable Icon getIcon() {
    return IconLoader.getIcon("/icons/gosu.svg", GosuFileType.class);
  }
}
