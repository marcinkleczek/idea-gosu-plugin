package gosu.gsp.refactor;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import gosu.gsp.hierarchy.GosuCalls;
import gosu.gsp.hierarchy.GosuHierarchy;
import gosu.gsp.editor.GosuNamesValidator;
import java.util.*;

public final class GosuChangeSignature {
  private GosuChangeSignature() {}
  public record Parameter(String original, String name, String type, String argument) {}
  public record Arguments(int start, int end, List<String> values) {}
  public static Arguments arguments(PsiElement reference) {
    var file = reference.getContainingFile();
    var ts = GosuSource.tokens(file);
    int open = GosuSource.before(ts, reference.getTextRange().getEndOffset()) + 1;
    if (open >= ts.size() || !ts.get(open).text().equals("(")) throw new IllegalArgumentException("A use of the function outside a call must be changed manually.");
    List<String> values = new ArrayList<>();
    int depth = 0, start = ts.get(open).end();
    for (int i = open + 1; i < ts.size(); i++) {
      String s = ts.get(i).text();
      if (s.equals("<") || s.equals(">")) throw new IllegalArgumentException("Arguments containing the < or > operators must be changed manually.");
      if (depth == 0 && (s.equals(",") || s.equals(")"))) {
        String value = file.getText().substring(start, ts.get(i).start()).strip();
        if (!value.isEmpty()) values.add(value);
        if (s.equals(")")) return new Arguments(ts.get(open).end(), ts.get(i).start(), values);
        start = ts.get(i).end();
      }
      if (Set.of("(", "[", "{").contains(s)) depth++;
      if (Set.of(")", "]", "}").contains(s)) depth--;
    }
    throw new IllegalArgumentException("Unclosed call.");
  }
  public static GosuEditPlan plan(FunctionDecl fn, List<Parameter> changes) {
    if (fn.getNameIdentifier() == null || !fn.getText().startsWith("function") || !GosuHierarchy.superMembers(fn).isEmpty() || !GosuHierarchy.implementations(fn, true).isEmpty())
      throw new IllegalArgumentException("Constructors and methods linked by overriding require changing the whole hierarchy.");
    var old = PsiTreeUtil.getChildrenOfTypeAsList(fn, Param.class);
    Map<String, Param> original = new LinkedHashMap<>();
    old.forEach(p -> original.put(p.getName(), p));
    Set<String> names = new HashSet<>(), origins = new HashSet<>();
    for (var p : changes) {
      if (!new GosuNamesValidator().isIdentifier(p.name(), fn.getProject()) || !names.add(p.name()) || p.type().isBlank() || GosuMembers.typeFromText(p.type(), fn) == null)
        throw new IllegalArgumentException("Parameter names must be unique and their types recognized.");
      if (!p.original().isEmpty() && (!original.containsKey(p.original()) || !origins.add(p.original()))) throw new IllegalArgumentException("Invalid mapping of an existing parameter.");
      if (p.original().isEmpty() && !gosu.gsp.intentions.GosuNullSupport.validFallback(p.argument())) throw new IllegalArgumentException("A new parameter needs an argument to insert at the call sites.");
      for (VarDecl v : PsiTreeUtil.findChildrenOfType(fn, VarDecl.class)) if (p.name().equals(v.getName())) throw new IllegalArgumentException("The name collides with a local variable: " + p.name());
    }
    if (fn.getText().contains("${") || fn.getText().contains("<%")) throw new IllegalArgumentException("Interpolation inside the function requires manual review of the references.");
    for (var other : PsiTreeUtil.findChildrenOfType(fn.getContainingFile(), FunctionDecl.class))
      if (other != fn && Objects.equals(other.getName(), fn.getName()) && PsiTreeUtil.getParentOfType(other, ClassDecl.class) == PsiTreeUtil.getParentOfType(fn, ClassDecl.class))
        throw new IllegalArgumentException("Changing an overloaded function requires reviewing all overloads.");
    var plan = new GosuEditPlan();
    var header = arguments(fn.getNameIdentifier());
    if (fn.getText().substring(0, header.end() - fn.getTextOffset()).contains("=")) throw new IllegalArgumentException("Parameters with default values must be changed manually.");
    plan.add(fn.getContainingFile(), header.start(), header.end(), String.join(", ", changes.stream().map(p -> p.name() + " : " + p.type()).toList()));
    Map<PsiElement, Parameter> mappings = new HashMap<>();
    for (var p : changes) if (!p.original().isEmpty()) mappings.put(original.get(p.original()), p);
    for (var ref : PsiTreeUtil.findChildrenOfType(fn, GosuRefExpr.class)) {
      PsiElement target = ref.getReference() == null ? null : ref.getReference().resolve();
      if (target instanceof Param && original.containsValue(target) && !mappings.containsKey(target)) throw new IllegalArgumentException("The removed parameter is used in the function body: " + ((Param) target).getName());
    }
    for (var file : GosuCalls.files(fn)) for (var ref : PsiTreeUtil.findChildrenOfType(file, GosuRefExpr.class)) {
      if (!Objects.equals(ref.getName(), fn.getName())) continue;
      PsiElement target = GosuCalls.target(ref);
      if (target == null) throw new IllegalArgumentException("Ambiguous use of " + fn.getName() + " in " + file.getName());
      if (!target.isEquivalentTo(fn)) continue;
      var args = arguments(ref);
      if (args.values().size() != old.size()) throw new IllegalArgumentException("A call with missing arguments must be changed manually.");
      Map<String, String> supplied = new LinkedHashMap<>();
      int positional = 0;
      for (String value : args.values()) {
        var tokens = GosuSource.tokenize(value);
        String key;
        if (!tokens.isEmpty() && tokens.getFirst().text().startsWith(":")) {
          key = tokens.getFirst().text().substring(1);
          if (tokens.size() < 3 || !tokens.get(1).text().equals("=")) throw new IllegalArgumentException("Unrecognized form of a named argument.");
          value = value.substring(tokens.get(1).end()).strip();
        } else key = old.get(positional++).getName();
        if (supplied.put(key, value) != null || !original.containsKey(key)) throw new IllegalArgumentException("Duplicate or unknown argument.");
      }
      List<String> keptOrder = changes.stream().map(Parameter::original).filter(s -> !s.isEmpty()).toList();
      if (!keptOrder.equals(new ArrayList<>(supplied.keySet()))) {
        for (String value : supplied.values()) if (!pure(value)) throw new IllegalArgumentException("Reordering or removing arguments with side effects requires extracting them into variables manually.");
      }
      List<String> arguments = new ArrayList<>();
      for (var p : changes) {
        String value = p.original().isEmpty() ? p.argument() : supplied.get(p.original());
        if (value == null) throw new IllegalArgumentException("Missing argument: " + p.original());
        // Recursive calls may use a parameter whose name changes in this same operation.
        if (PsiTreeUtil.isAncestor(fn, ref, false)) {
          for (var token : GosuSource.tokenize(value).reversed()) {
            var targetParam = original.get(token.text());
            if (targetParam != null && mappings.containsKey(targetParam) && (token.start() == 0 || value.charAt(token.start() - 1) != '.')) value = value.substring(0, token.start()) + mappings.get(targetParam).name() + value.substring(token.end());
          }
        }
        arguments.add(value);
      }
      plan.add(file, args.start(), args.end(), String.join(", ", arguments));
    }
    for (var ref : PsiTreeUtil.findChildrenOfType(fn, GosuRefExpr.class)) {
      var parameter = ref.getReference() == null ? null : mappings.get(ref.getReference().resolve());
      if (parameter != null && !plan.covers(ref.getContainingFile(), ref.getTextOffset())) plan.add(ref.getContainingFile(), ref.getTextOffset(), ref.getTextRange().getEndOffset(), parameter.name());
    }
    plan.validate(); return plan;
  }
  private static boolean pure(String expression) {
    var ts = GosuSource.tokenize(expression);
    return ts.size() == 1 && (ts.getFirst().kind() == gosu.gsp.lexer.GosuTokenTypes.IDENTIFIER || ts.getFirst().kind() == gosu.gsp.lexer.GosuTokenTypes.NUMBER
        || ts.getFirst().kind() == gosu.gsp.lexer.GosuTokenTypes.STRING && !expression.contains("${") || Set.of("true", "false", "null").contains(expression));
  }
}
