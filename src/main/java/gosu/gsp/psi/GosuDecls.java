package gosu.gsp.psi;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.stubs.GosuClassStub;
import gosu.gsp.stubs.GosuFunctionStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Named declaration nodes (created by {@link GosuParserDefinition}). */
public final class GosuDecls {
  private GosuDecls() {
  }

  // NOTE: StubBasedPsiElementBase alone is NOT enough on 2026.2 — the base
  // no longer declares StubBasedPsiElement, and DefaultStubBuilder skips
  // stub creation (SEVERE in idea.log) for PSI failing that instanceof.
  private abstract static class StubNamedDecl<StubT extends com.intellij.psi.stubs.StubElement<?>>
      extends StubBasedPsiElementBase<StubT>
      implements PsiNameIdentifierOwner, StubBasedPsiElement<StubT> {
    StubNamedDecl(@NotNull ASTNode node) {
      super(node);
    }

    StubNamedDecl(@NotNull StubT stub, @NotNull IStubElementType<?, ?> nodeType) {
      super(stub, nodeType);
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
      ASTNode id = getNode().findChildByType(GosuTokenTypes.IDENTIFIER);
      return id == null ? null : id.getPsi();
    }

    @Override
    public PsiElement setName(@NotNull String name) {
      PsiElement id = getNameIdentifier();
      if (id != null) {
        id.replace(GosuElementFactory.createIdentifier(getProject(), name));
      }
      return this;
    }
  }

  /** {@code class} / {@code interface} / {@code enum} / {@code enhancement} / {@code structure}. */
  public static final class ClassDecl extends StubNamedDecl<GosuClassStub> {
    public ClassDecl(@NotNull ASTNode node) {
      super(node);
    }

    public ClassDecl(@NotNull GosuClassStub stub, @NotNull IStubElementType<?, ?> nodeType) {
      super(stub, nodeType);
    }

    @Override
    public @Nullable String getName() {
      GosuClassStub stub = getStub();
      if (stub != null) {
        return stub.getName();
      }
      PsiElement id = getNameIdentifier();
      return id == null ? null : id.getText();
    }

    @Override
    public String toString() {
      return "GosuClassDecl(" + getName() + ")";
    }
  }

  /** {@code function name(...)} or {@code construct(...)}. */
  public static final class FunctionDecl extends StubNamedDecl<GosuFunctionStub> {
    public FunctionDecl(@NotNull ASTNode node) {
      super(node);
    }

    public FunctionDecl(@NotNull GosuFunctionStub stub, @NotNull IStubElementType<?, ?> nodeType) {
      super(stub, nodeType);
    }

    @Override
    public @Nullable String getName() {
      GosuFunctionStub stub = getStub();
      if (stub != null && stub.getName() != null) {
        return stub.getName();
      }
      PsiElement id = getNameIdentifier();
      String name = id == null ? null : id.getText();
      return name == null ? "construct" : name;
    }

    @Override
    public String toString() {
      return "GosuFunctionDecl(" + getName() + ")";
    }
  }

  /** {@code var} / {@code property} / loop / catch / using variable. */
  public static final class VarDecl extends GosuNamedDecl {
    public VarDecl(@NotNull ASTNode node) {
      super(node);
    }

    /** True for class members ({@code var} directly in a class body). */
    public boolean isMember() {
      PsiElement e = getParent();
      while (e != null && !(e instanceof FunctionDecl) && !(e instanceof ClassDecl)
          && !(e instanceof GosuFile)) {
        e = e.getParent();
      }
      return e instanceof ClassDecl;
    }

    @Override
    public String toString() {
      return "GosuVarDecl(" + getName() + ")";
    }
  }

  /** Function parameter ({@code name [: Type]}). */
  public static final class Param extends GosuNamedDecl {
    public Param(@NotNull ASTNode node) {
      super(node);
    }

    @Override
    public String toString() {
      return "GosuParam(" + getName() + ")";
    }
  }
}
