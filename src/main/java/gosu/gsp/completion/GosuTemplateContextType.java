package gosu.gsp.completion;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiFile;
import gosu.gsp.GosuLanguage;
import org.jetbrains.annotations.NotNull;

/** Enables the Gosu live templates in `.gsp` files. */
public final class GosuTemplateContextType extends TemplateContextType {
  public GosuTemplateContextType() {
    super("Gosu", "Gosu");
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return file.getLanguage() == GosuLanguage.INSTANCE;
  }

  @Override
  public boolean isInContext(@NotNull TemplateActionContext context) {
    return context.getFile().getLanguage() == GosuLanguage.INSTANCE;
  }
}
