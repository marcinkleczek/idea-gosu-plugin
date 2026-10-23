package gosu.gsp.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import gosu.gsp.psi.GosuDecls;
import org.jetbrains.annotations.Nullable;

/** Stub for a function declaration (name only; params resolve from PSI). */
public final class GosuFunctionStub extends StubBase<GosuDecls.FunctionDecl>
    implements NamedStub<GosuDecls.FunctionDecl> {
  private final String name;

  public GosuFunctionStub(@Nullable StubElement<?> parent, @Nullable String name) {
    super(parent, GosuStubElementTypes.FUNCTION);
    this.name = name;
  }

  @Override
  public @Nullable String getName() {
    return name;
  }
}
