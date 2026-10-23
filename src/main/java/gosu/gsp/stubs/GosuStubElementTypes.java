package gosu.gsp.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import gosu.gsp.GosuLanguage;
import gosu.gsp.psi.GosuDecls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Registry of stub element types (see {@code stubElementTypeHolder}).
 * Must be an interface: on 2026.2 {@code StubElementTypeHolderEP} asserts
 * {@code holderClass.isInterface()} and drops the whole registration
 * (SEVERE in idea.log, stub index dead) for a final class.
 */
public interface GosuStubElementTypes {
  GosuClassElementType CLASS = new GosuClassElementType();
  GosuFunctionElementType FUNCTION = new GosuFunctionElementType();

  public static final class GosuClassElementType
      extends IStubElementType<GosuClassStub, GosuDecls.ClassDecl> {
    GosuClassElementType() {
      super("GOSU_CLASS_DECL", GosuLanguage.INSTANCE);
    }

    @Override
    public @NotNull GosuDecls.ClassDecl createPsi(@NotNull GosuClassStub stub) {
      return new GosuDecls.ClassDecl(stub, this);
    }

    @Override
    public @NotNull GosuClassStub createStub(@NotNull GosuDecls.ClassDecl psi,
        StubElement<?> parentStub) {
      return new GosuClassStub(parentStub, psi.getName(), gosu.gsp.resolve.GosuEnhancements.targetText(psi), gosu.gsp.hierarchy.GosuHierarchy.parentTexts(psi.getText()));
    }

    @Override
    public @NotNull String getExternalId() {
      return "gosu.class";
    }

    @Override
    public void serialize(@NotNull GosuClassStub stub, @NotNull StubOutputStream out)
        throws IOException {
      out.writeName(stub.getName() == null ? "" : stub.getName());
      out.writeName(stub.getEnhancedType());
      out.writeVarInt(stub.getSuperTypes().size());
      for (String parent : stub.getSuperTypes()) out.writeName(parent);
    }

    @Override
    public @NotNull GosuClassStub deserialize(@NotNull StubInputStream in,
        StubElement parentStub) throws IOException {
      String name = in.readNameString();
      String enhanced = in.readNameString();
      int count = in.readVarInt();
      java.util.List<String> parents = new java.util.ArrayList<>();
      for (int i = 0; i < count; i++) parents.add(in.readNameString());
      return new GosuClassStub(parentStub, name.isEmpty() ? null : name, enhanced, parents);
    }

    @Override
    public void indexStub(@NotNull GosuClassStub stub, @NotNull IndexSink sink) {
      if (stub.getName() != null && !stub.getName().isEmpty()) {
        sink.occurrence(GosuStubIndexKeys.CLASSES, stub.getName());
        for (String parent : stub.getSuperTypes()) {
          String raw = gosu.gsp.resolve.GosuEnhancements.rawName(parent);
          sink.occurrence(GosuStubIndexKeys.SUPERTYPES, raw.substring(raw.lastIndexOf('.') + 1));
        }
        String target = gosu.gsp.resolve.GosuEnhancements.rawName(stub.getEnhancedType());
        if (!target.isEmpty()) sink.occurrence(GosuStubIndexKeys.ENHANCEMENTS, target.substring(target.lastIndexOf('.') + 1));
      }
    }
  }

  public static final class GosuFunctionElementType
      extends IStubElementType<GosuFunctionStub, GosuDecls.FunctionDecl> {
    GosuFunctionElementType() {
      super("GOSU_FUNCTION_DECL", GosuLanguage.INSTANCE);
    }

    @Override
    public @NotNull GosuDecls.FunctionDecl createPsi(@NotNull GosuFunctionStub stub) {
      return new GosuDecls.FunctionDecl(stub, this);
    }

    @Override
    public @NotNull GosuFunctionStub createStub(@NotNull GosuDecls.FunctionDecl psi,
        StubElement<?> parentStub) {
      return new GosuFunctionStub(parentStub, psi.getName());
    }

    @Override
    public @NotNull String getExternalId() {
      return "gosu.function";
    }

    @Override
    public void serialize(@NotNull GosuFunctionStub stub, @NotNull StubOutputStream out)
        throws IOException {
      out.writeName(stub.getName() == null ? "" : stub.getName());
    }

    @Override
    public @NotNull GosuFunctionStub deserialize(@NotNull StubInputStream in,
        StubElement parentStub) throws IOException {
      String name = in.readNameString();
      return new GosuFunctionStub(parentStub, name.isEmpty() ? null : name);
    }

    @Override
    public void indexStub(@NotNull GosuFunctionStub stub, @NotNull IndexSink sink) {
      if (stub.getName() != null && !stub.getName().isEmpty()) {
        sink.occurrence(GosuStubIndexKeys.FUNCTIONS, stub.getName());
      }
    }
  }
}
