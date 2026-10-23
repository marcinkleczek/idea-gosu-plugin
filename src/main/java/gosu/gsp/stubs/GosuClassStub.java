package gosu.gsp.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import gosu.gsp.psi.GosuDecls;
import org.jetbrains.annotations.Nullable;

/** Stub for a class-like declaration (name only; members resolve from PSI). */
public final class GosuClassStub extends StubBase<GosuDecls.ClassDecl>
    implements NamedStub<GosuDecls.ClassDecl> {
  private final String name;
  private final String enhancedType;
  private final java.util.List<String> superTypes;

  public GosuClassStub(@Nullable StubElement<?> parent, @Nullable String name) {
    this(parent, name, "");
  }

  public GosuClassStub(StubElement<?> parent, String name, String enhancedType) {
    this(parent, name, enhancedType, java.util.List.of());
  }

  public GosuClassStub(StubElement<?> parent, String name, String enhancedType, java.util.List<String> superTypes) {
    super(parent, GosuStubElementTypes.CLASS);
    this.superTypes = java.util.List.copyOf(superTypes);
    this.name = name;
    this.enhancedType = enhancedType;
  }

  public java.util.List<String> getSuperTypes() { return superTypes; }

  public String getEnhancedType() { return enhancedType; }

  @Override
  public @Nullable String getName() {
    return name;
  }
}
