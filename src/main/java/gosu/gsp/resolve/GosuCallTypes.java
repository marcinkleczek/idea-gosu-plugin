package gosu.gsp.resolve;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import java.util.*;

/** Conservative overload selection: unknown arguments never break a tie. */
public final class GosuCallTypes {
  private GosuCallTypes() {}
  public static List<GosuMembers.Member> select(List<GosuMembers.Member> members, String name,
      PsiElement context, List<GosuSource.Token> tokens, int open, int depth) {
    var ends = argumentEnds(tokens, open);
    if (ends == null) return List.of();
    List<GosuMembers.Type> actual = ends.stream().map(i -> GosuMembers.expression(context, tokens, i, depth + 1)).toList();
    List<GosuMembers.Member> best = new ArrayList<>();
    int bestScore = -1;
    for (var m : members) {
      if (!m.method() || !name.equals(m.name())) continue;
      boolean varargs = m.target() instanceof PsiMethod jm && jm.isVarArgs();
      if (varargs ? actual.size() < m.parameterCount() - 1 : actual.size() != m.parameterCount()) continue;
      List<String> params = GosuSignatures.splitParameters(m.signature().substring(1, m.signature().length() - 1));
      int score = varargs ? 0 : 1;
      boolean valid = true;
      PsiSubstitutor bindings = PsiSubstitutor.EMPTY;
      PsiMethod method = m.target() instanceof PsiMethod jm ? jm : null;
      if (method != null) {
        for (int i = 0; i < actual.size(); i++) {
          PsiType formal = method.getParameterList().getParameters()[Math.min(i, m.parameterCount() - 1)].getType();
          if (formal instanceof PsiEllipsisType e) formal = e.getComponentType();
          if (actual.get(i) != null && actual.get(i).javaType() != null) bindings = bind(formal, actual.get(i).javaType(), bindings, method, context);
        }
      }
      for (int i = 0; i < actual.size(); i++) {
        String text = params.get(Math.min(i, params.size() - 1));
        if (text.contains(":")) text = text.substring(text.indexOf(':') + 1).trim();
        if (varargs && i >= params.size() - 1) text = text.replace("...", "").replaceAll("\\[\\]$", "");
        GosuMembers.Type expected = GosuMembers.typeFromText(text, m.target());
        if (method != null && method.getTypeParameters().length > 0) {
          PsiType formal = method.getParameterList().getParameters()[Math.min(i, m.parameterCount() - 1)].getType();
          if (formal instanceof PsiEllipsisType e) formal = e.getComponentType();
          expected = GosuMembers.javaType(bindings.substitute(formal));
        }
        var value = actual.get(i);
        if (expected == null || value == null || expected.javaType() == null || value.javaType() == null) continue;
        if (expected.javaType().equals(value.javaType())) score += 4;
        else if (TypeConversionUtil.isAssignable(expected.javaType(), value.javaType())) score += 1;
        else { valid = false; break; }
      }
      if (!valid) continue;
      var result = m;
      if (method != null && method.getTypeParameters().length > 0 && !bindings.getSubstitutionMap().isEmpty()) {
        result = new GosuMembers.Member(m.name(), m.target(), GosuMembers.javaType(bindings.substitute(method.getReturnType())), true, m.parameterCount(), m.signature());
      }
      if (score > bestScore) { best.clear(); bestScore = score; }
      if (score == bestScore) best.add(result);
    }
    return best;
  }

  private static PsiSubstitutor bind(PsiType formal, PsiType actual, PsiSubstitutor sub, PsiMethod method, PsiElement context) {
    if (formal instanceof PsiClassType f && f.resolve() instanceof PsiTypeParameter parameter
        && parameter.getOwner() == method) {
      if (actual instanceof PsiPrimitiveType p) actual = p.getBoxedType(context);
      if (actual != null) {
        PsiType previous = sub.getSubstitutionMap().get(parameter);
        if (previous == null || previous.equals(actual)) return sub.put(parameter, actual);
        // Conflicting bindings remain unknown rather than inventing a return type.
        return sub.put(parameter, PsiType.getJavaLangObject(context.getManager(), context.getResolveScope()));
      }
    }
    if (formal instanceof PsiArrayType f && actual instanceof PsiArrayType a) return bind(f.getComponentType(), a.getComponentType(), sub, method, context);
    if (formal instanceof PsiClassType f && actual instanceof PsiClassType a && f.resolve() != null && a.resolve() != null) {
      PsiClass parent = f.resolve(), child = a.resolve();
      if (child.isEquivalentTo(parent) || child.isInheritor(parent, true)) {
        PsiSubstitutor actualSub = TypeConversionUtil.getSuperClassSubstitutor(parent, child, a.resolveGenerics().getSubstitutor());
        PsiType[] formalArgs = f.getParameters();
        for (int i = 0; i < Math.min(formalArgs.length, parent.getTypeParameters().length); i++) {
          PsiType value = actualSub.substitute(parent.getTypeParameters()[i]);
          if (value != null) sub = bind(formalArgs[i], value, sub, method, context);
        }
      }
    }
    if (formal instanceof PsiWildcardType w && w.getBound() != null) return bind(w.getBound(), actual, sub, method, context);
    return sub;
  }

  public static List<Integer> argumentEnds(List<GosuSource.Token> tokens, int open) {
    List<Integer> result = new ArrayList<>();
    int depth = 0;
    for (int i = open + 1; i < tokens.size(); i++) {
      String s = tokens.get(i).text();
      if (depth == 0 && s.equals(")")) { if (i > open + 1) result.add(i - 1); return result; }
      if (depth == 0 && s.equals(",")) result.add(i - 1);
      if (Set.of("(", "[", "{").contains(s)) depth++;
      if (Set.of(")", "]", "}").contains(s)) depth--;
    }
    return null;
  }
}
