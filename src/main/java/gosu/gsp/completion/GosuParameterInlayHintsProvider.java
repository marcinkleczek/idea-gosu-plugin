package gosu.gsp.completion;

import com.intellij.codeInsight.hints.ChangeListener;
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector;
import com.intellij.codeInsight.hints.ImmediateConfigurable;
import com.intellij.codeInsight.hints.InlayHintsCollector;
import com.intellij.codeInsight.hints.InlayHintsProvider;
import com.intellij.codeInsight.hints.InlayHintsSink;
import com.intellij.codeInsight.hints.NoSettings;
import com.intellij.codeInsight.hints.SettingsKey;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.psi.GosuDecls.FunctionDecl;
import gosu.gsp.psi.GosuDecls.Param;
import gosu.gsp.psi.GosuFile;
import gosu.gsp.psi.GosuRefExpr;
import gosu.gsp.psi.GosuReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Inline parameter names for calls to same-file functions:
 * {@code rate(p)} shows {@code policy:} before {@code p}.
 * Only fires when the argument count matches the parameter count;
 * an argument that already is the parameter name gets no hint.
 */
public final class GosuParameterInlayHintsProvider implements InlayHintsProvider<NoSettings> {
  private static final SettingsKey<NoSettings> KEY = new SettingsKey<>("gosu.parameter.hints");

  @Override
  public InlayHintsCollector getCollectorFor(@NotNull PsiFile file, @NotNull Editor editor,
      @NotNull NoSettings settings, @NotNull InlayHintsSink sink) {
    return new FactoryInlayHintsCollector(editor) {
      @Override
      public boolean collect(@NotNull PsiElement element, @NotNull Editor editor,
          @NotNull InlayHintsSink sink) {
        if (element instanceof GosuRefExpr ref
            && element.getContainingFile() instanceof GosuFile) {
          hintArguments(ref, editor, getFactory(), sink);
        }
        return true;
      }
    };
  }

  @Override
  public @NotNull NoSettings createSettings() {
    return new NoSettings();
  }

  @Override
  public @NotNull String getName() {
    return "Parameter names";
  }

  @Override
  public @NotNull SettingsKey<NoSettings> getKey() {
    return KEY;
  }

  @Override
  public @Nullable String getPreviewText() {
    return "rate(policy)";
  }

  @Override
  public @NotNull ImmediateConfigurable createConfigurable(@NotNull NoSettings settings) {
    return new ImmediateConfigurable() {
      @Override
      public @NotNull JComponent createComponent(@NotNull ChangeListener listener) {
        return new JPanel();
      }
    };
  }

  private static void hintArguments(GosuRefExpr ref, Editor editor,
      PresentationFactory factory, InlayHintsSink sink) {
    if (!GosuReference.followedByParen(ref)) return;
    var signatures = GosuCalls.signatures(ref);
    // Avoid guessing parameter names across different overloads.
    if (signatures.size() != 1) return;
    List<String> params = signatures.getFirst().parameters().stream()
        .map(p -> p.contains(":") ? p.substring(0, p.indexOf(':')).trim() : p.trim()).toList();
    if (params.isEmpty()) {
      return;
    }
    List<com.intellij.openapi.util.TextRange> args = argumentRanges(ref);
    if (args == null || args.size() != params.size()) {
      return;
    }
    for (int i = 0; i < params.size(); i++) {
      String paramName = params.get(i);
      com.intellij.openapi.util.TextRange arg = args.get(i);
      if (arg == null || arg.isEmpty()) {
        continue;
      }
      String argText = editor.getDocument().getText(arg).trim();
      if (argText.equals(paramName)) {
        continue;
      }
      sink.addInlineElement(arg.getStartOffset(), false, factory.smallText(paramName + ":"));
    }
  }

  /**
   * Start offsets of top-level comma-separated chunks after the call paren,
   * or null when the parens never balance. Nested brackets and string
   * tokens never split: only depth-zero commas do.
   */
  private static @Nullable List<com.intellij.openapi.util.TextRange> argumentRanges(
      GosuRefExpr ref) {
    PsiElement open = nextSignificant(ref.getNextSibling());
    if (open == null || typeOf(open) != GosuTokenTypes.LPAREN) {
      return null;
    }
    List<com.intellij.openapi.util.TextRange> ranges = new ArrayList<>();
    int depth = 0;
    int chunkStart = -1;
    int chunkEnd = -1;
    PsiElement e = open.getNextSibling();
    while (e != null) {
      IElementType type = typeOf(e);
      if (type == GosuTokenTypes.LPAREN || type == GosuTokenTypes.LBRACK
          || type == GosuTokenTypes.LBRACE) {
        if (chunkStart < 0) {
          chunkStart = e.getTextOffset();
        }
        chunkEnd = e.getTextRange().getEndOffset();
        depth++;
      }
      else if (type == GosuTokenTypes.RPAREN || type == GosuTokenTypes.RBRACK
          || type == GosuTokenTypes.RBRACE) {
        if (type == GosuTokenTypes.RPAREN && depth == 0) {
          ranges.add(chunkStart < 0 ? null
              : new com.intellij.openapi.util.TextRange(chunkStart, chunkEnd));
          return ranges;
        }
        chunkEnd = e.getTextRange().getEndOffset();
        depth = Math.max(0, depth - 1);
      }
      else if (type == GosuTokenTypes.COMMA && depth == 0) {
        ranges.add(chunkStart < 0 ? null
            : new com.intellij.openapi.util.TextRange(chunkStart, chunkEnd));
        chunkStart = -1;
        chunkEnd = -1;
      }
      else if (!(e instanceof PsiWhiteSpace) && !isComment(e)) {
        if (chunkStart < 0) {
          chunkStart = e.getTextOffset();
        }
        chunkEnd = e.getTextRange().getEndOffset();
      }
      e = e.getNextSibling();
    }
    return null;
  }

  private static @Nullable PsiElement nextSignificant(@Nullable PsiElement e) {
    while (e instanceof PsiWhiteSpace || isComment(e)) {
      e = e.getNextSibling();
    }
    return e;
  }

  private static boolean isComment(@Nullable PsiElement e) {
    IElementType type = e == null ? null : typeOf(e);
    return type == GosuTokenTypes.LINE_COMMENT || type == GosuTokenTypes.BLOCK_COMMENT;
  }

  private static @Nullable IElementType typeOf(@NotNull PsiElement e) {
    return e.getNode() == null ? null : e.getNode().getElementType();
  }
}
