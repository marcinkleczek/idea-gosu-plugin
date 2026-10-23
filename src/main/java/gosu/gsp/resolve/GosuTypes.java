package gosu.gsp.resolve;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import gosu.gsp.stubs.GosuStubIndexKeys;

import java.util.*;

/** Class lookup respects explicit imports, current package and wildcard imports. */
public final class GosuTypes {
  private GosuTypes() {}

  public static PsiElement find(String name, PsiElement context) {
    if (name == null || name.isBlank()) return null;
    PsiFile file = context.getContainingFile();
    if (!name.contains(".")) {
      for (ClassDecl c : PsiTreeUtil.findChildrenOfType(file, ClassDecl.class)) {
        if (name.equals(c.getName())) return c;
      }
    }
    if (DumbService.isDumb(context.getProject())) return null;
    if (name.contains(".")) return exact(name, context);
    List<PsiElement> explicit = new ArrayList<>();
    for (String imported : GosuSource.imports(file)) {
      if (imported.endsWith("." + name)) add(explicit, exact(imported, context));
    }
    if (!explicit.isEmpty()) return unique(explicit);
    String pkg = GosuSource.packageName(file);
    PsiElement samePackage = exact(pkg.isEmpty() ? name : pkg + "." + name, context);
    if (samePackage != null) return samePackage;
    List<PsiElement> candidates = new ArrayList<>();
    add(candidates, exact("java.lang." + name, context));
    for (String imported : GosuSource.imports(file)) {
      if (imported.endsWith(".*")) add(candidates, exact(imported.substring(0, imported.length() - 1) + name, context));
    }
    return unique(candidates);
  }

  private static void add(List<PsiElement> list, PsiElement e) {
    if (e != null && !list.contains(e)) list.add(e);
  }

  private static PsiElement unique(List<PsiElement> list) { return list.size() == 1 ? list.getFirst() : null; }

  public static String qualifiedName(ClassDecl declaration) {
    String pkg = GosuSource.packageName(declaration.getContainingFile());
    ClassDecl outer = PsiTreeUtil.getParentOfType(declaration, ClassDecl.class);
    return outer != null ? qualifiedName(outer) + "." + declaration.getName()
        : pkg.isEmpty() ? declaration.getName() : pkg + "." + declaration.getName();
  }

  private static PsiElement exact(String name, PsiElement context) {
    String shortName = name.substring(name.lastIndexOf('.') + 1);
    var scope = GlobalSearchScope.allScope(context.getProject());
    List<PsiElement> matches = new ArrayList<>();
    for (ClassDecl c : StubIndex.getElements(GosuStubIndexKeys.CLASSES, shortName,
        context.getProject(), scope, ClassDecl.class)) {
      if (name.equals(qualifiedName(c))) add(matches, c);
    }
    if (!matches.isEmpty()) return unique(matches);
    return JavaPsiFacade.getInstance(context.getProject()).findClass(name, GlobalSearchScope.allScope(context.getProject()));
  }
}
