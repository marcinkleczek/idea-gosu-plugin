package gosu.gsp.resolve;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.stubs.GosuStubIndexKeys;
import java.util.*;

/** Indexed source enhancements; generic target bindings are carried into return types. */
public final class GosuEnhancements {
  private GosuEnhancements() {}

  public static String targetText(ClassDecl declaration) {
    var tokens = GosuSource.tokenize(declaration.getText());
    if (tokens.isEmpty() || !tokens.getFirst().text().equals("enhancement")) return "";
    int depth = 0;
    for (int i = 1; i < tokens.size(); i++) {
      String s = tokens.get(i).text();
      if (s.equals("<")) depth++;
      if (s.equals(">")) depth--;
      if (s.equals(":" ) && depth == 0) {
        StringBuilder target = new StringBuilder();
        for (int j = i + 1; j < tokens.size() && !tokens.get(j).text().equals("{"); j++) target.append(tokens.get(j).text());
        return target.toString();
      }
      if (s.equals("{")) break;
    }
    return "";
  }

  public static String rawName(String type) {
    int generic = type.indexOf('<');
    return generic < 0 ? type : type.substring(0, generic);
  }

  public static boolean isEnhancement(PsiElement element) {
    return element instanceof ClassDecl c && !targetText(c).isEmpty();
  }

  public static boolean visible(ClassDecl enhancement, PsiElement context) {
    String pkg = GosuSource.packageName(enhancement.getContainingFile());
    return pkg.equals("gw.lang.enhancements") || pkg.equals(GosuSource.packageName(context.getContainingFile()))
        || GosuSource.imports(context.getContainingFile()).stream().anyMatch(i ->
            i.equals(pkg + ".*") || i.equals(GosuTypes.qualifiedName(enhancement)));
  }

  public static List<GosuMembers.Member> members(GosuMembers.Type receiver, PsiElement context) {
    return members(receiver, context, false);
  }

  public static List<GosuMembers.Member> members(GosuMembers.Type receiver, PsiElement context, boolean includeUnimported) {
    if (receiver == null || receiver.staticAccess() || DumbService.isDumb(context.getProject())) return List.of();
    Set<String> names = new HashSet<>();
    if (receiver.declaration() instanceof PsiClass cls) {
      collectJavaNames(cls, names);
    } else if (receiver.declaration() instanceof ClassDecl cls) {
      names.add(cls.getName());
    }
    List<GosuMembers.Member> result = new ArrayList<>();
    Set<ClassDecl> seen = new HashSet<>();
    for (String name : names) {
      for (ClassDecl enhancement : StubIndex.getElements(GosuStubIndexKeys.ENHANCEMENTS, name,
          context.getProject(), GlobalSearchScope.allScope(context.getProject()), ClassDecl.class)) {
        if (!seen.add(enhancement) || !includeUnimported && !visible(enhancement, context)) continue;
        String target = targetText(enhancement);
        PsiElement enhanced = GosuTypes.find(rawName(target), enhancement);
        Map<String, String> bindings = new HashMap<>();
        if (enhanced instanceof PsiClass parent && receiver.declaration() instanceof PsiClass actual) {
          if (!actual.isEquivalentTo(parent) && !actual.isInheritor(parent, true)) continue;
          PsiSubstitutor sub = receiver.javaType() instanceof PsiClassType ct ? ct.resolveGenerics().getSubstitutor() : PsiSubstitutor.EMPTY;
          sub = TypeConversionUtil.getSuperClassSubstitutor(parent, actual, sub);
          int lt = target.indexOf('<'), gt = target.lastIndexOf('>');
          if (lt >= 0 && gt > lt) {
            List<String> args = GosuSignatures.splitParameters(target.substring(lt + 1, gt));
            if (args.size() != parent.getTypeParameters().length) continue;
            boolean applicable = true;
            Set<String> variables = typeVariables(enhancement);
            for (int i = 0; i < args.size(); i++) {
              PsiType bound = sub.substitute(parent.getTypeParameters()[i]);
              if (bound == null) { applicable = false; break; }
              String argument = args.get(i).trim();
              if (variables.contains(argument)) bindings.put(argument, bound.getCanonicalText());
              else {
                GosuMembers.Type required = GosuMembers.typeFromText(argument, enhancement);
                if (required == null || required.javaType() == null || !required.javaType().equals(bound)) applicable = false;
              }
            }
            if (!applicable) continue;
          }
        } else if (enhanced == null || receiver.declaration() == null || !enhanced.isEquivalentTo(receiver.declaration())) continue;
        for (FunctionDecl fn : PsiTreeUtil.findChildrenOfType(enhancement, FunctionDecl.class)) {
          if (fn.getName() == null || PsiTreeUtil.getParentOfType(fn, ClassDecl.class) != enhancement || GosuMembers.modifiers(fn).contains("private")
              || GosuMembers.modifiers(fn).contains("static") || "construct".equals(fn.getName())) continue;
          var params = PsiTreeUtil.getChildrenOfTypeAsList(fn, Param.class);
          String signature = "(" + params.stream().map(p -> p.getName() + ": " + substitute(GosuSignatures.declaredType(p), bindings))
              .reduce((a,b) -> a + ", " + b).orElse("") + ")";
          String returnType = substitute(GosuSignatures.declaredType(fn), bindings);
          result.add(new GosuMembers.Member(fn.getName(), fn, GosuMembers.typeFromText(returnType, fn), true, params.size(), signature));
        }
        for (VarDecl property : PsiTreeUtil.findChildrenOfType(enhancement, VarDecl.class)) {
          if (property.getName() == null || PsiTreeUtil.getParentOfType(property, FunctionDecl.class) != null || PsiTreeUtil.getParentOfType(property, ClassDecl.class) != enhancement
              || !property.getText().startsWith("property get") || GosuMembers.modifiers(property).contains("private")
              || GosuMembers.modifiers(property).contains("static")) continue;
          result.add(new GosuMembers.Member(property.getName(), property,
              GosuMembers.typeFromText(substitute(GosuSignatures.declaredType(property), bindings), property), false, 0, ""));
        }
      }
    }
    return result;
  }

  private static Set<String> typeVariables(ClassDecl enhancement) {
    String text = enhancement.getText();
    int lt = text.indexOf('<'), colon = text.indexOf(':');
    if (lt < 0 || lt > colon) return Set.of();
    int gt = text.indexOf('>', lt);
    if (gt < 0) return Set.of();
    Set<String> names = new HashSet<>();
    for (String argument : GosuSignatures.splitParameters(text.substring(lt + 1, gt))) names.add(argument.trim().split("\\s+")[0]);
    return names;
  }

  private static void collectJavaNames(PsiClass cls, Set<String> names) {
    if (cls.getName() == null || !names.add(cls.getName())) return;
    for (PsiClass parent : cls.getSupers()) collectJavaNames(parent, names);
  }

  private static String substitute(String type, Map<String, String> bindings) {
    if (type == null) return "";
    StringBuilder out = new StringBuilder();
    for (var token : GosuSource.tokenize(type)) out.append(bindings.getOrDefault(token.text(), token.text()));
    return out.toString();
  }
}
