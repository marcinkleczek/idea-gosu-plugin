package gosu.gsp.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import gosu.gsp.GosuFileType;
import gosu.gsp.GosuLanguage;
import org.jetbrains.annotations.NotNull;

public final class GosuFile extends PsiFileBase {
  public GosuFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, GosuLanguage.INSTANCE);
  }

  @Override
  public @NotNull FileType getFileType() {
    return GosuFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "Gosu File";
  }
}
