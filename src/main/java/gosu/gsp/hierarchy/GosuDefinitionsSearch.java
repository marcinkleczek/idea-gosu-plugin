package gosu.gsp.hierarchy;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.*;
import gosu.gsp.psi.GosuDecls.*;

public final class GosuDefinitionsSearch implements QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {
  @Override public boolean execute(DefinitionsScopedSearch.SearchParameters query, Processor<? super PsiElement> consumer) {
    return ReadAction.compute(() -> {
      if (DumbService.isDumb(query.getProject())) return true;
      PsiElement base = query.getElement();
      if (!(base instanceof ClassDecl || base instanceof FunctionDecl || base instanceof VarDecl || base instanceof PsiClass || base instanceof PsiMethod)) return true;
      for (PsiElement target : GosuHierarchy.implementations(base, query.isCheckDeep())) {
        // Java's own search contributes Java targets; this executor supplies Gosu.
        if (!(target instanceof ClassDecl || target instanceof FunctionDecl || target instanceof VarDecl)) continue;
        var file = target.getContainingFile().getVirtualFile();
        if (query.getScope() instanceof GlobalSearchScope global && (file == null || !global.contains(file))) continue;
        if (query.getScope() instanceof LocalSearchScope local && ArraysSupport.outside(local, target)) continue;
        if (!consumer.process(target)) return false;
      }
      return true;
    });
  }
  private static class ArraysSupport {
    static boolean outside(LocalSearchScope scope, PsiElement target) {
      return java.util.Arrays.stream(scope.getScope()).noneMatch(e -> PsiTreeUtil.isAncestor(e, target, false));
    }
  }
}
