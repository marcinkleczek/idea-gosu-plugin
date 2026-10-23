package gosu.gsp.completion;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import java.util.*;

/** Uses the same inferred types and overload selection as completion. Unknowns stay explicit. */
public final class GosuTypeExplanation {
  private GosuTypeExplanation() {}
  public record Explanation(String type, List<String> reasons) {
    public String text() { return (type.isEmpty() ? "Type unknown" : "Type: " + type) + "\n\n" + String.join("\n", reasons); }
    public String html() {
      StringBuilder out = new StringBuilder("<p><b>Where does the type come from?</b></p>");
      for (String reason : reasons) out.append("<p>").append(escape(reason)).append("</p>");
      return out.toString();
    }
  }
  public static PsiElement declaration(PsiElement element) {
    if (element == null) return null;
    PsiElement named = PsiTreeUtil.getParentOfType(element, VarDecl.class, Param.class);
    if (element instanceof VarDecl || element instanceof Param) return element;
    if (named != null) return named;
    var ref = PsiTreeUtil.getParentOfType(element, GosuRefExpr.class, false);
    if (ref != null && ref.getReference() != null) {
      var target = ref.getReference().resolve();
      if (target instanceof VarDecl || target instanceof Param) return target;
    }
    return null;
  }
  public static Explanation explain(PsiElement declaration) { return explain(declaration, new HashSet<>(), 0); }
  private static Explanation explain(PsiElement declaration, Set<PsiElement> visited, int depth) {
    var type = GosuMembers.infer(declaration);
    String label = GosuMembers.typeLabel(type);
    if (depth > 5 || !visited.add(declaration)) return new Explanation(label, List.of("Further references were skipped to avoid a cycle."));
    String explicit = GosuSignatures.declaredType(declaration);
    if (!explicit.isBlank()) return new Explanation(label, List.of("The type is declared explicitly: : " + explicit + "."
        + (label.isEmpty() ? " The plugin could not find the definition of this type; check the imports, JDK, and classpath." : "")));
    if (label.isEmpty()) return new Explanation("", List.of("The plugin cannot determine the type unambiguously from the available code.",
        "A declaration or library may be missing, or the expression is outside the supported inference. Check the SDK diagnostics or declare the type explicitly."));
    if (declaration instanceof Param && declaration.getParent() instanceof GosuLambda) return new Explanation(label,
        List.of("The type " + label + " comes from the expected parameter of the Java functional interface that the block is passed to."));
    var ts = GosuSource.tokens(declaration.getContainingFile());
    int after = GosuSource.before(ts, declaration.getTextRange().getEndOffset()) + 1;
    if (after < ts.size() && ts.get(after).text().equals("in")) return new Explanation(label,
        List.of("The loop variable takes the element type of the collection or array after in: " + label + "."));
    var initializer = GosuMembers.initializer(declaration);
    if (initializer == null) return new Explanation(label, List.of("The type " + label + " comes from the available declaration."));
    int first = initializer.first(), last = initializer.last();
    String firstText = ts.get(first).text();
    List<String> reasons = new ArrayList<>();
    if (first == last) {
      if (ts.get(first).kind() == gosu.gsp.lexer.GosuTokenTypes.IDENTIFIER) {
        PsiElement source = GosuReference.findUnqualified(firstText, declaration);
        reasons.add("The initial value comes from the variable or parameter " + firstText + " of type " + label + ".");
        if (source instanceof VarDecl || source instanceof Param) reasons.addAll(explain(source, visited, depth + 1).reasons());
      } else reasons.add("The type " + label + " comes from the literal " + firstText + ".");
    } else if (simpleChain(ts, first, last) && ts.get(last).text().equals(")")) {
      int open = matchingOpen(ts, last);
      if (open > first && open >= 2 && GosuSource.dot(ts.get(open - 2))) {
        int name = open - 1;
        var receiver = GosuMembers.receiverAt(declaration, ts.get(name).start());
        var candidates = GosuCallTypes.select(GosuMembers.at(declaration, ts.get(name).start()), ts.get(name).text(), declaration, ts, open, 0);
        if (receiver != null) reasons.add("The call receiver has type " + GosuMembers.typeLabel(receiver) + ".");
        reasons.add("The call " + ts.get(name).text() + "(...) has the recognized result type " + label + ".");
        if (candidates.size() == 1) {
          var target = candidates.getFirst().target();
          String origin = GosuMethodOrigin.describe(target, receiver);
          if (!origin.isEmpty()) reasons.add(origin);
          if (target instanceof PsiMethod method && method.getReturnType() != null
              && !method.getReturnType().equals(type.javaType())) reasons.add("Declared method result: " + method.getReturnType().getPresentableText()
                  + ". Generic parameters were substituted from the receiver type or the call arguments.");
        } else if (candidates.size() > 1) reasons.add("Several matching overloads share this result type; the plugin does not pick one of them.");
      } else if (firstText.equals("new")) reasons.add("The new expression creates an object of type " + label + "; the variable takes that type together with its generic arguments.");
      else {
        PsiElement function = open > first ? GosuReference.findUnqualified(ts.get(open - 1).text(), declaration) : null;
        if (function instanceof FunctionDecl fn) reasons.add("The result comes from the function " + fn.getName() + ", whose declared result type is " + GosuSignatures.declaredType(fn) + ".");
      }
    } else if (firstText.equals("{") && ts.get(last).text().equals("}")) reasons.add("The collection type " + label + " comes from the identical recognized types of its elements.");
    if (reasons.isEmpty()) reasons.add("The plugin determined " + label + " through a simplified analysis of the initial value. There is no detailed explanation for this expression yet; the SDK diagnostics are an independent check.");
    return new Explanation(label, List.copyOf(reasons));
  }
  private static int matchingOpen(List<GosuSource.Token> tokens, int last) {
    int depth = 0;
    for (int i = last; i >= 0; i--) {
      if (tokens.get(i).text().equals(")")) depth++;
      if (tokens.get(i).text().equals("(") && --depth == 0) return i;
    }
    return -1;
  }
  private static boolean simpleChain(List<GosuSource.Token> tokens, int first, int last) {
    int depth = 0;
    for (int i = first; i <= last; i++) {
      String text = tokens.get(i).text();
      if (depth == 0 && Set.of("+", "-", "*", "/", "?:", "as", "and", "or", "==").contains(text)) return false;
      if (text.equals("(")) depth++;
      if (text.equals(")")) depth--;
    }
    return true;
  }
  private static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
