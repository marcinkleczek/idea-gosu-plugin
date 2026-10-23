package gosu.gsp.editor;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/** Surround With (Ctrl+Alt+T): braces, `if`, `try/catch`. */
public final class GosuSurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] SURROUNDERS = {
      new Wrapper("{...}", "{\n", "\n}", -1),
      new Wrapper("if", "if () {\n", "\n}", 4),
      new Wrapper("try / catch", "try {\n", "\n} catch (e : Exception) {\n}", -1),
  };

  @Override
  public PsiElement @NotNull [] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    if (startOffset >= endOffset) {
      return PsiElement.EMPTY_ARRAY;
    }
    PsiElement first = file.findElementAt(startOffset);
    PsiElement last = file.findElementAt(endOffset - 1);
    if (first == null || last == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    PsiElement common = PsiTreeUtil.findCommonParent(first, last);
    return common == null ? PsiElement.EMPTY_ARRAY : new PsiElement[]{common};
  }

  @Override
  public Surrounder @NotNull [] getSurrounders() {
    return SURROUNDERS;
  }

  @Override
  public boolean isExclusive() {
    return false;
  }

  /**
   * Wraps the range with fixed prefix/suffix. {@code caretInPrefix} &gt;= 0
   * moves the caret there (e.g. inside the `if ()` parens).
   */
  private static final class Wrapper implements Surrounder {
    private final String description;
    private final String prefix;
    private final String suffix;
    private final int caretInPrefix;

    Wrapper(String description, String prefix, String suffix, int caretInPrefix) {
      this.description = description;
      this.prefix = prefix;
      this.suffix = suffix;
      this.caretInPrefix = caretInPrefix;
    }

    @Override
    public String getTemplateDescription() {
      return description;
    }

    @Override
    public boolean isApplicable(PsiElement @NotNull [] elements) {
      return elements.length > 0;
    }

    @Override
    public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor,
        PsiElement @NotNull [] elements) {
      TextRange range = elements[0].getTextRange();
      int start = range.getStartOffset();
      int end = range.getEndOffset();
      com.intellij.openapi.application.WriteAction.run(() -> {
        editor.getDocument().insertString(end, suffix);
        editor.getDocument().insertString(start, prefix);
      });
      int caret = caretInPrefix >= 0 ? start + caretInPrefix : end + prefix.length();
      editor.getCaretModel().moveToOffset(Math.min(caret, editor.getDocument().getTextLength()));
      return new TextRange(start, end + prefix.length() + suffix.length());
    }
  }
}
