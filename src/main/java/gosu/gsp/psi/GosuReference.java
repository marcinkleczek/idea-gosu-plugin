package gosu.gsp.psi;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.resolve.GosuMembers;
import gosu.gsp.resolve.GosuTypes;
import gosu.gsp.resolve.GosuSource;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import gosu.gsp.psi.GosuDecls.FunctionDecl;
import gosu.gsp.psi.GosuDecls.Param;
import gosu.gsp.psi.GosuDecls.VarDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves local identifiers, imported/project types and statically known members.
 * Local lookup:
 * locals and parameters by scope (nearest preceding wins, members and
 * functions hoisted).
 */
public final class GosuReference extends PsiReferenceBase<PsiElement> {
  public GosuReference(@NotNull PsiElement element) {
    super(element, nameRange(element), false);
  }

  private static @NotNull TextRange nameRange(PsiElement element) {
    ASTNode id = element.getNode().findChildByType(GosuTokenTypes.IDENTIFIER);
    if (id == null && element instanceof GosuRefExpr) id = element.getNode().findChildByType(GosuTokenTypes.KEYWORD);
    if (id == null) {
      return TextRange.EMPTY_RANGE;
    }
    // PsiReferenceBase wants the range relative to the element.
    return id.getTextRange().shiftLeft(element.getTextRange().getStartOffset());
  }

  private @Nullable String refName() {
    PsiElement e = getElement();
    if (e instanceof GosuRefExpr ref) {
      return ref.getName();
    }
    if (e instanceof GosuTypeRef ref) {
      return ref.getName();
    }
    return null;
  }

  @Override
  public @Nullable PsiElement resolve() {
    String name = refName();
    if (name == null) return null;
    PsiElement element = getElement();
    if (element instanceof GosuTypeRef) {
      var ts = GosuSource.tokens(element.getContainingFile());
      int end = GosuSource.before(ts, element.getTextRange().getEndOffset());
      int start = end;
      while (start >= 2 && ts.get(start - 1).text().equals(".")
          && ts.get(start - 2).kind() == GosuTokenTypes.IDENTIFIER) start -= 2;
      StringBuilder qualified = new StringBuilder();
      for (int i = start; i <= end; i++) qualified.append(ts.get(i).text());
      return GosuTypes.find(qualified.toString(), element);
    }
    if (GosuMembers.isMemberPosition(element.getContainingFile(), element.getTextOffset())) {
      return GosuMembers.resolve(element, name);
    }
    return findUnqualified(name, element);
  }

  public static @Nullable PsiElement findUnqualified(String name, PsiElement context) {
    PsiElement local = findVisibleValue(name, context);
    if (local != null) return local;
    if (context.getContainingFile() instanceof GosuFile file) {
      ClassDecl owner = PsiTreeUtil.getParentOfType(context, ClassDecl.class);
      for (FunctionDecl fn : PsiTreeUtil.findChildrenOfType(file, FunctionDecl.class)) {
        ClassDecl fnOwner = PsiTreeUtil.getParentOfType(fn, ClassDecl.class);
        if (name.equals(fn.getName()) && (fnOwner == null || fnOwner == owner)) return fn;
      }
    }
    return GosuTypes.find(name, context);
  }

  @Override
  public Object @NotNull [] getVariants() {
    return variantsFor(getElement()).toArray();
  }

  /** Completion variants for a reference or a caret position: scope names first. */
  public static @NotNull List<com.intellij.codeInsight.lookup.LookupElement> variantsFor(
      @NotNull PsiElement position) {
    Map<String, PsiElement> seen = new LinkedHashMap<>();
    for (Candidate c : valueCandidates(position)) {
      if (c.offset() <= position.getTextOffset()) {
        seen.putIfAbsent(c.name(), c.target());
      }
    }
    if (position.getContainingFile() instanceof GosuFile file) {
      for (FunctionDecl decl : PsiTreeUtil.findChildrenOfType(file, FunctionDecl.class)) {
        if (decl.getName() != null) {
          seen.putIfAbsent(decl.getName(), decl);
        }
      }
      for (ClassDecl decl : PsiTreeUtil.findChildrenOfType(file, ClassDecl.class)) {
        if (decl.getName() != null) {
          seen.putIfAbsent(decl.getName(), decl);
        }
      }
    }
    List<com.intellij.codeInsight.lookup.LookupElement> out = new ArrayList<>();
    for (Map.Entry<String, PsiElement> entry : seen.entrySet()) {
      out.add(LookupElementBuilder.create(entry.getKey())
          .withIcon(iconFor(entry.getValue()))
          .withTypeText(kindFor(entry.getValue())));
    }
    return out;
  }

  private static @NotNull String kindFor(PsiElement e) {
    if (e instanceof FunctionDecl) {
      return "function";
    }
    if (e instanceof ClassDecl) {
      return "class";
    }
    if (e instanceof Param) {
      return "param";
    }
    return "var";
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) {
    if (element instanceof com.intellij.psi.PsiNameIdentifierOwner owner
        && owner.getName() != null) {
      ASTNode id = getElement().getNode().findChildByType(GosuTokenTypes.IDENTIFIER);
      if (id != null && id.getPsi() != null) {
        id.getPsi().replace(
            GosuElementFactory.createIdentifier(getElement().getProject(), owner.getName()));
      }
    }
    return getElement();
  }

  // --- scope search ---

  private record Candidate(String name, int offset, PsiElement target, boolean memberHoisted) {
  }

  private static @NotNull List<Candidate> valueCandidates(PsiElement ref) {
    List<Candidate> out = new ArrayList<>();
    if (!(ref.getContainingFile() instanceof GosuFile file)) {
      return out;
    }
    for (VarDecl decl : PsiTreeUtil.findChildrenOfType(file, VarDecl.class)) {
      if (decl.getName() == null || !varVisibleFrom(decl, ref)) {
        continue;
      }
      out.add(new Candidate(decl.getName(), decl.getTextOffset(), decl, decl.isMember()));
    }
    for (Param param : PsiTreeUtil.findChildrenOfType(file, Param.class)) {
      if (param.getName() == null || !paramVisibleFrom(param, ref)) {
        continue;
      }
      out.add(new Candidate(param.getName(), param.getTextOffset(), param, false));
    }
    return out;
  }

  private static @Nullable PsiElement findVisibleValue(String name, PsiElement ref) {
    PsiElement bestBefore = null;
    int bestOffset = -1;
    PsiElement memberFallback = null;
    for (Candidate c : valueCandidates(ref)) {
      if (!c.name().equals(name)) {
        continue;
      }
      if (c.offset() <= ref.getTextOffset()) {
        if (c.offset() > bestOffset) {
          bestOffset = c.offset();
          bestBefore = c.target();
        }
      }
      else if (c.memberHoisted()) {
        memberFallback = c.target();
      }
    }
    return bestBefore != null ? bestBefore : memberFallback;
  }

  private static boolean paramVisibleFrom(Param param, PsiElement ref) {
    GosuLambda lambda = PsiTreeUtil.getParentOfType(param, GosuLambda.class);
    if (lambda != null) return PsiTreeUtil.isAncestor(lambda, ref, false);
    FunctionDecl paramFn = PsiTreeUtil.getParentOfType(param, FunctionDecl.class, false);
    return paramFn != null && paramFn == PsiTreeUtil.getParentOfType(ref, FunctionDecl.class, false);
  }

  private static boolean varVisibleFrom(VarDecl decl, PsiElement ref) {
    FunctionDecl declFn = PsiTreeUtil.getParentOfType(decl, FunctionDecl.class, false);
    if (declFn != null && declFn != PsiTreeUtil.getParentOfType(ref, FunctionDecl.class, false)) return false;
    // Locals cannot escape their lexical block, including sibling branches.
    PsiElement scope = decl.getParent();
    if (!decl.isMember() && !PsiTreeUtil.isAncestor(scope, ref, false)) return false;
    if (decl.isMember()) {
      ClassDecl declClass = PsiTreeUtil.getParentOfType(decl, ClassDecl.class, false);
      return declClass != null
          && declClass == PsiTreeUtil.getParentOfType(ref, ClassDecl.class, false);
    }
    return true; // top-level script variable: visible file-wide
  }

  public static boolean afterDot(PsiElement e) {
    PsiElement prev = skipTrivia(e.getPrevSibling(), false);
    return prev != null && prev.getNode().getElementType() == GosuTokenTypes.DOT;
  }

  public static boolean followedByParen(PsiElement e) {
    PsiElement next = skipTrivia(e.getNextSibling(), true);
    return next != null && next.getNode().getElementType() == GosuTokenTypes.LPAREN;
  }

  private static @Nullable PsiElement skipTrivia(@Nullable PsiElement e, boolean forward) {
    while (e != null && (e instanceof PsiWhiteSpace
        || e.getNode().getElementType() == GosuTokenTypes.LINE_COMMENT
        || e.getNode().getElementType() == GosuTokenTypes.BLOCK_COMMENT)) {
      e = forward ? e.getNextSibling() : e.getPrevSibling();
    }
    return e;
  }

  private static @Nullable Icon iconFor(PsiElement e) {
    if (e instanceof FunctionDecl) {
      return AllIcons.Nodes.Method;
    }
    if (e instanceof ClassDecl) {
      return AllIcons.Nodes.Class;
    }
    if (e instanceof Param) {
      return AllIcons.Nodes.Parameter;
    }
    return AllIcons.Nodes.Variable;
  }
}
