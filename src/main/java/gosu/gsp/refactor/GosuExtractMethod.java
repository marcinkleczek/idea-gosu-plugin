package gosu.gsp.refactor;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import gosu.gsp.intentions.GosuExplicitTypeIntention;
import java.util.*;

/** Extracts expressions or straight-line statements; refuses uncertain data flow. */
public final class GosuExtractMethod {
  private GosuExtractMethod() {}
  public record Parameter(String name, String type, PsiElement declaration) {}
  public record Plan(int start, int end, int insertion, String indent, String helperIndent,
                     boolean member, boolean staticMethod, String body, String resultType,
                     String output, boolean expression, List<Parameter> parameters) {}

  public static Plan plan(PsiFile file, int start, int end) {
    if (!(file instanceof GosuFile) || start < 0 || start >= end || end > file.getTextLength()) return null;
    String source = file.getText();
    while (start < end && Character.isWhitespace(source.charAt(start))) start++;
    while (end > start && Character.isWhitespace(source.charAt(end - 1))) end--;
    if (start == end) return null;
    PsiElement context = file.findElementAt(start);
    if (context == null || PsiTreeUtil.getParentOfType(context, GosuLambda.class) != null) return null;
    FunctionDecl fn = PsiTreeUtil.getParentOfType(context, FunctionDecl.class);
    ClassDecl cls = PsiTreeUtil.getParentOfType(context, ClassDecl.class);
    if (cls != null && fn == null) return null;
    if (fn != null && (end > fn.getTextRange().getEndOffset()
        || PsiTreeUtil.getParentOfType(fn, FunctionDecl.class) != null)) return null;
    // Method type parameters cannot be moved into a sibling without their bounds.
    if (fn != null && fn.getText().substring(0, Math.max(0, fn.getText().indexOf('('))).contains("<")) return null;
    var expression = GosuExtractVariable.selection(file, start, end);
    int lineStart = source.lastIndexOf('\n', start - 1) + 1;
    String indent = source.substring(lineStart, start).replaceFirst("\\S.*", "");
    String body = source.substring(start, end);
    if (GosuSource.tokenize(body).stream().anyMatch(t -> t.kind() == gosu.gsp.lexer.GosuTokenTypes.STRING
        && (t.text().contains("${") || t.text().contains("<%")))) return null;
    String result = "void";
    if (expression != null) {
      result = expression.explicitType();
      if (result.isBlank()) {
        // The lightweight inferencer cannot infer arbitrary operators reliably.
        var ts = GosuSource.tokenize(body);
        if (!simpleInference(ts)) return null;
        result = type(GosuMembers.expression(context, ts, ts.size() - 1, 0));
      }
      if (result.isBlank() || result.equals("void")) return null;
    } else {
      int lineEnd = source.indexOf('\n', end);
      if (lineEnd < 0) lineEnd = source.length();
      if (!source.substring(lineStart, start).isBlank() || !source.substring(end, lineEnd).isBlank()) return null;
      if (!straightLine(file, start, end)) return null;
    }
    final int selectionStart = start, selectionEnd = end;
    var declarations = PsiTreeUtil.findChildrenOfType(file, VarDecl.class).stream()
        .filter(v -> v.getTextOffset() >= selectionStart && v.getTextOffset() < selectionEnd).toList();
    Map<PsiElement, Parameter> inputs = new LinkedHashMap<>();
    Set<VarDecl> outputs = new LinkedHashSet<>();
    List<GosuRefExpr> refs = new ArrayList<>(PsiTreeUtil.findChildrenOfType(file, GosuRefExpr.class));
    refs.sort(Comparator.comparingInt(PsiElement::getTextOffset));
    var tokens = GosuSource.tokens(file);
    for (var ref : refs) {
      int offset = ref.getTextOffset();
      PsiElement target = ref.getReference() == null ? null : ref.getReference().resolve();
      if (offset >= start && offset < end) {
        int before = GosuSource.before(tokens, offset);
        if (before >= 0 && GosuSource.dot(tokens.get(before))) continue;
        if (target instanceof VarDecl v && declarations.contains(v)) continue;
        if (target instanceof VarDecl v && v.isMember()) continue;
        if (target instanceof VarDecl || target instanceof Param) {
          String parameterType = reliableType(target, new HashSet<>());
          if (parameterType.isBlank() || parameterType.equals("void")) return null;
          inputs.putIfAbsent(target, new Parameter(((PsiNamedElement) target).getName(), parameterType, target));
        } else if (target == null && !Set.of("print").contains(ref.getText())) return null;
      } else if (target instanceof VarDecl v && declarations.contains(v)) {
        outputs.add(v);
      }
    }
    if (outputs.size() > 1 || (expression != null && !outputs.isEmpty())) return null;
    String output = "";
    if (!outputs.isEmpty()) {
      VarDecl v = outputs.iterator().next();
      output = v.getName();
      result = reliableType(v, new HashSet<>());
      if (result.isBlank() || result.equals("void")) return null;
    }
    boolean member = cls != null;
    int insertion = fn == null ? source.length() : fn.getTextRange().getEndOffset();
    String helperIndent = "";
    if (member) {
      int fnLine = source.lastIndexOf('\n', fn.getTextOffset() - 1) + 1;
      String prefix = source.substring(fnLine, fn.getTextOffset());
      helperIndent = prefix.substring(0, prefix.length() - prefix.stripLeading().length());
      if (prefix.contains("{")) helperIndent = "  ";
    }
    return new Plan(start, end, insertion, indent, helperIndent, member,
        fn != null && GosuMembers.modifiers(fn).contains("static"), body, result, output, expression != null, List.copyOf(inputs.values()));
  }

  private static String type(GosuMembers.Type type) { return GosuExplicitTypeIntention.typeText(type); }

  private static boolean simpleInference(List<GosuSource.Token> tokens) {
    return tokens.stream().noneMatch(t -> t.kind() == gosu.gsp.lexer.GosuTokenTypes.OPERATOR
        || Set.of("as", "typeis", "and", "or", "not").contains(t.text()));
  }
  private static String reliableType(PsiElement declaration, Set<PsiElement> visited) {
    if (!visited.add(declaration)) return "";
    if (declaration instanceof VarDecl && GosuSignatures.declaredType(declaration).isBlank()) {
      var init = GosuMembers.initializer(declaration);
      if (init == null) return "";
      var tokens = GosuSource.tokens(declaration.getContainingFile()).subList(init.first(), init.last() + 1);
      if (!simpleInference(tokens)) return "";
      if (tokens.size() == 1 && tokens.getFirst().kind() == gosu.gsp.lexer.GosuTokenTypes.IDENTIFIER) {
        PsiElement original = GosuReference.findUnqualified(tokens.getFirst().text(), declaration);
        if (original == null) return "";
        return reliableType(original, visited);
      }
    }
    return type(GosuMembers.infer(declaration));
  }

  private static boolean straightLine(PsiFile file, int start, int end) {
    String selected = file.getText().substring(start, end);
    var tokens = GosuSource.tokenize(selected);
    if (tokens.isEmpty() || tokens.stream().anyMatch(t -> Set.of("return", "break", "continue", "throw", "if", "else", "for", "while", "do", "switch", "try", "using", "function", "construct", "class", "\\", "->", "{", "}", "++", "--", "+=", "-=", "*=", "/=").contains(t.text()))) return false;
    int offset = start;
    for (String line : selected.split("\\n", -1)) {
      String text = line.strip();
      var ts = GosuSource.tokenize(text);
      if (!ts.isEmpty()) {
        int last = ts.size() - 1;
        if (ts.get(last).text().equals(";")) last--;
        if (last < 0) return false;
        if (text.startsWith("var ")) {
          boolean valid = false;
          for (VarDecl v : PsiTreeUtil.findChildrenOfType(file, VarDecl.class)) {
            if (v.getTextOffset() < offset || v.getTextOffset() >= offset + line.length() || v.isMember()) continue;
            var init = GosuMembers.initializer(v);
            var all = GosuSource.tokens(file);
            if (init != null && all.get(init.last()).end() <= offset + line.length()) {
              long assignments = ts.stream().filter(t -> t.text().equals("=")).count();
              valid = assignments == 1;
            }
          }
          if (!valid) return false;
        } else {
          // Only call statements, not assignments or bare expressions.
          if (!ts.get(last).text().equals(")") || ts.stream().anyMatch(t -> Set.of("=", ";").contains(t.text()) && t != ts.getLast())) return false;
          if (ts.getFirst().kind() != gosu.gsp.lexer.GosuTokenTypes.IDENTIFIER && !ts.getFirst().text().equals("this")) return false;
        }
        int balance = 0;
        for (var t : ts) { if (t.text().equals("(")) balance++; if (t.text().equals(")") && --balance < 0) return false; }
        if (balance != 0) return false;
      }
      offset += line.length() + 1;
    }
    return true;
  }

  private static String fresh(String base, Set<String> used) {
    String name = base;
    int suffix = 2;
    while (!used.add(name)) name = base + suffix++;
    return name;
  }

  public static String transform(PsiFile file, int start, int end, String name) {
    Plan p = plan(file, start, end);
    if (p == null || !GosuExtractVariable.validName(file, name)) throw new IllegalArgumentException("The method cannot be safely extracted from this selection, or the name is already in use.");
    Set<String> used = new HashSet<>();
    GosuSource.tokens(file).forEach(t -> used.add(t.text()));
    used.add(name);
    Map<PsiElement, String> renamed = new LinkedHashMap<>();
    for (var parameter : p.parameters()) renamed.put(parameter.declaration(), fresh(parameter.name() + "Input", used));
    String returned = p.output();
    for (VarDecl local : PsiTreeUtil.findChildrenOfType(file, VarDecl.class)) {
      if (local.getTextOffset() >= p.start() && local.getTextOffset() < p.end()) {
        String replacement = fresh(local.getName() + "Local", used);
        renamed.put(local, replacement);
        if (local.getName().equals(p.output())) returned = replacement;
      }
    }
    record Edit(int start, int end, String text) {}
    List<Edit> edits = new ArrayList<>();
    for (var entry : renamed.entrySet()) {
      if (entry.getKey() instanceof PsiNameIdentifierOwner decl && decl.getNameIdentifier() != null) {
        var range = decl.getNameIdentifier().getTextRange();
        if (range.getStartOffset() >= p.start() && range.getEndOffset() <= p.end())
          edits.add(new Edit(range.getStartOffset() - p.start(), range.getEndOffset() - p.start(), entry.getValue()));
      }
    }
    for (var ref : PsiTreeUtil.findChildrenOfType(file, GosuRefExpr.class)) {
      if (ref.getTextOffset() < p.start() || ref.getTextRange().getEndOffset() > p.end() || ref.getReference() == null) continue;
      String replacement = renamed.get(ref.getReference().resolve());
      if (replacement != null) edits.add(new Edit(ref.getTextOffset() - p.start(), ref.getTextRange().getEndOffset() - p.start(), replacement));
    }
    edits.sort(Comparator.comparingInt(Edit::start).reversed());
    StringBuilder rewritten = new StringBuilder(p.body());
    for (var edit : edits) rewritten.replace(edit.start(), edit.end(), edit.text());
    String parameters = String.join(", ", p.parameters().stream().map(v -> renamed.get(v.declaration()) + " : " + v.type()).toList());
    String arguments = String.join(", ", p.parameters().stream().map(Parameter::name).toList());
    String call = name + "(" + arguments + ")";
    if (!p.output().isEmpty()) call = "var " + p.output() + " : " + p.resultType() + " = " + call;
    String inner = p.helperIndent() + "  ";
    String body = p.expression() ? inner + "return " + rewritten : inner + rewritten.toString().replace("\n" + p.indent(), "\n" + inner);
    if (!p.output().isEmpty()) body += "\n" + inner + "return " + returned;
    String helper = "\n\n" + p.helperIndent() + (p.member() ? "private " : "") + (p.staticMethod() ? "static " : "")
        + "function " + name + "(" + parameters + ") : " + p.resultType() + " {\n" + body + "\n" + p.helperIndent() + "}\n";
    String source = file.getText();
    return source.substring(0, p.start()) + call + source.substring(p.end(), p.insertion()) + helper + source.substring(p.insertion());
  }
}
