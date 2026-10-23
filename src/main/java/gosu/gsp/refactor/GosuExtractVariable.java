package gosu.gsp.refactor;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import gosu.gsp.editor.GosuNamesValidator;
import java.util.*;

/** Conservative extraction of a complete initializer or return expression. */
public final class GosuExtractVariable {
  private GosuExtractVariable() {}
  public record Selection(int start, int end, int lineStart, String indent, String expression, String explicitType) {}

  public static Selection selection(PsiFile file, int start, int end) {
    if (!(file instanceof GosuFile) || start < 0 || end > file.getTextLength() || start >= end) return null;
    String source = file.getText();
    while (start < end && Character.isWhitespace(source.charAt(start))) start++;
    while (end > start && Character.isWhitespace(source.charAt(end - 1))) end--;
    if (start == end) return null;
    int line = source.lastIndexOf('\n', start - 1) + 1;
    int lineEnd = source.indexOf('\n', end);
    if (lineEnd < 0) lineEnd = source.length();
    String suffix = source.substring(end, lineEnd).strip();
    if (!suffix.isEmpty() && !suffix.equals(";")) return null;
    String prefix = source.substring(line, start);
    String indent = prefix.substring(0, prefix.length() - prefix.stripLeading().length());
    String head = prefix.strip();
    PsiElement context = file.findElementAt(start);
    if (context == null || PsiTreeUtil.getParentOfType(context, GosuLambda.class) != null) return null;
    String explicit = "";
    if (head.equals("return")) {
      FunctionDecl fn = PsiTreeUtil.getParentOfType(context, FunctionDecl.class);
      if (fn == null) return null;
      explicit = GosuSignatures.declaredType(fn);
      if (explicit.isBlank() || explicit.equals("void")) return null;
    } else {
      VarDecl found = null;
      for (var decl : PsiTreeUtil.findChildrenOfType(file, VarDecl.class)) {
        if (decl.getTextOffset() < line || decl.getTextOffset() >= start || decl.isMember()) continue;
        var range = GosuMembers.initializer(decl);
        var ts = GosuSource.tokens(file);
        if (range != null && ts.get(range.first()).start() == start && ts.get(range.last()).end() == end) found = decl;
      }
      if (found == null || !head.startsWith("var ") || !head.endsWith("=")) return null;
      explicit = GosuSignatures.declaredType(found);
    }
    String expression = source.substring(start, end);
    if (!validExpression(expression) || (expression.equals("null") && explicit.isBlank())) return null;
    return new Selection(start, end, line, indent, expression, explicit);
  }

  private static boolean validExpression(String expression) {
    var ts = GosuSource.tokenize(expression);
    if (ts.isEmpty()) return false;
    Deque<String> stack = new ArrayDeque<>();
    for (var token : ts) {
      String s = token.text();
      if (Set.of(";", "\\", "->", "return", "break", "continue", "var", "++", "--", "=", "+=", "-=", "*=", "/=").contains(s)) return false;
      if (Set.of("(", "[", "{").contains(s)) stack.push(s);
      if (Set.of(")", "]", "}").contains(s)) {
        if (stack.isEmpty() || !stack.pop().equals(s.equals(")") ? "(" : s.equals("]") ? "[" : "{")) return false;
      }
    }
    return stack.isEmpty();
  }

  public static boolean validName(PsiFile file, String name) {
    return new GosuNamesValidator().isIdentifier(name, file.getProject())
        && GosuSource.tokens(file).stream().noneMatch(t -> t.text().equals(name));
  }
  public static String transform(PsiFile file, int start, int end, String name) {
    Selection s = selection(file, start, end);
    if (s == null || !validName(file, name)) throw new IllegalArgumentException("The variable cannot be safely extracted from this selection, or the name is already in use.");
    String source = file.getText();
    String declaration = s.indent() + "var " + name + (s.explicitType().isBlank() ? "" : " : " + s.explicitType()) + " = " + s.expression() + "\n";
    return source.substring(0, s.lineStart()) + declaration + source.substring(s.lineStart(), s.start()) + name + source.substring(s.end());
  }
}
