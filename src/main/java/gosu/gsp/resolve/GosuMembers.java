package gosu.gsp.resolve;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;

import java.util.*;

/** Conservative type inference for literals, declarations, constructors and member chains. */
public final class GosuMembers {
  private GosuMembers() {}
  public record Type(PsiElement declaration, PsiType javaType, boolean staticAccess, Map<String, String> bindings) {
    public Type(PsiElement declaration, PsiType javaType, boolean staticAccess) { this(declaration, javaType, staticAccess, Map.of()); }
    Type instance() { return new Type(declaration, javaType, false, bindings); }
  }
  public record Member(String name, PsiElement target, Type type, boolean method,
                       int parameterCount, String signature) {}

  public static boolean isMemberPosition(PsiFile file, int offset) {
    List<GosuSource.Token> ts = GosuSource.tokens(file);
    int i = GosuSource.before(ts, offset);
    return i >= 0 && GosuSource.dot(ts.get(i));
  }

  public static Type receiverAt(PsiElement context, int memberOffset) {
    var ts = GosuSource.tokens(context.getContainingFile());
    int dot = GosuSource.before(ts, memberOffset);
    return dot < 0 || !GosuSource.dot(ts.get(dot)) ? null : expression(context, ts, dot - 1, 0);
  }

  public static List<Member> at(PsiElement context, int memberOffset) {
    List<GosuSource.Token> ts = GosuSource.tokens(context.getContainingFile());
    int dot = GosuSource.before(ts, memberOffset);
    if (dot < 0 || !GosuSource.dot(ts.get(dot))) return List.of();
    return members(expression(context, ts, dot - 1, 0), context, new HashSet<>());
  }

  public static PsiElement resolve(PsiElement context, String name) {
    List<GosuSource.Token> ts = GosuSource.tokens(context.getContainingFile());
    int end = GosuSource.before(ts, context.getTextRange().getEndOffset());
    int arity = end + 1 < ts.size() && ts.get(end + 1).text().equals("(")
        ? argumentCount(ts, end + 1) : -1;
    List<Member> candidates = arity >= 0
        ? GosuCallTypes.select(at(context, context.getTextOffset()), name, context, ts, end + 1, 0)
        : matching(at(context, context.getTextOffset()), name, arity);
    return candidates.size() == 1 ? candidates.getFirst().target() : null;
  }

  private static List<Member> matching(List<Member> members, String name, int arity) {
    return members.stream().filter(m -> m.name().equals(name)
        && (arity < 0 ? !m.method() : m.method() && m.parameterCount() == arity)).toList();
  }

  public static Type infer(PsiElement declaration) { return declaredType(declaration, 0); }

  public static Type typeFromText(String text, PsiElement context) {
    if (text == null || text.isBlank()) return null;
    if (!(context.getContainingFile() instanceof GosuFile)) {
      try { return javaType(JavaPsiFacade.getElementFactory(context.getProject()).createTypeFromText(text, context)); }
      catch (com.intellij.util.IncorrectOperationException ignored) { return null; }
    }
    var tokens = GosuSource.tokenize(text);
    return readType(context, tokens, 0, tokens.size());
  }

  private static Type named(String name, PsiElement context, boolean staticAccess) {
    PsiType primitive = switch (name) {
      case "int" -> PsiTypes.intType(); case "long" -> PsiTypes.longType();
      case "double" -> PsiTypes.doubleType(); case "float" -> PsiTypes.floatType();
      case "boolean" -> PsiTypes.booleanType(); case "char" -> PsiTypes.charType();
      case "byte" -> PsiTypes.byteType(); case "short" -> PsiTypes.shortType();
      case "void" -> PsiTypes.voidType(); default -> null;
    };
    if (primitive != null) return javaType(primitive);
    PsiElement c = GosuTypes.find(name, context);
    if (c instanceof PsiClass jc) {
      return new Type(jc, JavaPsiFacade.getElementFactory(context.getProject()).createType(jc), staticAccess);
    }
    return c == null ? null : new Type(c, null, staticAccess);
  }

  public static Type javaType(PsiType type) {
    if (type == null) return null;
    PsiClass c = type instanceof PsiClassType ct ? ct.resolve() : null;
    return new Type(c, type, false);
  }

  public static Type expression(PsiElement context, List<GosuSource.Token> ts, int end, int depth) {
    if (end < 0 || depth > 20) return null;
    ProgressManager.checkCanceled();
    var token = ts.get(end);
    // The lexer keeps ? and . as separate tokens for null-safe access.
    if (token.text().equals("?")) return expression(context, ts, end - 1, depth + 1);
    if (token.kind() == GosuTokenTypes.NUMBER) {
      String n = token.text().toLowerCase(Locale.ROOT).replace("_", "");
      if (n.endsWith("bi")) return named("java.math.BigInteger", context, false);
      if (n.endsWith("bd")) return named("java.math.BigDecimal", context, false);
      boolean radix = n.startsWith("0x") || n.startsWith("0b");
      String primitive = n.endsWith("l") ? "long" : !radix && n.endsWith("f") ? "float"
          : !radix && (n.endsWith("d") || n.contains(".") || n.contains("e")) ? "double" : "int";
      return named(primitive, context, false);
    }
    if (token.text().equals("true") || token.text().equals("false")) return named("boolean", context, false);
    if (token.kind() == GosuTokenTypes.CHAR_LITERAL) return named("char", context, false);
    if (token.kind() == GosuTokenTypes.STRING) {
      return named("java.lang.String", context, false);
    }
    if (token.text().equals("}")) {
      int open = opening(ts, end, "{", "}");
      if (open >= 0) {
        // Typed constructor initializers preserve their declared element type.
        int close = open - 1;
        if (close >= 0 && ts.get(close).text().equals(")")) return expression(context, ts, close, depth + 1);
        List<Type> items = new ArrayList<>();
        int nested = 0;
        for (int i = open + 1; i <= end; i++) {
          String part = ts.get(i).text();
          if (nested == 0 && (part.equals("->") || part.equals(":"))) return null;
          if (nested == 0 && (part.equals(",") || i == end) && i > open + 1) items.add(expression(context, ts, i - 1, depth + 1));
          if (Set.of("(", "[", "{").contains(part)) nested++;
          if (Set.of(")", "]", "}").contains(part)) nested--;
        }
        if (!items.isEmpty() && items.getFirst() != null && items.getFirst().javaType() != null
            && items.stream().allMatch(t -> t != null && items.getFirst().javaType().equals(t.javaType()))) {
          PsiType item = items.getFirst().javaType();
          if (item instanceof PsiPrimitiveType p) item = p.getBoxedType(context);
          if (item != null) return typeFromText("java.util.List<" + item.getCanonicalText() + ">", context);
        }
      }
      return null;
    }
    if (token.text().equals("]")) {
      int open = opening(ts, end, "[", "]");
      Type array = expression(context, ts, open - 1, depth + 1);
      if (array == null) return null;
      if (array.javaType() instanceof PsiArrayType a) return javaType(a.getComponentType());
      return collectionElement(array, context);
    }
    if (token.text().equals(")")) {
      int open = opening(ts, end, "(", ")");
      if (open < 0) return null;
      int nameEnd = open - 1;
      int typeEnd = nameEnd;
      if (nameEnd >= 0 && ts.get(nameEnd).text().equals(">")) {
        nameEnd = opening(ts, nameEnd, "<", ">") - 1;
      }
      int nameStart = qualifiedStart(ts, nameEnd);
      if (nameStart > 0 && ts.get(nameStart - 1).text().equals("new")) {
        return readType(context, ts, nameStart, typeEnd + 1);
      }
      if (nameEnd >= 0 && (ts.get(nameEnd).kind() == GosuTokenTypes.IDENTIFIER || ts.get(nameEnd).kind() == GosuTokenTypes.KEYWORD)) {
        String name = ts.get(nameEnd).text();
        if (nameEnd > 0 && GosuSource.dot(ts.get(nameEnd - 1))) {
          Type receiver = expression(context, ts, nameEnd - 2, depth + 1);
          var matches = GosuCallTypes.select(members(receiver, context, new HashSet<>()), name, context, ts, open, depth);
          return commonType(matches);
        }
        PsiElement fn = GosuReference.findUnqualified(name, context);
        return fn instanceof FunctionDecl ? declaredType(fn, depth + 1) : null;
      }
      return expression(context, ts, end - 1, depth + 1); // parenthesized expression
    }
    if (token.text().equals("this")) {
      ClassDecl owner = PsiTreeUtil.getParentOfType(context, ClassDecl.class);
      return owner == null ? null : new Type(owner, null, false);
    }
    if (token.kind() != GosuTokenTypes.IDENTIFIER) return null;
    if (end > 0 && GosuSource.dot(ts.get(end - 1))) {
      Type receiver = expression(context, ts, end - 2, depth + 1);
      var matches = matching(members(receiver, context, new HashSet<>()), token.text(), -1);
      if (!matches.isEmpty()) return commonType(matches);
      int start = qualifiedStart(ts, end);
      return named(join(ts, start, end + 1), context, true);
    }
    PsiElement value = GosuReference.findUnqualified(token.text(), context);
    if (value instanceof ClassDecl || value instanceof PsiClass) return named(token.text(), context, true);
    return value == null ? null : declaredType(value, depth + 1);
  }

  private static Type commonType(List<Member> members) {
    if (members.isEmpty()) return null;
    Type type = members.getFirst().type();
    return type != null && members.stream().allMatch(m -> type.equals(m.type())) ? type : null;
  }

  private static int qualifiedStart(List<GosuSource.Token> ts, int end) {
    int i = end;
    while (i >= 2 && ts.get(i - 1).text().equals(".")
        && ts.get(i - 2).kind() == GosuTokenTypes.IDENTIFIER) i -= 2;
    return i;
  }

  private static String join(List<GosuSource.Token> ts, int start, int end) {
    StringBuilder result = new StringBuilder();
    for (int i = Math.max(0, start); i < end; i++) result.append(ts.get(i).text());
    return result.toString();
  }

  private static int opening(List<GosuSource.Token> ts, int end, String open, String close) {
    int depth = 0;
    for (int i = end; i >= 0; i--) {
      if (ts.get(i).text().equals(close)) depth++;
      if (ts.get(i).text().equals(open) && --depth == 0) return i;
    }
    return -1;
  }

  private static int argumentCount(List<GosuSource.Token> ts, int open) {
    int depth = 0, count = 0;
    boolean hasArgument = false;
    for (int i = open + 1; i < ts.size(); i++) {
      String s = ts.get(i).text();
      if (s.equals(")") && depth == 0) return hasArgument ? count + 1 : 0;
      if (s.equals(",") && depth == 0) count++;
      if (s.equals("(") || s.equals("[") || s.equals("{")) depth++;
      if (s.equals(")") || s.equals("]") || s.equals("}")) depth--;
      hasArgument = true;
    }
    return -2;
  }

  private static Type readType(PsiElement context, List<GosuSource.Token> ts, int start, int limit) {
    if (start >= limit) return null;
    int end = start + 1;
    while (end + 1 < limit && ts.get(end).text().equals(".")) end += 2;
    Type base = named(join(ts, start, end), context, false);
    if (base == null) return null;
    // Preserve Java generic arguments so List<String>.get(0) has String members.
    if (end < limit && ts.get(end).text().equals("<") && base.declaration() instanceof PsiClass jc) {
      List<PsiType> args = new ArrayList<>();
      int argStart = end + 1, nesting = 0;
      for (int i = argStart; i < limit; i++) {
        String text = ts.get(i).text();
        if (text.equals("<")) nesting++;
        if ((text.equals(",") || text.equals(">")) && nesting == 0) {
          Type arg = readType(context, ts, argStart, i);
          if (arg == null || arg.javaType() == null) return base;
          args.add(arg.javaType());
          argStart = i + 1;
          if (text.equals(">")) {
            if (args.size() == jc.getTypeParameters().length) {
              PsiSubstitutor sub = PsiSubstitutor.EMPTY;
              for (int p = 0; p < args.size(); p++) sub = sub.put(jc.getTypeParameters()[p], args.get(p));
              base = new Type(jc, JavaPsiFacade.getElementFactory(context.getProject()).createType(jc, sub), false);
            }
            end = i + 1;
            break;
          }
        } else if (text.equals(">")) nesting--;
      }
    }
    if (end < limit && ts.get(end).text().equals("<") && base.declaration() instanceof ClassDecl cls) {
      var header = GosuSource.tokenize(cls.getText());
      List<String> variables = new ArrayList<>();
      if (header.size() > 2 && header.get(2).text().equals("<")) {
        for (int i = 3; i < header.size() && !header.get(i).text().equals(">"); i++) {
          if (i == 3 || header.get(i - 1).text().equals(",")) variables.add(header.get(i).text());
        }
      }
      int nesting = 0, finish = end;
      for (; finish < limit; finish++) {
        if (ts.get(finish).text().equals("<")) nesting++;
        if (ts.get(finish).text().equals(">") && --nesting == 0) break;
      }
      if (finish < limit) {
        var arguments = GosuSignatures.splitParameters(join(ts, end + 1, finish));
        if (variables.size() == arguments.size()) {
          Map<String, String> bindings = new LinkedHashMap<>();
          for (int i = 0; i < variables.size(); i++) {
            Type argument = typeFromText(arguments.get(i), context);
            String qualified = argument != null && argument.javaType() != null ? argument.javaType().getCanonicalText()
                : argument != null && argument.declaration() instanceof ClassDecl c ? GosuTypes.qualifiedName(c) : arguments.get(i);
            bindings.put(variables.get(i), qualified);
          }
          base = new Type(cls, null, false, Collections.unmodifiableMap(new LinkedHashMap<>(bindings)));
        }
        end = finish + 1;
      }
    }
    if (end + 1 < limit && ts.get(end).text().equals("[") && ts.get(end + 1).text().equals("]")
        && base.javaType() != null) return javaType(base.javaType().createArrayType());
    return base;
  }

  private static Type declaredType(PsiElement declaration, int depth) {
    return com.intellij.openapi.util.RecursionManager.doPreventingRecursion(declaration, false,
        () -> inferDeclaredType(declaration, depth));
  }

  private static Type inferDeclaredType(PsiElement declaration, int depth) {
    if (depth > 20) return null;
    List<GosuSource.Token> ts = GosuSource.tokens(declaration.getContainingFile());
    int start = GosuSource.before(ts, declaration.getTextOffset()) + 1;
    int end = GosuSource.before(ts, declaration.getTextRange().getEndOffset()) + 1;
    int parens = 0;
    for (int i = start; i < end; i++) {
      String s = ts.get(i).text();
      if (s.equals("(")) parens++;
      if (s.equals(")")) parens--;
      if (s.equals("{") && parens == 0) break;
      if (s.equals(":") && parens == 0) return readType(declaration, ts, i + 1, ts.size());
    }
    if (declaration instanceof Param param && param.getParent() instanceof GosuLambda lambda) {
      return GosuLambdaTypes.infer(param, lambda);
    }
    if (declaration instanceof VarDecl && end < ts.size() && ts.get(end).text().equals("in")) {
      int last = end + 1, nesting = 0;
      for (int i = end + 1; i < ts.size(); i++) {
        String part = ts.get(i).text();
        if (nesting == 0 && (part.equals(")") || part.equals("index") || part.equals("iterator"))) break;
        if (Set.of("(", "[", "{").contains(part)) nesting++;
        if (Set.of(")", "]", "}").contains(part)) nesting--;
        last = i;
      }
      return collectionElement(expression(declaration, ts, last, depth + 1), declaration);
    }
    Initializer initializer = initializer(declaration);
    if (initializer == null || ts.get(initializer.first()).text().equals("\\")) return null;
    return expression(declaration, ts, initializer.last(), depth + 1);
  }

  /** Token range shared by type inference and its user-facing explanation. */
  public record Initializer(int first, int last) {}
  public static Initializer initializer(PsiElement declaration) {
    if (!(declaration instanceof VarDecl)) return null;
    var ts = GosuSource.tokens(declaration.getContainingFile());
    int end = GosuSource.before(ts, declaration.getTextRange().getEndOffset()) + 1;
    if (end + 1 >= ts.size() || !ts.get(end).text().equals("=")) return null;
    int last = end, nesting = 0;
    String source = declaration.getContainingFile().getText();
    for (int i = end + 1; i < ts.size(); i++) {
      String s = ts.get(i).text();
      if (nesting == 0 && (s.equals(";") || s.equals("}")
          || (i > end + 1 && source.substring(ts.get(i - 1).end(), ts.get(i).start()).contains("\n")))) break;
      if (s.equals("(") || s.equals("[") || s.equals("{")) nesting++;
      if (s.equals(")") || s.equals("]") || s.equals("}")) {
        if (nesting == 0) break;
        nesting--;
      }
      last = i;
    }
    return last > end ? new Initializer(end + 1, last) : null;
  }

  public static Type collectionElement(Type collection, PsiElement context) {
    if (collection == null) return null;
    if (collection.javaType() instanceof PsiArrayType a) return javaType(a.getComponentType());
    if (collection.javaType() instanceof PsiClassType ct && ct.resolve() != null) {
      PsiClass iterable = JavaPsiFacade.getInstance(context.getProject()).findClass("java.lang.Iterable", context.getResolveScope());
      if (iterable != null && (ct.resolve().isEquivalentTo(iterable) || ct.resolve().isInheritor(iterable, true))) {
        var sub = TypeConversionUtil.getSuperClassSubstitutor(iterable, ct.resolve(), ct.resolveGenerics().getSubstitutor());
        return javaType(sub.substitute(iterable.getTypeParameters()[0]));
      }
    }
    return null;
  }

  private static List<Member> members(Type type, PsiElement context, Set<PsiElement> visited) {
    if (type == null || type.declaration() == null || !visited.add(type.declaration())) return List.of();
    ProgressManager.checkCanceled();
    List<Member> result = new ArrayList<>();
    if (type.declaration() instanceof PsiClass jc) {
      PsiSubstitutor sub = type.javaType() instanceof PsiClassType ct
          ? ct.resolveGenerics().getSubstitutor() : PsiSubstitutor.EMPTY;
      Set<String> seen = new HashSet<>();
      for (PsiMethod method : jc.getAllMethods()) {
        if (method.isConstructor() || !method.hasModifierProperty(PsiModifier.PUBLIC)
            || method.hasModifierProperty(PsiModifier.STATIC) != type.staticAccess()) continue;
        PsiSubstitutor inherited = TypeConversionUtil.getSuperClassSubstitutor(method.getContainingClass(), jc, sub);
        PsiType ret = inherited.substitute(method.getReturnType());
        String params = Arrays.stream(method.getParameterList().getParameters())
            .map(p -> inherited.substitute(p.getType()).getCanonicalText()).reduce((a, b) -> a + ", " + b).orElse("");
        String signature = "(" + params + ")";
        if (seen.add(method.getName() + signature)) result.add(new Member(method.getName(), method,
            javaType(ret), true, method.getParameterList().getParametersCount(), signature));
        String property = getterProperty(method);
        if (property != null && seen.add(property)) result.add(new Member(property, method, javaType(ret), false, 0, ""));
      }
      for (PsiField field : jc.getAllFields()) {
        if (field.hasModifierProperty(PsiModifier.PUBLIC)
            && field.hasModifierProperty(PsiModifier.STATIC) == type.staticAccess() && seen.add(field.getName())) {
          PsiSubstitutor inherited = TypeConversionUtil.getSuperClassSubstitutor(field.getContainingClass(), jc, sub);
          result.add(new Member(field.getName(), field, javaType(inherited.substitute(field.getType())), false, 0, ""));
        }
      }
    } else if (type.declaration() instanceof ClassDecl owner) {
      for (PsiElement child : PsiTreeUtil.findChildrenOfAnyType(owner, FunctionDecl.class, VarDecl.class)) {
        if (PsiTreeUtil.getParentOfType(child, ClassDecl.class) != owner
            || PsiTreeUtil.getParentOfType(child, FunctionDecl.class) != null) continue;
        // A get/set pair denotes one property; expose the readable accessor once.
        if (GosuPropertyAccessor.of(child) == GosuPropertyAccessor.SET) continue;
        String name = ((PsiNamedElement) child).getName();
        if (name == null || name.equals("construct")) continue;
        Set<String> modifiers = modifiers(child);
        if (modifiers.contains("static") != type.staticAccess()) continue;
        if (modifiers.contains("private") && PsiTreeUtil.getParentOfType(context, ClassDecl.class) != owner) continue;
        boolean method = child instanceof FunctionDecl;
        List<Param> params = method ? PsiTreeUtil.getChildrenOfTypeAsList(child, Param.class) : List.of();
        String signature = method ? "(" + params.stream().map(PsiElement::getText).reduce((a,b) -> a + ", " + b).orElse("") + ")" : "";
        Type returnType = declaredType(child, 1);
        if (!type.bindings().isEmpty()) {
          String resolved = GosuSignatures.declaredType(child);
          for (var entry : type.bindings().entrySet()) {
            resolved = resolved.replaceAll("\\b" + java.util.regex.Pattern.quote(entry.getKey()) + "\\b", java.util.regex.Matcher.quoteReplacement(entry.getValue()));
            signature = signature.replaceAll("\\b" + java.util.regex.Pattern.quote(entry.getKey()) + "\\b", java.util.regex.Matcher.quoteReplacement(entry.getValue()));
          }
          returnType = typeFromText(resolved, child);
        }
        result.add(new Member(name, child, returnType, method, params.size(), signature));
      }
      List<GosuSource.Token> ts = GosuSource.tokenize(owner.getText());
      for (int i = 0; i < ts.size() && !ts.get(i).text().equals("{"); i++) {
        if (ts.get(i).text().equals("extends")) {
          Type parent = readType(owner, ts, i + 1, ts.size());
          if (parent != null) {
            Type inherited = new Type(parent.declaration(), parent.javaType(), type.staticAccess(), parent.bindings());
            for (Member m : members(inherited, context, visited)) {
              if (result.stream().noneMatch(existing -> existing.name().equals(m.name()) && existing.signature().equals(m.signature()))) result.add(m);
            }
          }
        }
      }
    }
    for (Member enhancement : GosuEnhancements.members(type, context)) {
      if (result.stream().noneMatch(m -> m.name().equals(enhancement.name()) && m.signature().equals(enhancement.signature()))) result.add(enhancement);
    }
    return result;
  }

  public static Set<String> modifiers(PsiElement declaration) {
    var ts = GosuSource.tokens(declaration.getContainingFile());
    int i = GosuSource.before(ts, declaration.getTextOffset());
    Set<String> result = new HashSet<>();
    while (i >= 0 && Set.of("public", "private", "protected", "internal", "static", "final", "override", "abstract").contains(ts.get(i).text())) {
      result.add(ts.get(i--).text());
    }
    return result;
  }

  private static String getterProperty(PsiMethod method) {
    if (method.getParameterList().getParametersCount() != 0 || PsiTypes.voidType().equals(method.getReturnType())) return null;
    String name = method.getName();
    if (name.startsWith("get") && name.length() > 3) return name.substring(3);
    if (name.startsWith("is") && name.length() > 2 && PsiTypes.booleanType().equals(method.getReturnType())) return name.substring(2);
    return null;
  }

  public static String typeLabel(Member member) {
    return typeLabel(member.type());
  }

  public static String typeLabel(Type type) {
    if (type == null) return "";
    if (type.javaType() != null) return type.javaType().getPresentableText();
    String label = type.declaration() instanceof PsiNamedElement named ? Objects.toString(named.getName(), "") : "";
    if (!type.bindings().isEmpty()) label += "<" + String.join(", ", type.bindings().values()) + ">";
    return label;
  }
}
