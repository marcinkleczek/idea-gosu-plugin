package gosu.gsp.inspection;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.GosuSource;
import gosu.gsp.lexer.GosuTokenTypes;
import java.util.*;

/** Intraprocedural null facts for straight-line code and braced if/else; unknowns stay unknown. */
public final class GosuDataFlow {
  private GosuDataFlow() {}
  private enum Value { NULL, NON_NULL, MAYBE_NULL, UNKNOWN }
  public enum Kind { NULL_ACCESS, MAYBE_NULL_ACCESS, CONSTANT_CONDITION, UNREACHABLE }
  public record Finding(int start, int end, Kind kind, String message) {}
  private static final class State {
    final Map<PsiElement, Value> values = new HashMap<>();
    boolean reachable = true;
    State copy() { var s = new State(); s.values.putAll(values); s.reachable = reachable; return s; }
    Value get(PsiElement key) { return values.getOrDefault(key, Value.UNKNOWN); }
  }
  public static List<Finding> analyze(PsiFile file) {
    if (!(file instanceof GosuFile)) return List.of();
    return new Analyzer(file).analyze();
  }
  private static final class Analyzer {
    final PsiFile file;
    final String source;
    final List<GosuSource.Token> tokens;
    final List<Finding> findings = new ArrayList<>();
    final Map<Integer, Integer> close = new HashMap<>();
    final Map<Integer, Integer> declarations = new HashMap<>();
    Analyzer(PsiFile file) {
      this.file = file; source = file.getText(); tokens = GosuSource.tokens(file);
      Deque<Integer> stack = new ArrayDeque<>();
      for (int i = 0; i < tokens.size(); i++) {
        String t = text(i);
        if (Set.of("(", "[", "{").contains(t)) stack.push(i);
        else if (Set.of(")", "]", "}").contains(t) && !stack.isEmpty()) {
          int opening = stack.pop();
          if (text(opening).equals(t.equals(")") ? "(" : t.equals("]") ? "[" : "{")) close.put(opening, i);
        }
      }
      for (PsiElement d : PsiTreeUtil.collectElements(file, e -> e instanceof FunctionDecl || e instanceof ClassDecl)) {
        int first = index(d.getTextOffset());
        int last = GosuSource.before(tokens, d.getTextRange().getEndOffset());
        if (first >= 0 && last >= first) declarations.put(first, last + 1);
      }
    }
    List<Finding> analyze() {
      for (FunctionDecl fn : PsiTreeUtil.findChildrenOfType(file, FunctionDecl.class)) {
        int first = index(fn.getTextOffset()), end = GosuSource.before(tokens, fn.getTextRange().getEndOffset());
        for (int i = first; i <= end && i >= 0; i++) if (text(i).equals("{") && close.containsKey(i)) {
          walk(i + 1, close.get(i), new State(), 0); break;
        }
      }
      if (file.getName().endsWith(".gsp")) walk(0, tokens.size(), new State(), 0);
      return List.copyOf(findings);
    }
    String text(int i) { return i >= 0 && i < tokens.size() ? tokens.get(i).text() : ""; }
    int index(int offset) {
      int i = GosuSource.before(tokens, offset) + 1;
      return i < tokens.size() && tokens.get(i).start() == offset ? i : -1;
    }
    PsiElement variable(int i) {
      if (i < 0 || i >= tokens.size()) return null;
      var leaf = file.findElementAt(tokens.get(i).start());
      if (leaf == null) return null;
      var reference = file.findReferenceAt(tokens.get(i).start());
      PsiElement target = reference == null ? PsiTreeUtil.getParentOfType(leaf, VarDecl.class, Param.class) : reference.resolve();
      if (target instanceof VarDecl v && !v.isMember() || target instanceof Param) return target;
      return null;
    }
    void note(int i, Kind kind, String message) {
      if (i < 0 || i >= tokens.size()) return;
      var t = tokens.get(i);
      if (findings.stream().noneMatch(f -> f.start() == t.start() && f.kind() == kind)) findings.add(new Finding(t.start(), t.end(), kind, message));
    }
    State walk(int from, int end, State state, int recursion) {
      if (recursion > 30) { state.values.clear(); return state; }
      boolean warnedUnreachable = false;
      for (int i = from; i < end;) {
        ProgressManager.checkCanceled();
        String word = text(i);
        if (word.equals(";")) { i++; continue; }
        if (declarations.containsKey(i)) { i = declarations.get(i); continue; }
        if (!state.reachable) {
          if (!warnedUnreachable) note(i, Kind.UNREACHABLE, "Unreachable code: an earlier statement ends this execution path.");
          warnedUnreachable = true; i = statementEnd(i, end); continue;
        }
        if (word.equals("if") && text(i + 1).equals("(") && close.containsKey(i + 1)) {
          int conditionEnd = close.get(i + 1), thenOpen = conditionEnd + 1;
          if (!text(thenOpen).equals("{") || !close.containsKey(thenOpen)) { state.values.clear(); i = skipIf(i, end); continue; }
          State yes = state.copy(), no = state.copy();
          refine(i + 2, conditionEnd, yes, no);
          int thenEnd = close.get(thenOpen), next = thenEnd + 1;
          yes = walk(thenOpen + 1, thenEnd, yes, recursion + 1);
          if (text(next).equals("else")) {
            if (text(next + 1).equals("{") && close.containsKey(next + 1)) {
              int elseEnd = close.get(next + 1);
              no = walk(next + 2, elseEnd, no, recursion + 1); next = elseEnd + 1;
            } else { state.values.clear(); i = skipIf(i, end); continue; }
          }
          state = join(yes, no); i = next; continue;
        }
        if (Set.of("while", "for", "do", "try", "catch", "finally", "switch", "using").contains(word)) {
          // Loop iterations, exceptional edges and resource scopes need a richer CFG.
          state.values.clear(); i = statementEnd(i, end); continue;
        }
        if (word.equals("{")) {
          // A bare nested block can contain shadowed values or deferred blocks: do not extrapolate.
          state.values.clear(); i = close.getOrDefault(i, i) + 1; continue;
        }
        int next = statementEnd(i, end);
        int last = next;
        if (text(last - 1).equals(";")) last--;
        if (has(i, last, "\\") || has(i, last, "->") || interpolated(i, last)) { state.values.clear(); i = next; continue; }
        checkAccesses(i, last, state);
        Value assigned = Value.UNKNOWN;
        PsiElement target = null;
        int equals = -1;
        if (word.equals("var")) {
          target = variable(i + 1);
          for (int j = i + 2; j < last; j++) if (text(j).equals("=")) { equals = j; break; }
        } else if (text(i + 1).equals("=")) { target = variable(i); equals = i + 1; }
        if (target != null && equals >= 0) assigned = value(equals + 1, last, state);
        // Calls may invoke closures or modify script state; retained facts would be speculative.
        if (hasCall(i, last) || has(i, last, ".") || has(i, last, "[") || mutates(i, last)) state.values.clear();
        if (target != null) state.values.put(target, assigned);
        if (word.equals("return") || word.equals("throw")) state.reachable = false;
        i = next;
      }
      return state;
    }
    boolean mutates(int from, int end) {
      for (int i = from; i < end; i++) if (Set.of("+=", "-=", "*=", "/=", "%=", "++", "--").contains(text(i))) return true;
      return false;
    }
    int skipIf(int from, int end) {
      if (!text(from).equals("if") || !close.containsKey(from + 1)) return statementEnd(from, end);
      int body = close.get(from + 1) + 1;
      int next = text(body).equals("{") && close.containsKey(body) ? close.get(body) + 1 : statementEnd(body, end);
      if (text(next).equals("else")) {
        if (text(next + 1).equals("if")) return skipIf(next + 1, end);
        return text(next + 1).equals("{") && close.containsKey(next + 1) ? close.get(next + 1) + 1 : statementEnd(next + 1, end);
      }
      return next;
    }
    int statementEnd(int from, int end) {
      int i = from;
      for (; i < end; i++) {
        if (i > from && source.substring(tokens.get(i - 1).end(), tokens.get(i).start()).contains("\n")) return i;
        if (text(i).equals(";")) return i + 1;
        if (close.containsKey(i)) i = close.get(i);
        else if (text(i).equals("}")) return Math.max(from + 1, i);
      }
      return Math.max(from + 1, i);
    }
    boolean has(int from, int end, String wanted) { for (int i = from; i < end; i++) if (text(i).equals(wanted)) return true; return false; }
    boolean interpolated(int from, int end) {
      for (int i = from; i < end; i++) if (tokens.get(i).kind() == GosuTokenTypes.STRING && (text(i).contains("${") || text(i).contains("<%"))) return true;
      return false;
    }
    boolean hasCall(int from, int end) {
      for (int i = from + 1; i < end; i++) if (text(i).equals("(") && (tokens.get(i - 1).kind() == GosuTokenTypes.IDENTIFIER || text(i - 1).equals(")"))) return true;
      return false;
    }
    Value value(int from, int end, State state) {
      while (text(from).equals("(") && Objects.equals(close.get(from), end - 1)) { from++; end--; }
      if (from >= end) return Value.UNKNOWN;
      if (end - from == 1) {
        if (text(from).equals("null")) return Value.NULL;
        if (tokens.get(from).kind() == GosuTokenTypes.STRING || tokens.get(from).kind() == GosuTokenTypes.CHAR_LITERAL || tokens.get(from).kind() == GosuTokenTypes.NUMBER || Set.of("true", "false").contains(text(from))) return Value.NON_NULL;
        return state.get(variable(from));
      }
      for (int i = from; i + 1 < end; i++) {
        if (text(i).equals("?:") || text(i).equals("?") && text(i + 1).equals(":")) {
          Value left = value(from, i, state), right = value(i + (text(i).equals("?:") ? 1 : 2), end, state);
          if (left == Value.NON_NULL) return left;
          if (left == Value.NULL) return right;
          return right == Value.NON_NULL ? Value.NON_NULL : Value.UNKNOWN;
        }
        if (close.containsKey(i)) i = close.get(i);
      }
      if (text(from).equals("new")) {
        for (int i = from + 1; i < end; i++) if (text(i).equals("("))
          return Objects.equals(close.get(i), end - 1) ? Value.NON_NULL : Value.UNKNOWN;
      }
      return Value.UNKNOWN;
    }
    void checkAccesses(int from, int end, State state) {
      // Compound conditions and expressions may guard their RHS by short circuiting.
      if (has(from, end, "and") || has(from, end, "or") || has(from, end, "&&") || has(from, end, "||") || has(from, end, "?") || has(from, end, "?:")) return;
      State current = state.copy();
      int nesting = 0;
      for (int i = from; i < end; i++) {
        if (text(i).equals("(") || text(i).equals("[")) {
          nesting++;
          if (text(i).equals("[") || i > from && tokens.get(i - 1).kind() == GosuTokenTypes.IDENTIFIER) current.values.clear();
        }
        if (text(i).equals(")") || text(i).equals("]")) nesting--;
        if (nesting > 0 && text(i).equals("=")) current.values.clear();
        if (!text(i).equals(".")) continue;
        if (i > from && i + 2 < end && text(i + 2).equals("(") && tokens.get(i - 1).kind() == GosuTokenTypes.IDENTIFIER
            && (i < 2 || !text(i - 2).equals("."))) {
          Value fact = current.get(variable(i - 1));
          if (fact == Value.NULL) note(i + 1, Kind.NULL_ACCESS, "The receiver is null on this path. Provide an object or use ?. to skip the call.");
          if (fact == Value.MAYBE_NULL) note(i + 1, Kind.MAYBE_NULL_ACCESS, "The receiver may be null after the branches merge. Check for null or use null-safe access (?.).");
        }
        // Property getters and earlier calls in this expression may change captured state.
        current.values.clear();
      }
    }

    void refine(int from, int end, State yes, State no) {
      while (text(from).equals("(") && Objects.equals(close.get(from), end - 1)) { from++; end--; }
      if (end - from == 1 && Set.of("true", "false").contains(text(from))) {
        if (text(from).equals("true")) no.reachable = false; else yes.reachable = false;
        note(from, Kind.CONSTANT_CONDITION, "The condition has the constant value " + text(from) + "."); return;
      }
      if (end - from == 3 && Set.of("==", "!=").contains(text(from + 1)) && (text(from).equals("null") || text(from + 2).equals("null"))) {
        PsiElement variable = variable(text(from).equals("null") ? from + 2 : from);
        if (variable != null) {
          Value known = yes.get(variable);
          boolean equal = text(from + 1).equals("==");
          yes.values.put(variable, equal ? Value.NULL : Value.NON_NULL);
          no.values.put(variable, equal ? Value.NON_NULL : Value.NULL);
          if (known == Value.NULL || known == Value.NON_NULL) {
            boolean truth = (known == Value.NULL) == equal;
            if (truth) no.reachable = false; else yes.reachable = false;
            note(from, Kind.CONSTANT_CONDITION, "The condition is always " + (truth ? "true" : "false") + " on this execution path.");
          }
          return;
        }
      }
      if (end - from == 1 && variable(from) != null) return;
      yes.values.clear(); no.values.clear();
    }
    State join(State yes, State no) {
      if (!yes.reachable) return no;
      if (!no.reachable) return yes;
      State result = new State();
      Set<PsiElement> keys = new HashSet<>(yes.values.keySet()); keys.addAll(no.values.keySet());
      for (var key : keys) {
        Value a = yes.get(key), b = no.get(key);
        result.values.put(key, a == b ? a : a == Value.UNKNOWN || b == Value.UNKNOWN ? Value.UNKNOWN : Value.MAYBE_NULL);
      }
      return result;
    }
  }
}
