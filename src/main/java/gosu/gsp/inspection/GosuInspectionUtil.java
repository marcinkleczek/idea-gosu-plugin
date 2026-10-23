package gosu.gsp.inspection;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuFile;
import gosu.gsp.psi.GosuNamedDecl;
import gosu.gsp.psi.GosuRefExpr;
import org.jetbrains.annotations.Nullable;

/** Shared helpers for the Gosu inspections. */
final class GosuInspectionUtil {
  private GosuInspectionUtil() {
  }

  /** True when some same-name reference in the file resolves to this declaration. */
  static boolean hasResolvingReference(GosuNamedDecl decl) {
    String name = decl.getName();
    if (name == null || !(decl.getContainingFile() instanceof GosuFile file)) {
      return true; // be conservative: unknown means "used"
    }
    for (GosuRefExpr ref : PsiTreeUtil.findChildrenOfType(file, GosuRefExpr.class)) {
      if (!name.equals(ref.getName()) || ref.getReference() == null) {
        continue;
      }
      if (decl.equals(ref.getReference().resolve())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Line range covering the whole statement when the line holds nothing else:
   * only blanks before the anchor, and only blanks (or the statement's own
   * tail up to `;`) after it. Null when deleting the line would eat neighbors.
   */
  static @Nullable TextRange safeLineRange(PsiElement anchor) {
    PsiFile file = anchor.getContainingFile();
    if (file == null) {
      return null;
    }
    String text = file.getText();
    int start = anchor.getTextRange().getStartOffset();
    int lineStart = text.lastIndexOf('\n', start - 1) + 1;
    if (!text.substring(lineStart, start).isBlank()) {
      return null;
    }
    int end = anchor.getTextRange().getEndOffset();
    // Allow the statement tail (initializer, type, `;`) on the same line.
    int lineEnd = text.indexOf('\n', end);
    if (lineEnd < 0) {
      lineEnd = text.length();
    }
    String rest = text.substring(end, lineEnd);
    String tail = rest;
    int semi = rest.indexOf(';');
    if (semi >= 0) {
      tail = rest.substring(0, semi + 1);
      rest = rest.substring(semi + 1);
    }
    if (tail.contains("{") || tail.contains("}")) {
      return null; // never cut through braces (blocks, initializers)
    }
    if (!rest.isBlank() && !isCommentOnly(rest)) {
      return null;
    }
    int deleteEnd = lineEnd < text.length() ? lineEnd + 1 : lineEnd;
    return new TextRange(lineStart, deleteEnd);
  }

  private static boolean isCommentOnly(String rest) {
    String t = rest.trim();
    return t.startsWith("//") || (t.startsWith("/*") && t.endsWith("*/") && !t.contains("\n"));
  }
}
