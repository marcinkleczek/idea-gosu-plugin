package gosu.gsp.format;

import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GosuFormattingModelBuilder implements FormattingModelBuilder {
  @Override
  public @NotNull FormattingModel createModel(@NotNull FormattingContext context) {
    ASTNode node = context.getNode();
    PsiElement psi = context.getPsiElement();
    CodeStyleSettings settings = context.getCodeStyleSettings();
    PsiFile file = psi.getContainingFile();
    return FormattingModelProvider.createFormattingModelForPsiFile(
        file, new GosuBlock(node, Indent.getNoneIndent(), 0), settings);
  }

  @Override
  public @Nullable TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return null;
  }
}
