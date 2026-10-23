package gosu.gsp.editor;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuFile;
import gosu.gsp.psi.GosuRefExpr;
import gosu.gsp.psi.GosuTypeRef;
import gosu.gsp.psi.GosuUsesDecl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Optimize {@code uses} imports: drop unused and duplicated lines, sort the
 * block alphabetically. Sorting only happens when every line between the
 * first and last {@code uses} is itself a {@code uses} line or blank —
 * otherwise only safe whole-line deletions run, so comments and code
 * in between are never touched.
 */
public final class GosuImportOptimizer implements ImportOptimizer {

  @Override
  public boolean supports(@NotNull PsiFile file) {
    return file instanceof GosuFile;
  }

  @Override
  public @NotNull Runnable processFile(@NotNull PsiFile file) {
    List<GosuUsesDecl> uses =
        new ArrayList<>(PsiTreeUtil.findChildrenOfType(file, GosuUsesDecl.class));
    if (uses.isEmpty()) {
      return () -> {
      };
    }
    Set<String> names = new HashSet<>();
    for (GosuRefExpr ref : PsiTreeUtil.findChildrenOfType(file, GosuRefExpr.class)) {
      if (ref.getName() != null) {
        names.add(ref.getName());
      }
    }
    for (GosuTypeRef ref : PsiTreeUtil.findChildrenOfType(file, GosuTypeRef.class)) {
      if (ref.getName() != null) {
        names.add(ref.getName());
      }
    }

    Set<String> seen = new HashSet<>();
    List<GosuUsesDecl> kept = new ArrayList<>();
    List<GosuUsesDecl> dropped = new ArrayList<>();
    for (GosuUsesDecl decl : uses) {
      String qualified = decl.getQualifiedName().trim();
      if (qualified.isEmpty() || qualified.contains("*")) {
        kept.add(decl); // wildcard or unparseable: never touch
        continue;
      }
      String last = qualified.substring(qualified.lastIndexOf('.') + 1);
      if (!seen.add(qualified) || (!names.contains(last) && !gosu.gsp.completion.GosuAutoImports.preserveImport(decl))) {
        dropped.add(decl);
      }
      else {
        kept.add(decl);
      }
    }

    List<String> currentOrder = uses.stream()
        .map(decl -> decl.getQualifiedName().trim())
        .collect(Collectors.toList());
    List<String> keptOrder = kept.stream()
        .map(decl -> decl.getQualifiedName().trim())
        .collect(Collectors.toList());
    if (keptOrder.equals(currentOrder)) {
      return () -> {
      };
    }

    Document document =
        PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return () -> {
      };
    }
    if (canSortBlock(document, uses)) {
      List<String> sorted = kept.stream()
          .map(decl -> decl.getQualifiedName().trim())
          .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
          .map(qualified -> "uses " + qualified)
          .collect(Collectors.toList());
      int start = document.getLineStartOffset(
          document.getLineNumber(uses.get(0).getTextOffset()));
      int end = document.getLineEndOffset(
          document.getLineNumber(uses.get(uses.size() - 1).getTextOffset()));
      if (sorted.isEmpty()) {
        // Remove the whole block including its line break when possible.
        int endLine = end;
        if (endLine < document.getTextLength()
            && document.getCharsSequence().charAt(endLine) == '\n') {
          endLine++;
        }
        int finalEnd = endLine;
        return () -> document.deleteString(start, finalEnd);
      }
      String replacement = String.join("\n", sorted);
      return () -> document.replaceString(start, end, replacement);
    }

    // Mixed content: delete only dropped lines that hold nothing else.
    List<TextRange> deletions = new ArrayList<>();
    for (GosuUsesDecl decl : dropped) {
      int line = document.getLineNumber(decl.getTextOffset());
      String lineText = document.getText(
          new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));
      if (lineText.matches("\\s*uses\\s+[\\w.]+\\s*")) {
        int start = document.getLineStartOffset(line);
        int end = document.getLineEndOffset(line);
        if (end < document.getTextLength() && document.getCharsSequence().charAt(end) == '\n') {
          end++;
        }
        deletions.add(new TextRange(start, end));
      }
    }
    if (deletions.isEmpty()) {
      return () -> {
      };
    }
    return () -> {
      deletions.sort((a, b) -> Integer.compare(b.getStartOffset(), a.getStartOffset()));
      for (TextRange range : deletions) {
        if (range.getEndOffset() <= document.getTextLength()) {
          document.deleteString(range.getStartOffset(), range.getEndOffset());
        }
      }
    };
  }

  /** True when the span holds only {@code uses} lines and blank lines. */
  private static boolean canSortBlock(Document document, List<GosuUsesDecl> uses) {
    int first = document.getLineNumber(uses.get(0).getTextOffset());
    int last = document.getLineNumber(uses.get(uses.size() - 1).getTextOffset());
    for (int line = first; line <= last; line++) {
      String text = document.getText(
          new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));
      if (!text.matches("\\s*(uses\\s+[\\w.]+\\s*)?")) {
        return false;
      }
    }
    return true;
  }
}
