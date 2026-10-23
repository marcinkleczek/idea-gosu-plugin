package gosu.gsp.hierarchy;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import gosu.gsp.stubs.GosuStubIndexKeys;
import java.util.*;

/** Indexed Gosu descendants and resolved ancestor signatures, including Java interfaces. */
public final class GosuHierarchy {
  private GosuHierarchy() {}
  public static List<String> parentTexts(String source) {
    List<String> parents = new ArrayList<>();
    boolean collecting = false;
    int depth = 0;
    StringBuilder name = new StringBuilder();
    for (var token : GosuSource.tokenize(source)) {
      String part = token.text();
      if (part.equals("enhancement")) return List.of();
      if (part.equals("{")) break;
      if ((part.equals("extends") || part.equals("implements")) && depth == 0) {
        if (!name.isEmpty()) parents.add(name.toString());
        name.setLength(0); collecting = true; continue;
      }
      if (!collecting) {
        if (part.equals("<")) depth++;
        if (part.equals(">")) depth--;
        continue;
      }
      if (part.equals(",") && depth == 0) { parents.add(name.toString()); name.setLength(0); continue; }
      if (part.equals("<")) depth++;
      if (part.equals(">")) depth--;
      name.append(part);
    }
    if (!name.isEmpty()) parents.add(name.toString());
    return parents;
  }
  public static List<GosuMembers.Type> parentTypes(PsiElement cls) {
    if (cls instanceof ClassDecl gosu) return parentTexts(gosu.getText()).stream()
        .map(t -> GosuMembers.typeFromText(t, gosu)).filter(Objects::nonNull).toList();
    if (cls instanceof PsiClass java) return Arrays.stream(java.getSuperTypes()).map(GosuMembers::javaType).toList();
    return List.of();
  }
  public static List<PsiElement> parents(PsiElement cls) {
    return parentTypes(cls).stream().map(GosuMembers.Type::declaration).filter(Objects::nonNull).toList();
  }
  public static List<PsiElement> children(PsiElement cls) {
    if (!(cls instanceof PsiNamedElement named) || named.getName() == null || DumbService.isDumb(cls.getProject())) return List.of();
    Set<PsiElement> result = new LinkedHashSet<>();
    for (ClassDecl child : StubIndex.getElements(GosuStubIndexKeys.SUPERTYPES, named.getName(), cls.getProject(), GlobalSearchScope.projectScope(cls.getProject()), ClassDecl.class)) {
      ProgressManager.checkCanceled();
      if (!child.isEquivalentTo(cls) && parents(child).stream().anyMatch(p -> p.isEquivalentTo(cls))) result.add(child);
    }
    if (cls instanceof PsiClass java) result.addAll(ClassInheritorsSearch.search(java, GlobalSearchScope.projectScope(cls.getProject()), false).findAll());
    return List.copyOf(result);
  }
  public static List<PsiElement> descendants(PsiElement cls) {
    Set<PsiElement> found = new LinkedHashSet<>();
    Deque<PsiElement> queue = new ArrayDeque<>(children(cls));
    while (!queue.isEmpty()) {
      ProgressManager.checkCanceled();
      PsiElement next = queue.removeFirst();
      if (next.isEquivalentTo(cls) || !found.add(next)) continue;
      queue.addAll(children(next));
    }
    return List.copyOf(found);
  }
  public static PsiElement owner(PsiElement member) {
    return member instanceof PsiMember java ? java.getContainingClass() : PsiTreeUtil.getParentOfType(member, ClassDecl.class);
  }
  private static List<PsiElement> declaredMembers(PsiElement cls) {
    if (cls instanceof PsiClass java) return List.of(java.getMethods());
    if (cls instanceof ClassDecl gosu) return PsiTreeUtil.findChildrenOfAnyType(gosu, FunctionDecl.class, VarDecl.class).stream()
        .filter(m -> owner(m) == gosu && PsiTreeUtil.getParentOfType(m, FunctionDecl.class) == null).map(m -> (PsiElement)m).toList();
    return List.of();
  }
  private static boolean inheritable(PsiElement member) {
    if (member instanceof PsiMethod m) return !m.isConstructor() && !m.hasModifierProperty(PsiModifier.PRIVATE) && !m.hasModifierProperty(PsiModifier.STATIC);
    return member instanceof PsiNamedElement n && n.getName() != null && !n.getName().equals("construct")
        && Collections.disjoint(GosuMembers.modifiers(member), Set.of("private", "static"));
  }
  private static String canonical(String text, PsiElement context, Map<String,String> bindings) {
    for (var b : bindings.entrySet()) text = text.replaceAll("\\b" + java.util.regex.Pattern.quote(b.getKey()) + "\\b", java.util.regex.Matcher.quoteReplacement(b.getValue()));
    var type = GosuMembers.typeFromText(text, context);
    if (type == null) return text;
    if (type.javaType() != null) return type.javaType().getCanonicalText();
    if (type.declaration() instanceof ClassDecl cls) return GosuTypes.qualifiedName(cls);
    return text;
  }
  private static String signature(PsiElement member, GosuMembers.Type containingType) {
    if (!inheritable(member)) return null;
    if (member instanceof PsiMethod method) {
      var cls = containingType != null && containingType.declaration() instanceof PsiClass c ? c : method.getContainingClass();
      var sub = containingType != null && containingType.javaType() instanceof PsiClassType ct ? ct.resolveGenerics().getSubstitutor() : PsiSubstitutor.EMPTY;
      if (cls != null && method.getContainingClass() != null) sub = TypeConversionUtil.getSuperClassSubstitutor(method.getContainingClass(), cls, sub);
      List<String> params = new ArrayList<>();
      for (var p : method.getParameterList().getParameters()) params.add(sub.substitute(p.getType()).getCanonicalText().replace("...", "[]"));
      String name = method.getName();
      if (params.isEmpty() && name.startsWith("get") && name.length() > 3 && !PsiTypes.voidType().equals(method.getReturnType())) return "get:" + name.substring(3);
      if (params.isEmpty() && name.startsWith("is") && name.length() > 2 && PsiTypes.booleanType().equals(method.getReturnType())) return "get:" + name.substring(2);
      if (params.size() == 1 && name.startsWith("set") && name.length() > 3 && PsiTypes.voidType().equals(method.getReturnType())) return "set:" + name.substring(3);
      return name + params;
    }
    var accessor = GosuPropertyAccessor.of(member);
    if (accessor != null) return accessor.name().toLowerCase(Locale.ROOT) + ":" + ((VarDecl)member).getName();
    if (!(member instanceof FunctionDecl fn)) return null;
    Map<String,String> bindings = containingType == null ? Map.of() : containingType.bindings();
    return fn.getName() + PsiTreeUtil.getChildrenOfTypeAsList(fn, Param.class).stream()
        .map(p -> canonical(GosuSignatures.declaredType(p), p, bindings)).toList();
  }
  public static List<PsiElement> superMembers(PsiElement member) {
    String signature = signature(member, null);
    PsiElement owner = owner(member);
    if (signature == null || owner == null) return List.of();
    List<PsiElement> found = new ArrayList<>();
    Deque<GosuMembers.Type> pending = new ArrayDeque<>(parentTypes(owner));
    Set<PsiElement> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      ProgressManager.checkCanceled();
      var type = pending.removeFirst();
      if (type.declaration() == null || !seen.add(type.declaration())) continue;
      for (PsiElement candidate : declaredMembers(type.declaration())) {
        if (signature.equals(signature(candidate, type))) found.add(candidate);
      }
      for (var parent : parentTypes(type.declaration())) {
        // Carry Java substitutions across multiple levels of inheritance.
        if (type.javaType() instanceof PsiClassType ct && parent.javaType() != null) {
          parent = GosuMembers.javaType(ct.resolveGenerics().getSubstitutor().substitute(parent.javaType()));
        } else if (!type.bindings().isEmpty() && parent.declaration() instanceof ClassDecl) {
          Map<String,String> bound = new LinkedHashMap<>();
          parent.bindings().forEach((k,v) -> bound.put(k, type.bindings().getOrDefault(v, v)));
          parent = new GosuMembers.Type(parent.declaration(), null, false, bound);
        }
        if (parent != null) pending.add(parent);
      }
    }
    return found;
  }
  public static List<PsiElement> implementations(PsiElement element, boolean deep) {
    if (element instanceof ClassDecl || element instanceof PsiClass) return deep ? descendants(element) : children(element);
    PsiElement owner = owner(element);
    if (owner == null || !inheritable(element)) return List.of();
    List<PsiElement> result = new ArrayList<>();
    for (PsiElement child : deep ? descendants(owner) : children(owner)) {
      for (PsiElement member : declaredMembers(child)) {
        if (superMembers(member).stream().anyMatch(m -> m.isEquivalentTo(element))) result.add(member);
      }
    }
    return result;
  }
  public static String name(PsiElement cls) {
    if (cls instanceof ClassDecl gosu) return GosuTypes.qualifiedName(gosu);
    if (cls instanceof PsiClass java) return Objects.toString(java.getQualifiedName(), java.getName());
    return cls.getText();
  }
}
