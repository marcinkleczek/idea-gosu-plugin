package gosu.gsp.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import java.util.*;

public final class GosuSmartCompletion {
  private GosuSmartCompletion() {}
  public static List<GosuMembers.Type> expected(PsiElement position, int offset) {
    List<GosuMembers.Type> result = new ArrayList<>();
    var file = position.getContainingFile();
    var call = GosuCalls.at(file, offset);
    if (call != null) {
      String named = "";
      int nesting = 0;
      for (var token : GosuSource.tokens(file)) {
        if (token.start() <= call.openOffset() || token.start() >= offset) continue;
        if (nesting == 0 && token.text().equals(",")) named = "";
        if (nesting == 0 && token.kind() == gosu.gsp.lexer.GosuTokenTypes.LABEL) named = token.text().substring(1);
        if (Set.of("(", "[", "{").contains(token.text())) nesting++;
        if (Set.of(")", "]", "}").contains(token.text())) nesting--;
      }
      for (var sig : call.signatures()) {
        int index = call.parameterIndex();
        if (!named.isEmpty()) {
          index = -1;
          for (int i = 0; i < sig.parameters().size(); i++)
            if (sig.parameters().get(i).split(":", 2)[0].strip().equals(named)) index = i;
        }
        if (index < 0 || index >= sig.parameters().size()) continue;
        String parameter = sig.parameters().get(index);
        int colon = parameter.indexOf(':');
        String type = (colon < 0 ? parameter : parameter.substring(colon + 1)).strip();
        var parsed = GosuMembers.typeFromText(type, sig.target());
        if (parsed != null) result.add(parsed);
      }
      return result;
    }
    var ts = GosuSource.tokens(file);
    int previous = GosuSource.before(ts, offset);
    int boundary = previous;
    while (boundary >= 0 && !Set.of(";", "{", "}").contains(ts.get(boundary).text())) {
      if (boundary < previous && file.getText().substring(ts.get(boundary).end(), ts.get(boundary + 1).start()).contains("\n")) break;
      boundary--;
    }
    for (int i = boundary + 1; i <= previous; i++) {
      if (ts.get(i).text().equals("return")) {
        var fn = PsiTreeUtil.getParentOfType(position, FunctionDecl.class);
        if (fn != null) add(result, GosuSignatures.declaredType(fn), fn);
        break;
      }
      if (ts.get(i).text().equals("=")) {
        VarDecl declaration = null;
        for (var v : PsiTreeUtil.findChildrenOfType(file, VarDecl.class))
          if (v.getTextOffset() >= (boundary < 0 ? 0 : ts.get(boundary).end()) && v.getTextRange().getEndOffset() <= ts.get(i).start()) declaration = v;
        if (declaration != null) add(result, GosuSignatures.declaredType(declaration), declaration);
        else if (i > 0) {
          var leaf = file.findElementAt(ts.get(i - 1).start());
          var ref = PsiTreeUtil.getParentOfType(leaf, GosuRefExpr.class, false);
          var target = ref == null || ref.getReference() == null ? null : ref.getReference().resolve();
          if (target != null) { var type = GosuMembers.infer(target); if (type != null) result.add(type); }
        }
        break;
      }
    }
    return result;
  }
  private static void add(List<GosuMembers.Type> result, String type, PsiElement context) {
    if (!type.isBlank() && !type.equals("void")) { var parsed = GosuMembers.typeFromText(type, context); if (parsed != null) result.add(parsed); }
  }
  public static boolean compatible(GosuMembers.Type expected, GosuMembers.Type actual) {
    return compatible(expected, actual, new HashSet<>());
  }
  private static boolean compatible(GosuMembers.Type expected, GosuMembers.Type actual, Set<PsiElement> seen) {
    if (actual == null || actual.staticAccess()) return false;
    if (expected.javaType() != null && actual.javaType() != null) return TypeConversionUtil.isAssignable(expected.javaType(), actual.javaType());
    if (actual.declaration() == null || !seen.add(actual.declaration())) return false;
    if (expected.declaration() != null && actual.declaration().isEquivalentTo(expected.declaration())) return expected.bindings().equals(actual.bindings());
    for (var parent : gosu.gsp.hierarchy.GosuHierarchy.parents(actual.declaration())) {
      var type = parent instanceof PsiClass c ? GosuMembers.javaType(JavaPsiFacade.getElementFactory(c.getProject()).createType(c)) : new GosuMembers.Type(parent, null, false);
      if (compatible(expected, type, seen)) return true;
    }
    return false;
  }
  public static void complete(CompletionParameters parameters, CompletionResultSet result) {
    var position = parameters.getPosition();
    List<GosuMembers.Type> expected = expected(position, position.getTextOffset());
    if (expected.isEmpty()) return;
    if (GosuMembers.isMemberPosition(position.getContainingFile(), position.getTextOffset())) {
      for (var member : GosuMembers.at(position, position.getTextOffset()))
        if (expected.stream().anyMatch(t -> compatible(t, member.type()))) addCandidate(result, member.target(), member.name(), member.type(), member.method(), member.parameterCount(), member.signature());
    } else {
      for (var variant : GosuReference.variantsFor(position)) {
        String name = variant.getLookupString();
        PsiElement target = GosuReference.findUnqualified(name, position);
        if (!(target instanceof VarDecl) && !(target instanceof Param) && !(target instanceof FunctionDecl)) continue;
        var type = GosuMembers.infer(target);
        if (expected.stream().noneMatch(t -> compatible(t, type))) continue;
        var sig = GosuSignatures.of(target);
        addCandidate(result, target, name, type, sig != null, sig == null ? 0 : sig.parameters().size(), sig == null ? "" : "(" + String.join(", ", sig.parameters()) + ")");
      }
      for (String literal : List.of("true", "false", "0", "\"\"")) {
        var ts = GosuSource.tokenize(literal);
        var type = GosuMembers.expression(position, ts, 0, 0);
        if (expected.stream().anyMatch(t -> compatible(t, type))) result.addElement(LookupElementBuilder.create(literal).withTypeText(GosuMembers.typeLabel(type)));
      }
    }
    result.stopHere();
  }
  private static void addCandidate(CompletionResultSet result, PsiElement target, String name, GosuMembers.Type type, boolean method, int count, String signature) {
    result.addElement(LookupElementBuilder.create(target, name).withTypeText(GosuMembers.typeLabel(type)).withTailText(signature, true)
        .withInsertHandler((ctx, item) -> {
          if (!method) return;
          int tail = ctx.getTailOffset();
          if (tail < ctx.getDocument().getTextLength() && ctx.getDocument().getCharsSequence().charAt(tail) == '(') return;
          ctx.getDocument().insertString(tail, "()");
          ctx.getEditor().getCaretModel().moveToOffset(tail + (count == 0 ? 2 : 1));
          if (ctx.getCompletionChar() == '(') ctx.setAddCompletionChar(false);
        }));
  }
}
