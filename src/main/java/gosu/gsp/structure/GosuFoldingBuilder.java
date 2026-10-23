package gosu.gsp.structure;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.psi.GosuElementTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Folds classes, functions, multiline blocks/comments and `uses` runs. */
public final class GosuFoldingBuilder implements FoldingBuilder {
  @Override
  public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull ASTNode node,
      @NotNull Document document) {
    List<FoldingDescriptor> out = new ArrayList<>();
    collect(node, document, out);
    foldUsesRuns(node, document, out);
    return out.toArray(FoldingDescriptor.EMPTY_ARRAY);
  }

  private static void collect(ASTNode node, Document document, List<FoldingDescriptor> out) {
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      var type = child.getElementType();
      if (type == GosuElementTypes.CLASS_DECL
          || type == GosuElementTypes.FUNCTION_DECL
          || type == GosuElementTypes.BLOCK) {
        TextRange range = foldableRange(child);
        if (range != null && isMultiline(document, range)) {
          out.add(new FoldingDescriptor(child, range));
        }
      }
      else if (type == GosuTokenTypes.BLOCK_COMMENT) {
        TextRange range = child.getTextRange();
        if (isMultiline(document, range)) {
          out.add(new FoldingDescriptor(child, range));
        }
      }
      collect(child, document, out);
    }
  }

  /** Folds `{`…`}` (or the whole declaration) instead of trailing tokens. */
  private static TextRange foldableRange(ASTNode node) {
    ASTNode first = node.getFirstChildNode();
    ASTNode last = node.getLastChildNode();
    if (first == null || last == null) {
      return null;
    }
    int start = node.getTextRange().getStartOffset();
    int end = node.getTextRange().getEndOffset();
    // Prefer the braced range when the node ends with '}'.
    ASTNode brace = last;
    while (brace != null
        && brace.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE) {
      brace = brace.getTreePrev();
    }
    if (brace != null && brace.getElementType() == GosuTokenTypes.RBRACE) {
      ASTNode open = first;
      while (open != null && open != brace) {
        if (open.getElementType() == GosuTokenTypes.LBRACE) {
          start = open.getTextRange().getStartOffset();
          break;
        }
        open = open.getTreeNext();
      }
      end = brace.getTextRange().getEndOffset();
    }
    return new TextRange(start, end);
  }

  /** Consecutive `uses` lines fold into one region. */
  private static void foldUsesRuns(ASTNode node, Document document, List<FoldingDescriptor> out) {
    ASTNode runStart = null;
    ASTNode runEnd = null;
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == GosuElementTypes.USES_DECL) {
        if (runStart == null) {
          runStart = child;
        }
        runEnd = child;
      }
      else if (child.getElementType() != com.intellij.psi.TokenType.WHITE_SPACE) {
        if (runStart != null && runEnd != null && runStart != runEnd) {
          TextRange range = new TextRange(runStart.getTextRange().getStartOffset(),
              runEnd.getTextRange().getEndOffset());
          if (isMultiline(document, range)) {
            out.add(new FoldingDescriptor(runStart, range));
          }
        }
        runStart = null;
        runEnd = null;
      }
    }
    if (runStart != null && runEnd != null && runStart != runEnd) {
      TextRange range = new TextRange(runStart.getTextRange().getStartOffset(),
          runEnd.getTextRange().getEndOffset());
      if (isMultiline(document, range)) {
        out.add(new FoldingDescriptor(runStart, range));
      }
    }
  }

  private static boolean isMultiline(Document document, TextRange range) {
    return document.getLineNumber(range.getStartOffset()) < document.getLineNumber(range.getEndOffset());
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    var type = node.getElementType();
    if (type == GosuTokenTypes.BLOCK_COMMENT) {
      return "/*...*/";
    }
    if (type == GosuElementTypes.USES_DECL) {
      return "uses ...";
    }
    return "{...}";
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull FoldingDescriptor descriptor) {
    return false;
  }
}
