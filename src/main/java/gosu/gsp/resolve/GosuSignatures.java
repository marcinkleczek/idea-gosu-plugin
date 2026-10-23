package gosu.gsp.resolve;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuDecls.*;
import java.util.*;

/** Shared signatures for completion, documentation and parameter info. */
public final class GosuSignatures {
  private GosuSignatures() {}
  public record Callable(PsiElement target, String name, List<String> parameters, String returnType) {
    public String text() { return name + "(" + String.join(", ", parameters) + ")" + (returnType.isBlank() ? "" : " : " + returnType); }
  }

  public static String declaredType(PsiElement declaration) {
    var ts = GosuSource.tokenize(declaration.getText());
    int parens = 0;
    for (int i = 0; i < ts.size(); i++) {
      String s = ts.get(i).text();
      if (s.equals("(")) parens++;
      if (s.equals(")")) parens--;
      if (s.equals("{") && parens == 0) break;
      if (s.equals(":") && parens == 0) {
        StringBuilder type = new StringBuilder();
        for (int j = i + 1; j < ts.size(); j++) {
          String part = ts.get(j).text();
          if (Set.of("{", "=", ";", "as").contains(part)) break;
          type.append(part);
        }
        return type.toString();
      }
    }
    return "";
  }

  public static Callable of(PsiElement target) {
    if (target instanceof PsiMethod method) {
      return new Callable(method, method.getName(), Arrays.stream(method.getParameterList().getParameters())
          .map(p -> p.getName() + ": " + p.getType().getPresentableText()).toList(),
          method.getReturnType() == null ? "" : method.getReturnType().getPresentableText());
    }
    if (target instanceof FunctionDecl fn) {
      return new Callable(fn, fn.getName(), PsiTreeUtil.getChildrenOfTypeAsList(fn, Param.class).stream()
          .map(p -> p.getName() + (declaredType(p).isEmpty() ? "" : ": " + declaredType(p))).toList(), declaredType(fn));
    }
    return null;
  }

  public static Callable of(GosuMembers.Member member) {
    String signature = member.signature();
    List<String> types = splitParameters(signature.length() < 2 ? "" : signature.substring(1, signature.length() - 1));
    if (member.target() instanceof PsiMethod method) {
      List<String> named = new ArrayList<>();
      var params = method.getParameterList().getParameters();
      for (int i = 0; i < types.size(); i++) named.add((i < params.length ? params[i].getName() : "arg" + i) + ": " + types.get(i));
      types = named;
    }
    return new Callable(member.target(), member.name(), types, GosuMembers.typeLabel(member));
  }

  public static List<String> splitParameters(String text) {
    if (text.isBlank()) return List.of();
    List<String> result = new ArrayList<>();
    int depth = 0, start = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '<' || c == '(' || c == '[') depth++;
      if (c == '>' || c == ')' || c == ']') depth--;
      if (c == ',' && depth == 0) { result.add(text.substring(start, i).trim()); start = i + 1; }
    }
    result.add(text.substring(start).trim());
    return result;
  }
}
