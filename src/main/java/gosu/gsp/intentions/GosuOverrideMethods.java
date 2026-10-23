package gosu.gsp.intentions;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import java.util.*;

/** Candidates and source generation shared by Alt+Enter and the Generate menu. */
public final class GosuOverrideMethods {
  private GosuOverrideMethods() {}
  public record Candidate(String key, String display, String source, boolean required) {
    @Override public String toString() { return display; }
  }
  public static List<Candidate> candidates(ClassDecl owner) {
    Map<String, Candidate> result = new LinkedHashMap<>();
    collect(owner, owner, result, new HashSet<>());
    Set<String> implemented = new HashSet<>();
    for (FunctionDecl fn : PsiTreeUtil.findChildrenOfType(owner, FunctionDecl.class)) {
      if (PsiTreeUtil.getParentOfType(fn, ClassDecl.class) != owner) continue;
      var signature = GosuSignatures.of(fn);
      implemented.add(key(signature.name(), signature.parameters()));
    }
    for (VarDecl prop : PsiTreeUtil.findChildrenOfType(owner, VarDecl.class)) {
      var accessor = GosuPropertyAccessor.of(prop);
      if (accessor != null && PsiTreeUtil.getParentOfType(prop, ClassDecl.class) == owner) implemented.add(accessor + ":" + prop.getName());
    }
    result.keySet().removeAll(implemented);
    return result.values().stream().sorted(Comparator.comparing(Candidate::display)).toList();
  }
  private static void collect(ClassDecl child, ClassDecl target, Map<String,Candidate> result, Set<PsiElement> seen) {
    if (!seen.add(child)) return;
    var tokens = GosuSource.tokenize(child.getText());
    boolean parents = false;
    StringBuilder name = new StringBuilder();
    int depth = 0;
    List<String> types = new ArrayList<>();
    for (var token : tokens) {
      String part = token.text();
      if (part.equals("{")) { if (!name.isEmpty()) types.add(name.toString()); break; }
      if (part.equals("extends") || part.equals("implements")) { if (!name.isEmpty()) types.add(name.toString()); name.setLength(0); parents = true; continue; }
      if (!parents) continue;
      if (part.equals(",") && depth == 0) { types.add(name.toString()); name.setLength(0); continue; }
      if (part.equals("<")) depth++;
      if (part.equals(">")) depth--;
      name.append(part);
    }
    for (String text : types) {
      var type = GosuMembers.typeFromText(text, child);
      if (type == null) continue;
      if (type.declaration() instanceof PsiClass javaClass) {
        var sub = type.javaType() instanceof PsiClassType ct ? ct.resolveGenerics().getSubstitutor() : PsiSubstitutor.EMPTY;
        for (PsiMethod method : javaClass.getAllMethods()) {
          if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.FINAL)
              || method.hasModifierProperty(PsiModifier.PRIVATE) || !(method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED))) continue;
          // Method type parameters with bounds need a richer generator; do not emit invalid stubs.
          if (method.getTypeParameters().length > 0) continue;
          var inherited = TypeConversionUtil.getSuperClassSubstitutor(method.getContainingClass(), javaClass, sub);
          List<String> parameters = Arrays.stream(method.getParameterList().getParameters())
              .map(p -> p.getName() + " : " + inherited.substitute(p.getType()).getCanonicalText().replace("...", "[]")).toList();
          String ret = inherited.substitute(method.getReturnType()).getCanonicalText();
          boolean required = method.hasModifierProperty(PsiModifier.ABSTRACT);
          String methodName = method.getName();
          String header = "function " + methodName + "(" + String.join(", ", parameters) + ") : " + ret;
          String key = key(methodName, parameters);
          if (parameters.isEmpty() && !ret.equals("void") && methodName.startsWith("get") && methodName.length() > 3) {
            key = "GET:" + methodName.substring(3); header = "property get " + methodName.substring(3) + "() : " + ret;
          } else if (parameters.isEmpty() && ret.equals("boolean") && methodName.startsWith("is") && methodName.length() > 2) {
            key = "GET:" + methodName.substring(2); header = "property get " + methodName.substring(2) + "() : boolean";
          } else if (parameters.size() == 1 && ret.equals("void") && methodName.startsWith("set") && methodName.length() > 3) {
            key = "SET:" + methodName.substring(3); header = "property set " + methodName.substring(3) + "(" + parameters.getFirst() + ")";
          }
          result.putIfAbsent(key, new Candidate(key, header, stub(header), required));
        }
      } else if (type.declaration() instanceof ClassDecl parent) {
        for (FunctionDecl fn : PsiTreeUtil.findChildrenOfType(parent, FunctionDecl.class)) {
          if (PsiTreeUtil.getParentOfType(fn, ClassDecl.class) != parent || fn.getName() == null || fn.getName().equals("construct")) continue;
          var mods = GosuMembers.modifiers(fn);
          if (mods.contains("private") || mods.contains("static") || mods.contains("final")) continue;
          var sig = GosuSignatures.of(fn);
          List<String> parameters = new ArrayList<>();
          for (Param param : PsiTreeUtil.getChildrenOfTypeAsList(fn, Param.class)) parameters.add(param.getName() + " : " + qualified(GosuSignatures.declaredType(param), parent));
          String header = "function " + sig.name() + "(" + String.join(", ", parameters) + ") : " + qualified(sig.returnType().isBlank() ? "void" : sig.returnType(), parent);
          String key = key(sig.name(), parameters);
          result.putIfAbsent(key, new Candidate(key, header, stub(header), mods.contains("abstract") || parent.getText().startsWith("interface")));
        }
        collect(parent, target, result, seen);
      }
    }
  }
  private static String qualified(String text, PsiElement context) {
    var type = GosuMembers.typeFromText(text, context);
    if (type != null && type.javaType() != null) return type.javaType().getCanonicalText();
    if (type != null && type.declaration() instanceof ClassDecl cls) return GosuTypes.qualifiedName(cls);
    return text;
  }
  private static String key(String name, List<String> parameters) {
    return name + "(" + parameters.stream().map(p -> {
      String type = p.substring(p.indexOf(':') + 1).trim();
      return type.replaceAll("[a-zA-Z_$][\\w$]*\\.", "").replace(" ", "");
    }).reduce((a,b) -> a + "," + b).orElse("") + ")";
  }
  private static String stub(String header) {
    return "\n  override " + header + " {\n    throw new java.lang.UnsupportedOperationException(\"Not implemented yet\")\n  }\n";
  }
  public static int insertionOffset(ClassDecl owner) {
    for (PsiElement child : owner.getChildren()) {
      if (child.getNode().getElementType() == GosuElementTypes.BLOCK && child.getLastChild() != null && "}".equals(child.getLastChild().getText())) return child.getLastChild().getTextOffset();
    }
    return -1;
  }
}
