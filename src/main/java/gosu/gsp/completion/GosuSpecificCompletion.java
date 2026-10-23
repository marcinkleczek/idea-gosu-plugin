package gosu.gsp.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.psi.GosuPropertyAccessor;
import gosu.gsp.resolve.*;
import gosu.gsp.hierarchy.GosuHierarchy;
import java.util.*;

/** Gosu's colon slots are distinct from expressions and type annotations. */
public final class GosuSpecificCompletion {
  private GosuSpecificCompletion() {}
  public record Slot(int open, int from, int to, boolean colon) {}
  public record Property(String name, String type, PsiElement target) {}
  public static Slot slot(PsiFile file, int offset) {
    var ts = GosuSource.tokens(file);
    String text = file.getText();
    int start = offset;
    while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
    int colon = start;
    while (colon > 0 && Character.isWhitespace(text.charAt(colon - 1))) colon--;
    boolean hasColon = colon > 0 && text.charAt(colon - 1) == ':';
    int before = hasColon ? colon - 1 : start;
    int last = GosuSource.before(ts, before);
    if (last < 0 || !Set.of("{", "(", ",").contains(ts.get(last).text())) return null;
    int depth = 0;
    for (int i = last; i >= 0; i--) {
      String part = ts.get(i).text();
      if (Set.of(")", "]", "}").contains(part)) depth++;
      if (Set.of("(", "[", "{").contains(part)) {
        if (depth == 0) return new Slot(i, start, offset, hasColon);
        depth--;
      }
    }
    return null;
  }
  public static List<Property> writable(GosuMembers.Type type, PsiElement context) { return writable(type, context, new HashSet<>()); }
  private static List<Property> writable(GosuMembers.Type type, PsiElement context, Set<PsiElement> seen) {
    if (type == null || type.declaration() == null || !seen.add(type.declaration())) return List.of();
    Map<String,Property> out = new LinkedHashMap<>();
    if (type.declaration() instanceof PsiClass cls) {
      for (PsiMethod method : cls.getAllMethods()) {
        if (method.getName().startsWith("set") && method.getName().length() > 3 && method.getParameterList().getParametersCount() == 1
            && method.hasModifierProperty(PsiModifier.PUBLIC) && !method.hasModifierProperty(PsiModifier.STATIC) && PsiTypes.voidType().equals(method.getReturnType())) {
          String name = method.getName().substring(3);
          out.putIfAbsent(name, new Property(name, method.getParameterList().getParameters()[0].getType().getPresentableText(), method));
        }
      }
      for (PsiField field : cls.getAllFields()) if (field.hasModifierProperty(PsiModifier.PUBLIC) && !field.hasModifierProperty(PsiModifier.STATIC) && !field.hasModifierProperty(PsiModifier.FINAL))
        out.putIfAbsent(field.getName(), new Property(field.getName(), field.getType().getPresentableText(), field));
    } else if (type.declaration() instanceof ClassDecl cls) {
      for (VarDecl property : PsiTreeUtil.findChildrenOfType(cls, VarDecl.class)) {
        if (GosuHierarchy.owner(property) != cls || PsiTreeUtil.getParentOfType(property, FunctionDecl.class) != null) continue;
        var mods = GosuMembers.modifiers(property);
        if (!Collections.disjoint(mods, Set.of("private", "protected", "static", "final"))) continue;
        if (GosuPropertyAccessor.of(property) == GosuPropertyAccessor.SET) {
          out.putIfAbsent(property.getName(), new Property(property.getName(), "property", property));
        } else if (property.getText().startsWith("var ")) {
          var ts = GosuSource.tokens(property.getContainingFile());
          int next = GosuSource.before(ts, property.getTextRange().getEndOffset()) + 1;
          if (next + 1 < ts.size() && ts.get(next).text().equals("as")) {
            String name = ts.get(next + 1).text();
            out.putIfAbsent(name, new Property(name, GosuSignatures.declaredType(property), property));
          } else if (mods.contains("public") && property.getName() != null) out.putIfAbsent(property.getName(), new Property(property.getName(), GosuSignatures.declaredType(property), property));
        }
      }
      for (var parent : GosuHierarchy.parentTypes(cls)) for (var p : writable(parent, context, seen)) out.putIfAbsent(p.name(), p);
    }
    return out.values().stream().filter(p -> p.name() != null).toList();
  }
  public static Set<String> usedNames(PsiFile file, int open, int offset) {
    var ts = GosuSource.tokens(file);
    Set<String> used = new HashSet<>();
    int depth = 0;
    for (int i = open + 1; i < ts.size(); i++) {
      String part = ts.get(i).text();
      if (depth == 0 && Set.of(")", "}", "]").contains(part)) break;
      boolean current = ts.get(i).start() <= offset && ts.get(i).end() >= offset
          || part.equals(":") && i + 1 < ts.size() && ts.get(i + 1).start() == offset;
      if (depth == 0 && !current) {
        if (part.startsWith(":" ) && part.length() > 1) used.add(part.substring(1));
        else if (part.equals(":") && i + 2 < ts.size() && ts.get(i + 2).text().equals("=")) used.add(ts.get(i + 1).text());
      }
      if (Set.of("(", "{", "[").contains(part)) depth++;
      if (Set.of(")", "}", "]").contains(part)) depth--;
    }
    return used;
  }
  private static int positionalCount(List<GosuSource.Token> tokens, int open, int offset) {
    int count = 0, depth = 0, start = open + 1;
    for (int i = start; i < tokens.size() && tokens.get(i).start() < offset; i++) {
      String part = tokens.get(i).text();
      if (depth == 0 && part.equals(",")) {
        if (i > start && !tokens.get(start).text().startsWith(":")) count++;
        start = i + 1;
      }
      if (Set.of("(", "{", "[").contains(part)) depth++;
      if (Set.of(")", "}", "]").contains(part)) depth--;
    }
    return count;
  }

  private static boolean isConstructorInitializer(List<GosuSource.Token> tokens, int brace) {
    if (brace < 3 || !tokens.get(brace - 1).text().equals(")")) return false;
    int nesting = 0, open = -1;
    for (int i = brace - 1; i >= 0; i--) {
      String part = tokens.get(i).text();
      if (part.equals(")")) nesting++;
      if (part.equals("(") && --nesting == 0) { open = i; break; }
    }
    for (int i = open - 1; i >= 0; i--) {
      String part = tokens.get(i).text();
      if (part.equals("new")) return true;
      if (tokens.get(i).kind() != gosu.gsp.lexer.GosuTokenTypes.IDENTIFIER && !Set.of(".", "<", ">", ",", "[", "]").contains(part)) return false;
    }
    return false;
  }

  public static boolean complete(CompletionParameters parameters, CompletionResultSet result) {
    var file = parameters.getOriginalFile();
    int offset = parameters.getOffset();
    Slot slot = slot(file, offset);
    if (slot == null) return false;
    var ts = GosuSource.tokens(file);
    List<Property> candidates = List.of();
    boolean initializer = ts.get(slot.open()).text().equals("{");
    if (initializer) {
      if (!isConstructorInitializer(ts, slot.open())) return false;
      var type = GosuMembers.expression(file, ts, slot.open() - 1, 0);
      if (type == null) return false;
      candidates = writable(type, file);
    } else {
      if (!slot.colon()) return false; // Ordinary argument expressions keep their normal completion.
      var call = GosuCalls.at(file, ts.get(slot.open()).end());
      if (call == null) return false;
      Map<String,Property> names = new LinkedHashMap<>();
      for (var signature : call.signatures()) {
        // Java bytecode often exposes arg0; named-argument suggestions need Gosu declarations.
        if (!(signature.target() instanceof FunctionDecl)) continue;
        int positional = positionalCount(ts, slot.open(), slot.from());
        for (String parameter : signature.parameters().subList(Math.min(positional, signature.parameters().size()), signature.parameters().size())) {
          int colon = parameter.indexOf(':');
          String name = (colon < 0 ? parameter : parameter.substring(0, colon)).trim();
          if (name.matches("[\\p{L}_$][\\p{L}\\p{N}_$]*")) names.putIfAbsent(name, new Property(name, colon < 0 ? "" : parameter.substring(colon + 1).trim(), signature.target()));
        }
      }
      candidates = List.copyOf(names.values());
    }
    var used = usedNames(file, slot.open(), slot.from());
    var specific = result.withPrefixMatcher(file.getText().substring(slot.from(), slot.to()));
    for (var candidate : candidates) {
      if (used.contains(candidate.name())) continue;
      specific.addElement(LookupElementBuilder.create(candidate.target(), candidate.name()).withIcon(AllIcons.Nodes.Property)
          .withTypeText(candidate.type()).withTailText(initializer ? " (initializer)" : " (named argument)", true)
          .withInsertHandler((ctx,item) -> {
            int start = ctx.getStartOffset(), tail = ctx.getTailOffset();
            if (!slot.colon()) { ctx.getDocument().insertString(start, ":"); tail++; }
            int next = tail;
            var chars = ctx.getDocument().getCharsSequence();
            while (next < chars.length() && Character.isWhitespace(chars.charAt(next))) next++;
            if (next >= chars.length() || chars.charAt(next) != '=') {
              ctx.getDocument().insertString(tail, " = "); tail += 3;
            } else tail = next + 1;
            ctx.getEditor().getCaretModel().moveToOffset(tail);
            if (ctx.getCompletionChar() == '=') ctx.setAddCompletionChar(false);
          }));
    }
    specific.stopHere();
    return true;
  }
}
