package gosu.gsp.completion;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import java.util.*;

/** Token-based call lookup: strings/comments do not affect argument nesting. */
public final class GosuCalls {
  private GosuCalls() {}
  public record Call(PsiElement reference, int openOffset, int parameterIndex, List<GosuSignatures.Callable> signatures) {}

  public static Call at(PsiFile file, int offset) {
    var ts = GosuSource.tokens(file);
    int depth = 0;
    for (int i = GosuSource.before(ts, offset); i >= 0; i--) {
      String s = ts.get(i).text();
      if (s.equals(")") || s.equals("]") || s.equals("}")) depth++;
      else if (s.equals("(") || s.equals("[") || s.equals("{")) {
        if (depth == 0) {
          if (!s.equals("(") || i == 0) return null;
          PsiElement leaf = file.findElementAt(ts.get(i - 1).start());
          PsiElement ref = reference(leaf);
          if (ref == null) return null;
          List<GosuSignatures.Callable> signatures = signatures(ref);
          if (signatures.isEmpty()) return null;
          int parameter = 0, nested = 0;
          for (int j = i + 1; j < ts.size() && ts.get(j).start() < offset; j++) {
            String part = ts.get(j).text();
            if (part.equals(",") && nested == 0) parameter++;
            if (part.equals("(") || part.equals("[") || part.equals("{")) nested++;
            if (part.equals(")") || part.equals("]") || part.equals("}")) nested--;
          }
          return new Call(ref, ts.get(i).start(), parameter, signatures);
        }
        depth--;
      } else if (depth == 0 && s.equals(";")) return null;
    }
    return null;
  }

  private static PsiElement reference(PsiElement leaf) {
    for (PsiElement e = leaf; e != null && !(e instanceof PsiFile); e = e.getParent()) {
      if (e instanceof GosuRefExpr || e instanceof GosuTypeRef) return e;
    }
    return null;
  }

  public static List<GosuSignatures.Callable> signatures(PsiElement ref) {
    String name = ref instanceof GosuRefExpr r ? r.getName() : ref instanceof GosuTypeRef t ? t.getName() : null;
    if (name == null) return List.of();
    if (ref instanceof GosuTypeRef && ref.getReference() != null) {
      PsiElement type = ref.getReference().resolve();
      if (type instanceof PsiClass cls) return Arrays.stream(cls.getConstructors()).filter(m -> m.hasModifierProperty(PsiModifier.PUBLIC))
          .map(GosuSignatures::of).toList();
      if (type instanceof ClassDecl cls) return PsiTreeUtil.findChildrenOfType(cls, FunctionDecl.class).stream()
          .filter(fn -> "construct".equals(fn.getName())).map(GosuSignatures::of).toList();
    }
    if (GosuMembers.isMemberPosition(ref.getContainingFile(), ref.getTextOffset())) {
      return GosuMembers.at(ref, ref.getTextOffset()).stream().filter(m -> m.method() && name.equals(m.name()))
          .map(GosuSignatures::of).toList();
    }
    ClassDecl owner = PsiTreeUtil.getParentOfType(ref, ClassDecl.class);
    return PsiTreeUtil.findChildrenOfType(ref.getContainingFile(), FunctionDecl.class).stream()
        .filter(fn -> name.equals(fn.getName()) && PsiTreeUtil.getParentOfType(fn, ClassDecl.class) == owner)
        .map(GosuSignatures::of).toList();
  }
}
