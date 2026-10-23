package gosu.gsp.testing;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import java.util.*;

public final class GosuTests {
  private GosuTests() {}
  public static boolean isTest(FunctionDecl fn) {
    return fn.getName() != null && fn.getName().startsWith("test") && PsiTreeUtil.getChildrenOfTypeAsList(fn, Param.class).isEmpty()
        && Collections.disjoint(GosuMembers.modifiers(fn), Set.of("private", "protected", "static", "abstract"))
        && Set.of("", "void").contains(GosuSignatures.declaredType(fn));
  }
  public static ClassDecl testClass(PsiElement element) {
    ClassDecl cls = PsiTreeUtil.getParentOfType(element, ClassDecl.class, false);
    if (cls == null || cls.getName() == null || !cls.getName().endsWith("Test")
        || !cls.getContainingFile().getName().endsWith(".gs") || GosuMembers.modifiers(cls).contains("abstract")) return null;
    return PsiTreeUtil.findChildrenOfType(cls, FunctionDecl.class).stream()
        .anyMatch(fn -> PsiTreeUtil.getParentOfType(fn, ClassDecl.class) == cls && isTest(fn)) ? cls : null;
  }
}
