package gosu.gsp.editor;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import gosu.gsp.psi.GosuDecls.FunctionDecl;
import gosu.gsp.psi.GosuFile;
import gosu.gsp.psi.GosuNamedDecl;
import gosu.gsp.psi.GosuRefExpr;
import gosu.gsp.psi.GosuTypeRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ctrl+W progression: identifier → string content → whole string/comment →
 * declaration header → whole declaration.
 */
public final class GosuExtendWordSelectionHandler implements ExtendWordSelectionHandler {

  @Override
  public boolean canSelect(@NotNull PsiElement element) {
    return element.getContainingFile() instanceof GosuFile;
  }

  @Override
  public @Nullable List<TextRange> select(@NotNull PsiElement element,
      @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> ranges = new ArrayList<>();
    if (element instanceof GosuRefExpr || element instanceof GosuTypeRef) {
      addNameRange(element, ranges);
    }
    else if (element.getNode() != null
        && element.getNode().getElementType() == GosuTokenTypes.IDENTIFIER) {
      ranges.add(element.getTextRange());
    }
    else if (element.getNode() != null
        && (element.getNode().getElementType() == GosuTokenTypes.STRING)) {
      addStringRanges(element, ranges);
    }
    else if (element.getNode() != null
        && (element.getNode().getElementType() == GosuTokenTypes.LINE_COMMENT
        || element.getNode().getElementType() == GosuTokenTypes.BLOCK_COMMENT)) {
      ranges.add(element.getTextRange());
    }
    else if (element instanceof FunctionDecl || element instanceof ClassDecl) {
      addNameRange(element, ranges);
      ranges.add(element.getTextRange());
    }
    else if (element instanceof GosuNamedDecl decl) {
      if (decl.getNameIdentifier() != null) {
        ranges.add(decl.getNameIdentifier().getTextRange());
      }
      ranges.add(element.getTextRange());
    }
    return ranges.isEmpty() ? null : ranges;
  }

  private static void addNameRange(PsiElement element, List<TextRange> ranges) {
    for (PsiElement child : element.getChildren()) {
      if (child.getNode() != null
          && child.getNode().getElementType() == GosuTokenTypes.IDENTIFIER) {
        ranges.add(child.getTextRange());
        return;
      }
    }
  }

  private static void addStringRanges(PsiElement element, List<TextRange> ranges) {
    TextRange full = element.getTextRange();
    String text = element.getText();
    if (text.length() > 2
        && (text.startsWith("\"") || text.startsWith("'"))
        && text.endsWith(text.substring(0, 1))) {
      ranges.add(new TextRange(full.getStartOffset() + 1, full.getEndOffset() - 1));
    }
    ranges.add(full);
  }
}
