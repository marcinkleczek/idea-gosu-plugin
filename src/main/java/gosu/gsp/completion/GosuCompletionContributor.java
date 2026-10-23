package gosu.gsp.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import gosu.gsp.lexer.GosuLexer;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.psi.GosuReference;
import gosu.gsp.resolve.GosuMembers;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

/** Keywords and scoped names, or type-aware members after a dot. */
public final class GosuCompletionContributor extends CompletionContributor {
  public GosuCompletionContributor() {
    extend(CompletionType.SMART, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
      @Override protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
        if (!inCommentOrString(parameters) && !com.intellij.openapi.project.DumbService.isDumb(parameters.getPosition().getProject())) GosuSmartCompletion.complete(parameters, result);
      }
    });
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(),
        new CompletionProvider<CompletionParameters>() {
          @Override
          protected void addCompletions(@NotNull CompletionParameters params,
              @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
            PsiElement position = params.getPosition();
            if (inCommentOrString(params)) {
              return;
            }
            if (GosuSpecificCompletion.complete(params, result)) return;
            int start = position.getTextRange().getStartOffset();
            if (GosuMembers.isMemberPosition(position.getContainingFile(), start)) {
              var receiver = GosuMembers.receiverAt(position, start);
              for (GosuMembers.Member member : GosuMembers.at(position, start)) {
                result.addElement(LookupElementBuilder.create(member.target(), member.name())
                    .withIcon(member.method() ? AllIcons.Nodes.Method : AllIcons.Nodes.Property)
                    .withTailText(member.signature() + GosuMethodOrigin.suffix(member.target(), receiver), true)
                    .withTypeText(GosuMembers.typeLabel(member))
                    .withInsertHandler((ctx, item) -> {
                      if (!member.method()) return;
                      int tail = ctx.getTailOffset();
                      CharSequence text = ctx.getDocument().getCharsSequence();
                      if (tail < text.length() && text.charAt(tail) == '(') return;
                      ctx.getDocument().insertString(tail, "()");
                      ctx.getEditor().getCaretModel().moveToOffset(tail + (member.parameterCount() == 0 ? 2 : 1));
                      if (ctx.getCompletionChar() == '(') ctx.setAddCompletionChar(false);
                    }));
              }
              for (var member : gosu.gsp.resolve.GosuEnhancements.members(receiver, position, true)) {
                var owner = com.intellij.psi.util.PsiTreeUtil.getParentOfType(member.target(), gosu.gsp.psi.GosuDecls.ClassDecl.class);
                if (owner == null || gosu.gsp.resolve.GosuEnhancements.visible(owner, position)) continue;
                String qualified = gosu.gsp.resolve.GosuTypes.qualifiedName(owner);
                if (!GosuAutoImports.canImportEnhancement(params.getOriginalFile(), qualified)) continue;
                result.addElement(LookupElementBuilder.create(member.target(), member.name())
                    .withIcon(member.method() ? AllIcons.Nodes.Method : AllIcons.Nodes.Property)
                    .withTailText(member.signature() + " — enhancement " + qualified + " (doda uses)", true)
                    .withTypeText(GosuMembers.typeLabel(member))
                    .withInsertHandler((ctx,item) -> {
                      int tail = ctx.getTailOffset();
                      if (member.method() && (tail >= ctx.getDocument().getTextLength() || ctx.getDocument().getCharsSequence().charAt(tail) != '(')) {
                        ctx.getDocument().insertString(tail, "()");
                        ctx.getEditor().getCaretModel().moveToOffset(tail + (member.parameterCount() == 0 ? 2 : 1));
                        if (ctx.getCompletionChar() == '(') ctx.setAddCompletionChar(false);
                      }
                      ctx.commitDocument();
                      GosuAutoImports.addEnhancementImport(ctx.getFile(), qualified);
                    }));
              }
              result.stopHere();
              return;
            }
            for (String keyword : GosuLexer.getKeywords()) {
              result.addElement(LookupElementBuilder.create(keyword)
                  .withTypeText("keyword"));
            }
            result.addAllElements(GosuReference.variantsFor(position));
            if (result.getPrefixMatcher().getPrefix().length() >= 2) {
              for (var candidate : GosuAutoImports.candidates(position, result.getPrefixMatcher()::prefixMatches)) {
                result.addElement(LookupElementBuilder.create(candidate.target(), candidate.name())
                    .withIcon(AllIcons.Nodes.Class).withTailText(" (" + candidate.qualifiedName() + ")", true)
                    .withInsertHandler((ctx, item) -> {
                      ctx.commitDocument();
                      GosuAutoImports.insert(ctx.getFile(), ctx.getStartOffset(), ctx.getTailOffset(), candidate.qualifiedName());
                    }));
              }
            }
          }
        });
  }

  @Override
  public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
    return typeChar == '.' || typeChar == ':';
  }

  /** The dummy identifier lives in a copy; check the original file instead. */
  private static boolean inCommentOrString(CompletionParameters params) {
    PsiElement e = params.getOriginalFile()
        .findElementAt(Math.max(0, params.getOffset() - 1));
    while (e != null && !(e instanceof com.intellij.psi.PsiFile)) {
      IElementType type = e.getNode() != null ? e.getNode().getElementType() : null;
      if (type == GosuTokenTypes.LINE_COMMENT || type == GosuTokenTypes.BLOCK_COMMENT
          || type == GosuTokenTypes.STRING || type == GosuTokenTypes.CHAR_LITERAL) {
        return true;
      }
      e = e.getParent();
    }
    return false;
  }
}
