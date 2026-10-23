package gosu.gsp.parser;

import com.intellij.lang.ITokenTypeRemapper;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespaceSkippedCallback;
import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import gosu.gsp.lexer.GosuLexer;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.psi.GosuElementTypes;
import gosu.gsp.psi.GosuParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural tests for {@link GosuParser} against a recording fake
 * {@link PsiBuilder}. The fake mirrors the one behavior the parser relies on:
 * {@code getTokenType}/{@code advanceLexer} transparently skip whitespace and
 * comments, exactly like the real builder.
 */
public class GosuParserTest {
  // --- recording tree ---

  sealed interface Child permits Tok, Node {
  }

  record Tok(IElementType type, String text) implements Child {
  }

  static final class Node implements Child {
    final IElementType type;
    final List<Child> children = new ArrayList<>();

    Node(IElementType type) {
      this.type = type;
    }

    List<Node> nodes(IElementType type) {
      return children.stream()
          .filter(c -> c instanceof Node n && n.type == type)
          .map(c -> (Node) c)
          .toList();
    }

    /** All descendant nodes of the given type, in document order. */
    List<Node> desc(IElementType type) {
      List<Node> out = new ArrayList<>();
      collect(this, type, out);
      return out;
    }

    private static void collect(Node node, IElementType type, List<Node> out) {
      for (Child c : node.children) {
        if (c instanceof Node n) {
          if (n.type == type) {
            out.add(n);
          }
          collect(n, type, out);
        }
      }
    }

    List<Node> allNodes() {
      return children.stream()
          .filter(c -> c instanceof Node)
          .map(c -> (Node) c)
          .toList();
    }

    String firstIdent() {
      return children.stream()
          .filter(c -> c instanceof Tok t && t.type == GosuTokenTypes.IDENTIFIER)
          .map(c -> ((Tok) c).text)
          .findFirst().orElse(null);
    }

    boolean hasToken(IElementType type, String text) {
      return children.stream().anyMatch(c -> c instanceof Tok t
          && t.type == type && t.text.equals(text));
    }

    @Override
    public String toString() {
      return type + "" + children;
    }
  }

  // --- fake builder ---

  static final class FakeBuilder extends UserDataHolderBase implements PsiBuilder {
    private final String text;
    private final List<Tok> toks = new ArrayList<>();
    private final List<Integer> offsets = new ArrayList<>();
    private int pos;
    private int steps;
    private final Deque<Node> stack = new ArrayDeque<>();
    private Node root;

    FakeBuilder(String text) {
      this.text = text;
      GosuLexer lexer = new GosuLexer();
      lexer.start(text, 0, text.length(), 0);
      while (lexer.getTokenType() != null) {
        toks.add(new Tok(lexer.getTokenType(),
            text.substring(lexer.getTokenStart(), lexer.getTokenEnd())));
        offsets.add(lexer.getTokenStart());
        lexer.advance();
      }
      pos = nextSignificant(0);
    }

    private boolean significant(int i) {
      IElementType t = toks.get(i).type;
      return t != TokenType.WHITE_SPACE
          && t != GosuTokenTypes.LINE_COMMENT
          && t != GosuTokenTypes.BLOCK_COMMENT;
    }

    private int nextSignificant(int from) {
      int i = from;
      while (i < toks.size() && !significant(i)) {
        i++;
      }
      return i;
    }

    Node root() {
      return root;
    }

    @Override
    public @Nullable IElementType getTokenType() {
      return pos < toks.size() ? toks.get(pos).type : null;
    }

    @Override
    public @Nullable String getTokenText() {
      return pos < toks.size() ? toks.get(pos).text : null;
    }

    @Override
    public void advanceLexer() {
      if (++steps > 100_000) {
        throw new IllegalStateException("Parser did not terminate on: " + text);
      }
      if (!stack.isEmpty() && pos < toks.size()) {
        stack.peek().children.add(toks.get(pos));
      }
      pos = nextSignificant(pos + 1);
    }

    @Override
    public boolean eof() {
      return getTokenType() == null;
    }

    @Override
    public @NotNull Marker mark() {
      Node frame = new Node(null);
      stack.push(frame);
      return new FakeMarker(frame);
    }

    @Override
    public @Nullable IElementType lookAhead(int steps) {
      int i = pos;
      for (int s = 0; s <= steps; s++) {
        i = nextSignificant(i);
        if (i >= toks.size()) {
          return null;
        }
        if (s < steps) {
          i++;
        }
      }
      return toks.get(i).type;
    }

    @Override
    public int getCurrentOffset() {
      return pos < toks.size() ? offsets.get(pos) : text.length();
    }

    @Override
    public @NotNull CharSequence getOriginalText() {
      return text;
    }

    @Override
    public @Nullable Project getProject() {
      return null;
    }

    final class FakeMarker implements Marker {
      private final Node frame;
      private boolean done;

      FakeMarker(Node frame) {
        this.frame = frame;
      }

      @Override
      public @NotNull Marker precede() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void drop() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void rollbackTo() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void done(@NotNull IElementType type) {
        if (done) {
          throw new IllegalStateException("double done: " + type);
        }
        done = true;
        if (stack.pop() != frame) {
          throw new IllegalStateException("unbalanced markers at: " + type);
        }
        Node node = new Node(type);
        node.children.addAll(frame.children);
        if (stack.isEmpty()) {
          root = node;
        }
        else {
          stack.peek().children.add(node);
        }
      }

      @Override
      public void error(@NotNull String message) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void errorBefore(@NotNull String message, @NotNull Marker marker) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void doneBefore(@NotNull IElementType type, @NotNull Marker marker) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void doneBefore(@NotNull IElementType type, @NotNull Marker marker,
          @NotNull String errorMessage) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void setCustomEdgeTokenBinders(@Nullable WhitespacesAndCommentsBinder left,
          @Nullable WhitespacesAndCommentsBinder right) {
      }

      @Override
      public void collapse(@NotNull IElementType type) {
        throw new UnsupportedOperationException();
      }
    }

    // Unused by the parser.
    @Override
    public void error(String messageText) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setDebugMode(boolean dbgMode) {
    }

    @Override
    public void setTokenTypeRemapper(ITokenTypeRemapper remapper) {
    }

    @Override
    public void setWhitespaceSkippedCallback(WhitespaceSkippedCallback callback) {
    }

    @Override
    public @Nullable FlyweightCapableTreeStructure<LighterASTNode> getLightTree() {
      return null;
    }

    @Override
    public com.intellij.lang.ASTNode getTreeBuilt() {
      // The recording fake never builds a real tree; the test asserts on
      // root() instead. Still, the contract is @NotNull, so return a stub.
      return new StubNode(text);
    }

    @Override
    public @Nullable Marker getLatestDoneMarker() {
      return null;
    }

    @Override
    public void enforceCommentTokens(@NotNull TokenSet tokens) {
    }

    @Override
    public int rawTokenIndex() {
      return pos;
    }

    @Override
    public int rawTokenTypeStart(int rawIndex) {
      return rawIndex < offsets.size() ? offsets.get(rawIndex) : text.length();
    }

    @Override
    public @Nullable IElementType rawLookup(int rawOffset) {
      int i = pos + rawOffset;
      return i >= 0 && i < toks.size() ? toks.get(i).type : null;
    }

    @Override
    public void remapCurrentToken(@NotNull IElementType type) {
      throw new UnsupportedOperationException();
    }
  }

  /** Non-null placeholder satisfying the @NotNull getTreeBuilt() contract. */
  static final class StubNode extends com.intellij.openapi.util.UserDataHolderBase
      implements com.intellij.lang.ASTNode {
    private final String text;

    StubNode(String text) {
      this.text = text;
    }

    @Override
    public @NotNull IElementType getElementType() {
      return new IElementType("GOSU_STUB", gosu.gsp.GosuLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getText() {
      return text;
    }

    @Override
    public @NotNull CharSequence getChars() {
      return text;
    }

    @Override
    public @Nullable PsiElement getPsi() {
      return null;
    }

    @Override
    public <T extends PsiElement> @Nullable T getPsi(@NotNull Class<T> clazz) {
      return null;
    }

    @Override
    public @Nullable com.intellij.lang.ASTNode getTreeParent() {
      return null;
    }

    @Override
    public @Nullable com.intellij.lang.ASTNode getTreePrev() {
      return null;
    }

    @Override
    public com.intellij.lang.ASTNode getTreeNext() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable com.intellij.lang.ASTNode getFirstChildNode() {
      return null;
    }

    @Override
    public @Nullable com.intellij.lang.ASTNode getLastChildNode() {
      return null;
    }

    @Override
    public com.intellij.lang.ASTNode @NotNull [] getChildren(@Nullable TokenSet filter) {
      return new com.intellij.lang.ASTNode[0];
    }

    @Override
    public @Nullable com.intellij.lang.ASTNode findChildByType(@NotNull IElementType type) {
      return null;
    }

    @Override
    public @Nullable com.intellij.lang.ASTNode findChildByType(@NotNull TokenSet types) {
      return null;
    }

    @Override
    public @Nullable com.intellij.lang.ASTNode findChildByType(@NotNull TokenSet types,
        @Nullable com.intellij.lang.ASTNode anchor) {
      return null;
    }

    @Override
    public @Nullable com.intellij.lang.ASTNode findChildByType(@NotNull IElementType type,
        @Nullable com.intellij.lang.ASTNode anchor) {
      return null;
    }

    @Override
    public int getStartOffset() {
      return 0;
    }

    @Override
    public int getTextLength() {
      return text.length();
    }

    @Override
    public @NotNull TextRange getTextRange() {
      return new TextRange(0, text.length());
    }

    @Override
    public @Nullable com.intellij.lang.ASTNode findLeafElementAt(int offset) {
      return null;
    }

    @Override
    public boolean textContains(char c) {
      return text.indexOf(c) >= 0;
    }

    @Override
    public com.intellij.lang.ASTNode copyElement() {
      return new StubNode(text);
    }

    @Override
    public int getStartOffsetInParent() {
      return 0;
    }

    @Override
    public void addChild(@NotNull com.intellij.lang.ASTNode child) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addChild(@NotNull com.intellij.lang.ASTNode child,
        @Nullable com.intellij.lang.ASTNode anchorBefore) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addLeaf(@NotNull IElementType leafType, @NotNull CharSequence leafText,
        @Nullable com.intellij.lang.ASTNode anchorBefore) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeChild(@NotNull com.intellij.lang.ASTNode child) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeRange(@NotNull com.intellij.lang.ASTNode firstNodeToRemove,
        @Nullable com.intellij.lang.ASTNode firstNodeToKeep) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void replaceChild(@NotNull com.intellij.lang.ASTNode oldChild,
        @NotNull com.intellij.lang.ASTNode newChild) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void replaceAllChildrenToChildrenOf(@NotNull com.intellij.lang.ASTNode anotherParent) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addChildren(@NotNull com.intellij.lang.ASTNode firstChild,
        @Nullable com.intellij.lang.ASTNode firstNodeToKeep,
        @Nullable com.intellij.lang.ASTNode anchorBefore) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Object clone() {
      return new StubNode(text);
    }
  }

  private static Node parse(String text) {
    FakeBuilder b = new FakeBuilder(text);
    new GosuParser().parse(
        new com.intellij.psi.tree.IFileElementType("GOSU_TEST", gosu.gsp.GosuLanguage.INSTANCE), b);
    assertTrue(b.stack.isEmpty(), "unclosed markers");
    return b.root;
  }

  // --- tests ---

  @Test
  void topLevelVar() {
    Node file = parse("var x = 1");
    List<Node> vars = file.nodes(GosuElementTypes.VAR_DECL);
    assertEquals(1, vars.size());
    assertEquals("x", vars.get(0).firstIdent());
    // The initializer stays outside the declaration node (flat expression tail).
    assertTrue(file.hasToken(GosuTokenTypes.NUMBER, "1"));
  }

  @Test
  void functionWithParamsAndLocals() {
    Node file = parse("function rate(policy : String) : String { var msg = policy }");
    List<Node> fns = file.desc(GosuElementTypes.FUNCTION_DECL);
    assertEquals(1, fns.size());
    Node fn = fns.get(0);
    assertEquals("rate", fn.firstIdent());
    List<Node> params = fn.desc(GosuElementTypes.PARAM);
    assertEquals(1, params.size());
    assertEquals("policy", params.get(0).firstIdent());
    assertEquals(1, params.get(0).desc(GosuElementTypes.TYPE_REF).size());
    assertEquals(1, fn.desc(GosuElementTypes.VAR_DECL).size());
    // 'policy' use inside the body is a reference, not a declaration.
    List<Node> refs = fn.desc(GosuElementTypes.REF_EXPR);
    assertEquals(1, refs.size());
    assertEquals("policy", refs.get(0).firstIdent());
  }

  @Test
  void classMembersAndMethod() {
    Node file = parse("class Q { var premium : String as Update function rate() { return premium } }");
    List<Node> classes = file.desc(GosuElementTypes.CLASS_DECL);
    assertEquals(1, classes.size());
    Node cls = classes.get(0);
    assertEquals("Q", cls.firstIdent());
    List<Node> members = cls.desc(GosuElementTypes.VAR_DECL);
    assertEquals(1, members.size());
    assertEquals("premium", members.get(0).firstIdent());
    assertEquals(1, cls.desc(GosuElementTypes.FUNCTION_DECL).size());
  }

  @Test
  void loopCatchUsingVars() {
    Node file = parse("""
        for (p in items index i) { print(p) }
        for (k = 1; k < 2; k++) { print(k) }
        try { x() } catch (e : Exception) { print(e) }
        using (var r = open()) { print(r) }
        using (a, b) { print(a) }""");
    List<String> varNames = file.desc(GosuElementTypes.VAR_DECL).stream()
        .map(Node::firstIdent).toList();
    // p, i, e, r declared; k (C-style) and a, b (plain uses) are references.
    assertEquals(List.of("p", "i", "e", "r"), varNames);
  }

  @Test
  void newAndUsesProduceTypeRefs() {
    Node file = parse("uses java.util.ArrayList\nvar c = new FileContainer()");
    assertEquals(1, file.desc(GosuElementTypes.USES_DECL).size());
    List<Node> types = file.desc(GosuElementTypes.TYPE_REF);
    assertEquals(1, types.size());
    assertEquals("FileContainer", types.get(0).firstIdent());
  }

  @Test
  void labelsAreNotDeclarations() {
    Node file = parse("var c = new FileContainer() {\n  :DestFile = jarFile,\n  :Update = true\n}");
    assertEquals(1, file.desc(GosuElementTypes.VAR_DECL).size()); // only 'c'
    List<Node> refs = file.desc(GosuElementTypes.REF_EXPR);
    assertTrue(refs.stream().anyMatch(n -> "jarFile".equals(n.firstIdent())));
  }

  @Test
  void enhancementAndProperty() {
    Node file = parse("""
        package example
        enhancement E : String {
          public function m() : String { return "x" }
          public property get P() : String { return "y" }
        }""");
    List<Node> classes = file.desc(GosuElementTypes.CLASS_DECL);
    assertEquals(1, classes.size());
    assertEquals("E", classes.get(0).firstIdent());
    assertTrue(classes.get(0).desc(GosuElementTypes.TYPE_REF).size() >= 1); // : String, returns
    assertEquals(1, classes.get(0).desc(GosuElementTypes.FUNCTION_DECL).size());
    assertEquals(1, classes.get(0).desc(GosuElementTypes.VAR_DECL).size()); // property P
  }

  @Test
  void enumConstantsAreDeclarations() {
    Node file = parse("enum Color { RED, GREEN, BLUE }");
    List<String> names = file.desc(GosuElementTypes.VAR_DECL).stream()
        .map(Node::firstIdent).toList();
    assertEquals(List.of("RED", "GREEN", "BLUE"), names);
  }

  @Test
  void enumWithMembers() {
    Node file = parse("enum E { A, B; function f() { return A } }");
    List<String> names = file.desc(GosuElementTypes.VAR_DECL).stream()
        .map(Node::firstIdent).toList();
    assertEquals(List.of("A", "B"), names);
    assertEquals(1, file.desc(GosuElementTypes.FUNCTION_DECL).size());
  }

  @Test
  void brokenInputStillTerminates() {
    // Unclosed everything: must terminate with a tree, never hang or throw.
    Node file = parse("class Broken { function f( { var x = ");
    assertTrue(file.nodes(GosuElementTypes.CLASS_DECL).size() >= 1);
  }
}
