package gosu.gsp.intentions;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import gosu.gsp.lexer.*;
import java.util.*;

/** Shared, conservative eligibility checks for null handling actions. */
public final class GosuNullSupport {
  private GosuNullSupport() {}
  public record Access(int dot, int receiverStart, VarDecl variable) {}

  public static Access access(PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (!(file instanceof GosuFile)) return null;
    var tokens = GosuSource.tokens(file);
    int offset = element.getTextOffset();
    for (int i = 1; i + 2 < tokens.size(); i++) {
      var dot = tokens.get(i);
      if (!dot.text().equals(".") || tokens.get(i - 1).text().equals("?")) continue;
      if (offset < dot.start() || offset > tokens.get(i + 1).end() || !tokens.get(i + 2).text().equals("(")) continue;
      var receiver = GosuMembers.expression(element, tokens, i - 1, 0);
      if (!referenceType(receiver)) return null;
      PsiReference ref = file.findReferenceAt(tokens.get(i - 1).start());
      PsiElement declaration = ref == null ? null : ref.resolve();
      return new Access(dot.start(), tokens.get(i - 1).start(), declaration instanceof VarDecl v ? v : null);
    }
    return null;
  }

  private static boolean referenceType(GosuMembers.Type type) {
    return type != null && !type.staticAccess() && !(type.javaType() instanceof PsiPrimitiveType);
  }

  public static boolean immediatelyNull(PsiElement element) {
    var access = access(element);
    if (access == null || access.variable() == null || access.variable().isMember()) return false;
    var init = GosuMembers.initializer(access.variable());
    if (init == null || init.first() != init.last()) return false;
    var tokens = GosuSource.tokens(element.getContainingFile());
    var value = tokens.get(init.first());
    if (!value.text().equals("null") || value.end() > access.receiverStart()) return false;
    String between = element.getContainingFile().getText().substring(value.end(), access.receiverStart());
    // No intervening condition, write, call or control-flow boundary: no speculative null analysis.
    return GosuSource.tokenize(between).stream().allMatch(t -> t.text().equals(";"));
  }

  public static GosuRefExpr fallbackTarget(PsiElement element) {
    var ref = PsiTreeUtil.getParentOfType(element, GosuRefExpr.class, false);
    if (ref == null || !(ref.getContainingFile() instanceof GosuFile) || ref.getReference() == null) return null;
    var declaration = ref.getReference().resolve();
    if (!(declaration instanceof VarDecl) && !(declaration instanceof Param)) return null;
    if (!referenceType(GosuMembers.infer(declaration))) return null;
    var ts = GosuSource.tokens(ref.getContainingFile());
    int before = GosuSource.before(ts, ref.getTextOffset());
    if (before >= 0 && GosuSource.dot(ts.get(before))) return null;
    int after = GosuSource.before(ts, ref.getTextRange().getEndOffset()) + 1;
    if (after < ts.size() && Set.of("=", "+=", "-=", "*=", "/=", "++", "--").contains(ts.get(after).text())) return null;
    return ref;
  }

  public static boolean validFallback(String text) {
    if (text.isBlank() || text.contains("\n") || text.contains("\r")) return false;
    GosuLexer lexer = new GosuLexer();
    lexer.start(text);
    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() == GosuTokenTypes.LINE_COMMENT || lexer.getTokenType() == GosuTokenTypes.BLOCK_COMMENT) return false;
      lexer.advance();
    }
    Deque<String> stack = new ArrayDeque<>();
    for (var token : GosuSource.tokenize(text)) {
      String s = token.text();
      if (Set.of(";", "=", "return", "var", "\\", "->").contains(s)) return false;
      if (Set.of("(", "[", "{").contains(s)) stack.push(s);
      if (Set.of(")", "]", "}").contains(s)) {
        if (stack.isEmpty() || !stack.pop().equals(s.equals(")") ? "(" : s.equals("]") ? "[" : "{")) return false;
      }
    }
    return stack.isEmpty();
  }

  public static String fallback(PsiElement element, String expression) {
    var target = fallbackTarget(element);
    if (target == null || !validFallback(expression)) throw new IllegalArgumentException("Select a read of a reference-type variable and enter a single expression.");
    return "(" + target.getText() + " ?: " + expression.strip() + ")";
  }
}
