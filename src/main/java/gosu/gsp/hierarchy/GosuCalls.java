package gosu.gsp.hierarchy;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.GosuFileType;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import java.util.*;

/** Statically resolved calls only; ambiguous overloads are not presented as certain edges. */
public final class GosuCalls {
  private GosuCalls() {}
  public static PsiElement target(GosuRefExpr ref) {
    var ts = GosuSource.tokens(ref.getContainingFile());
    int open = GosuSource.before(ts, ref.getTextRange().getEndOffset()) + 1;
    if (open >= ts.size() || !ts.get(open).text().equals("(")) return null;
    List<GosuMembers.Member> candidates;
    if (GosuMembers.isMemberPosition(ref.getContainingFile(), ref.getTextOffset())) candidates = GosuMembers.at(ref, ref.getTextOffset());
    else {
      candidates = new ArrayList<>();
      var owner = PsiTreeUtil.getParentOfType(ref, ClassDecl.class);
      for (var fn : PsiTreeUtil.findChildrenOfType(ref.getContainingFile(), FunctionDecl.class)) {
        if (!Objects.equals(fn.getName(), ref.getName()) || PsiTreeUtil.getParentOfType(fn, ClassDecl.class) != owner) continue;
        var sig = GosuSignatures.of(fn);
        candidates.add(new GosuMembers.Member(fn.getName(), fn, GosuMembers.infer(fn), true, sig.parameters().size(), "(" + String.join(", ", sig.parameters()) + ")"));
      }
    }
    var matches = GosuCallTypes.select(candidates, ref.getName(), ref, ts, open, 0);
    if (matches.size() == 1) return matches.getFirst().target();
    // Named argument order need not match the declaration; only a unique callable is safe here.
    var ends = GosuCallTypes.argumentEnds(ts, open);
    if (ends != null && !ends.isEmpty() && ts.subList(open + 1, ends.getLast() + 1).stream().anyMatch(t -> t.kind() == gosu.gsp.lexer.GosuTokenTypes.LABEL)) {
      var unique = candidates.stream().filter(m -> m.method() && Objects.equals(m.name(), ref.getName()) && m.parameterCount() == ends.size()).toList();
      if (unique.size() == 1) return unique.getFirst().target();
    }
    return null;
  }
  public static List<PsiFile> files(PsiElement context) {
    List<PsiFile> result = new ArrayList<>();
    for (var vf : FileTypeIndex.getFiles(GosuFileType.INSTANCE, GlobalSearchScope.projectScope(context.getProject()))) {
      ProgressManager.checkCanceled();
      var file = PsiManager.getInstance(context.getProject()).findFile(vf);
      if (file != null) result.add(file);
    }
    if (context.getContainingFile() != null && !result.contains(context.getContainingFile())) result.add(context.getContainingFile());
    return result;
  }
  public static List<PsiElement> related(PsiElement element, boolean callers) {
    Set<PsiElement> result = new LinkedHashSet<>();
    if (callers) {
      for (var file : files(element)) for (var ref : PsiTreeUtil.findChildrenOfType(file, GosuRefExpr.class)) {
        ProgressManager.checkCanceled();
        if (element instanceof PsiNamedElement named && !Objects.equals(named.getName(), ref.getName())) continue;
        var target = target(ref);
        if (target != null && target.isEquivalentTo(element)) {
          var fn = PsiTreeUtil.getParentOfType(ref, FunctionDecl.class);
          result.add(fn == null ? file : fn);
        }
      }
    } else if (element instanceof PsiMethod method) {
      if (method.getBody() != null) for (var call : PsiTreeUtil.findChildrenOfType(method.getBody(), PsiMethodCallExpression.class)) {
        var target = call.resolveMethod();
        if (target != null) result.add(target);
      }
    } else for (var ref : PsiTreeUtil.findChildrenOfType(element, GosuRefExpr.class)) {
      ProgressManager.checkCanceled();
      var fn = PsiTreeUtil.getParentOfType(ref, FunctionDecl.class);
      if (element instanceof PsiFile ? fn != null : fn != element) continue;
      var target = target(ref);
      if (target != null) result.add(target);
    }
    return List.copyOf(result);
  }
  public static String name(PsiElement element) {
    var sig = GosuSignatures.of(element);
    if (sig == null) return element.getContainingFile().getName();
    var owner = PsiTreeUtil.getParentOfType(element, ClassDecl.class);
    return (owner == null ? element.getContainingFile().getName() : GosuTypes.qualifiedName(owner)) + " — " + sig.text();
  }
}
