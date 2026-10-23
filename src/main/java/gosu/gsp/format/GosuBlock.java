package gosu.gsp.format;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static gosu.gsp.lexer.GosuTokenTypes.LBRACK;
import static gosu.gsp.lexer.GosuTokenTypes.LPAREN;
import static gosu.gsp.lexer.GosuTokenTypes.RBRACE;
import static gosu.gsp.lexer.GosuTokenTypes.RBRACK;
import static gosu.gsp.lexer.GosuTokenTypes.RPAREN;
import static gosu.gsp.lexer.GosuTokenTypes.SEMICOLON;

/**
 * Conservative block: normalizes only unambiguous whitespace (comma/semicolon
 * gaps, {@code =} gaps, gaps around closers, {@code name(} vs keyword gaps)
 * and indents block/paren contents. Everything else keeps the original text:
 * returning {@code null} spacing never rewrites user code.
 */
final class GosuBlock implements Block {
  private final ASTNode node;
  private final Indent indent;
  private final int parenDepth;
  private List<Block> subBlocks;

  GosuBlock(@NotNull ASTNode node, @NotNull Indent indent, int parenDepth) {
    this.node = node;
    this.indent = indent;
    this.parenDepth = parenDepth;
  }

  ASTNode node() {
    return node;
  }

  @Override
  public @NotNull Indent getIndent() {
    return indent;
  }

  @Override
  public @Nullable Wrap getWrap() {
    return null;
  }

  @Override
  public @Nullable Alignment getAlignment() {
    return null;
  }

  @Override
  public @NotNull List<Block> getSubBlocks() {
    if (subBlocks == null) {
      subBlocks = new ArrayList<>();
      int depth = 0;
      for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        IElementType type = child.getElementType();
        if (type == com.intellij.psi.TokenType.WHITE_SPACE) {
          continue;
        }
        subBlocks.add(new GosuBlock(child, indentFor(child, depth), depth));
        if (type == LPAREN || type == LBRACK) {
          depth++;
        }
        else if ((type == RPAREN || type == RBRACK) && depth > 0) {
          depth--;
        }
      }
    }
    return subBlocks;
  }

  private @NotNull Indent indentFor(ASTNode child, int depth) {
    IElementType type = child.getElementType();
    if (type == RBRACE || type == RPAREN || type == RBRACK) {
      return Indent.getNoneIndent();
    }
    if (node.getElementType() == gosu.gsp.psi.GosuElementTypes.BLOCK) {
      return Indent.getNormalIndent();
    }
    if (depth > 0) {
      return Indent.getContinuationIndent();
    }
    return Indent.getNoneIndent();
  }

  @Override
  public @Nullable Spacing getSpacing(@Nullable Block first, @NotNull Block second) {
    if (!(first instanceof GosuBlock left) || !(second instanceof GosuBlock right)) {
      return null;
    }
    GosuSpacing.Gap gap = GosuSpacing.gapFor(
        left.node().getElementType(), left.node().getText(),
        right.node().getElementType(), right.node().getText());
    if (gap == null) {
      return null;
    }
    return Spacing.createSpacing(gap.minSpaces(), gap.maxSpaces(), gap.minFeeds(),
        gap.keepBreaks(), gap.keepBlank());
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
    if (node.getElementType() == gosu.gsp.psi.GosuElementTypes.BLOCK) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    return new ChildAttributes(Indent.getNoneIndent(), null);
  }

  @Override
  public boolean isIncomplete() {
    IElementType type = node.getElementType();
    if (type == gosu.gsp.psi.GosuElementTypes.BLOCK
        || type == gosu.gsp.psi.GosuElementTypes.CLASS_DECL
        || type == gosu.gsp.psi.GosuElementTypes.FUNCTION_DECL) {
      ASTNode last = node.getLastChildNode();
      while (last != null && last.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE) {
        last = last.getTreePrev();
      }
      if (type == gosu.gsp.psi.GosuElementTypes.BLOCK) {
        return last == null || last.getElementType() != RBRACE;
      }
      return last != null && last.getElementType() != RBRACE && last.getElementType() != SEMICOLON;
    }
    return false;
  }

  @Override
  public boolean isLeaf() {
    return node.getFirstChildNode() == null;
  }

  @Override
  public @NotNull com.intellij.openapi.util.TextRange getTextRange() {
    return node.getTextRange();
  }
}
