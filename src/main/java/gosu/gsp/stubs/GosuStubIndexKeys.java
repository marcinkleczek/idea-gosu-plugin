package gosu.gsp.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import gosu.gsp.psi.GosuDecls;
import org.jetbrains.annotations.NotNull;

/** Project-wide index of class/function names for Goto Symbol. */
public final class GosuStubIndexKeys {
  private GosuStubIndexKeys() {
  }

  public static final StubIndexKey<String, GosuDecls.ClassDecl> CLASSES =
      StubIndexKey.createIndexKey("gosu.classes");

  public static final StubIndexKey<String, GosuDecls.ClassDecl> SUPERTYPES = StubIndexKey.createIndexKey("gosu.supertypes");
  public static final class Supertypes extends StringStubIndexExtension<GosuDecls.ClassDecl> {
    @Override public @NotNull StubIndexKey<String, GosuDecls.ClassDecl> getKey() { return SUPERTYPES; }
    @Override public int getVersion() { return GosuStubVersion.VERSION; }
  }

  public static final StubIndexKey<String, GosuDecls.ClassDecl> ENHANCEMENTS = StubIndexKey.createIndexKey("gosu.enhancements");

  public static final class Enhancements extends StringStubIndexExtension<GosuDecls.ClassDecl> {
    @Override public @NotNull StubIndexKey<String, GosuDecls.ClassDecl> getKey() { return ENHANCEMENTS; }
    @Override public int getVersion() { return GosuStubVersion.VERSION; }
  }

  public static final StubIndexKey<String, GosuDecls.FunctionDecl> FUNCTIONS =
      StubIndexKey.createIndexKey("gosu.functions");

  public static final class Classes extends StringStubIndexExtension<GosuDecls.ClassDecl> {
    @Override
    public @NotNull StubIndexKey<String, GosuDecls.ClassDecl> getKey() {
      return CLASSES;
    }

    @Override
    public int getVersion() {
      return GosuStubVersion.VERSION;
    }
  }

  public static final class Functions extends StringStubIndexExtension<GosuDecls.FunctionDecl> {
    @Override
    public @NotNull StubIndexKey<String, GosuDecls.FunctionDecl> getKey() {
      return FUNCTIONS;
    }

    @Override
    public int getVersion() {
      return GosuStubVersion.VERSION;
    }
  }
}
