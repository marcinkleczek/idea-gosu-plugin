package gosu.gsp.structure;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import gosu.gsp.psi.GosuDecls.FunctionDecl;
import gosu.gsp.psi.GosuDecls.Param;
import gosu.gsp.psi.GosuDecls.VarDecl;
import gosu.gsp.psi.GosuFile;
import gosu.gsp.psi.GosuPropertyAccessor;
import gosu.gsp.psi.GosuTypeRef;
import gosu.gsp.psi.GosuUsesDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/** File structure: classes, functions (with parameters), variables and imports. */
public final class GosuStructureViewFactory implements PsiStructureViewFactory {
  @Override
  public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
    return new TreeBasedStructureViewBuilder() {
      @Override
      public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new GosuStructureViewModel(psiFile, editor);
      }
    };
  }

  private static final class GosuStructureViewModel extends TextEditorBasedStructureViewModel {
    GosuStructureViewModel(@NotNull PsiFile file, @Nullable Editor editor) {
      super(editor, file);
      PsiManager.getInstance(file.getProject()).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
        private void changed(PsiTreeChangeEvent event) {
          if (event.getFile() == file) fireModelUpdate();
        }

        @Override public void childAdded(@NotNull PsiTreeChangeEvent event) { changed(event); }
        @Override public void childRemoved(@NotNull PsiTreeChangeEvent event) { changed(event); }
        @Override public void childReplaced(@NotNull PsiTreeChangeEvent event) { changed(event); }
        @Override public void childMoved(@NotNull PsiTreeChangeEvent event) { changed(event); }
        @Override public void childrenChanged(@NotNull PsiTreeChangeEvent event) { changed(event); }
        @Override public void propertyChanged(@NotNull PsiTreeChangeEvent event) { changed(event); }
      }, this);
    }

    @Override
    public @NotNull StructureViewTreeElement getRoot() {
      return new GosuStructureElement(getPsiFile());
    }

    @Override
    protected Class<?> @NotNull [] getSuitableClasses() {
      return new Class<?>[]{ClassDecl.class, FunctionDecl.class, VarDecl.class,
          Param.class, GosuUsesDecl.class, GosuFile.class};
    }

    @Override
    public Sorter @NotNull [] getSorters() {
      return new Sorter[]{Sorter.ALPHA_SORTER};
    }
  }

  private static final class GosuStructureElement
      implements StructureViewTreeElement, SortableTreeElement {
    private final PsiElement element;

    GosuStructureElement(@NotNull PsiElement element) {
      this.element = element;
    }

    @Override
    public Object getValue() {
      return element;
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
      String text = presentableText();
      String location = locationText();
      Icon icon = icon();
      return new ItemPresentation() {
        @Override
        public @Nullable String getPresentableText() {
          return text;
        }

        @Override
        public @Nullable String getLocationString() {
          return location;
        }

        @Override
        public @Nullable Icon getIcon(boolean unused) {
          return icon;
        }
      };
    }

    /** Declared type ({@code : String}, function return type) shown greyed out. */
    private @Nullable String locationText() {
      if (!(element instanceof FunctionDecl)
          && !(element instanceof VarDecl)
          && !(element instanceof Param)) {
        return null;
      }
      String last = null;
      for (PsiElement child : element.getChildren()) {
        if (child instanceof GosuTypeRef ref && ref.getName() != null) {
          last = ref.getName();
        }
      }
      return last == null ? null : ": " + last;
    }

    @Override
    public @NotNull String getAlphaSortKey() {
      return presentableText();
    }

    private @NotNull String presentableText() {
      GosuPropertyAccessor accessor = GosuPropertyAccessor.of(element);
      if (accessor != null && element instanceof VarDecl variable) {
        return variable.getName() + (accessor == GosuPropertyAccessor.GET ? " [get]" : " [set]");
      }
      if (element instanceof GosuUsesDecl uses) {
        return uses.getQualifiedName();
      }
      if (element instanceof FunctionDecl fn) {
        StringBuilder params = new StringBuilder();
        for (PsiElement child : fn.getChildren()) {
          if (child instanceof Param param && param.getName() != null) {
            if (!params.isEmpty()) {
              params.append(", ");
            }
            params.append(param.getName());
          }
        }
        return fn.getName() + "(" + params + ")";
      }
      if (element instanceof com.intellij.psi.PsiNameIdentifierOwner owner) {
        return String.valueOf(owner.getName());
      }
      if (element instanceof PsiFile file) {
        return file.getName();
      }
      return element.getText();
    }

    private @Nullable Icon icon() {
      GosuPropertyAccessor accessor = GosuPropertyAccessor.of(element);
      if (accessor == GosuPropertyAccessor.GET) return AllIcons.Nodes.PropertyRead;
      if (accessor == GosuPropertyAccessor.SET) return AllIcons.Nodes.PropertyWrite;
      if (element instanceof ClassDecl) {
        return AllIcons.Nodes.Class;
      }
      if (element instanceof FunctionDecl) {
        return AllIcons.Nodes.Method;
      }
      if (element instanceof Param) {
        return AllIcons.Nodes.Parameter;
      }
      if (element instanceof VarDecl var) {
        return var.isMember() ? AllIcons.Nodes.Field : AllIcons.Nodes.Variable;
      }
      if (element instanceof GosuUsesDecl) {
        return AllIcons.Nodes.Package;
      }
      return null;
    }

    @Override
    public StructureViewTreeElement @NotNull [] getChildren() {
      List<StructureViewTreeElement> out = new ArrayList<>();
      if (element.isValid()) collectInto(element, out);
      return out.toArray(StructureViewTreeElement.EMPTY_ARRAY);
    }

    /**
     * Direct declarations, looking through blocks but never into nested
     * classes or functions (those expand on their own node).
     */
    private static void collectInto(PsiElement owner, List<StructureViewTreeElement> out) {
      for (PsiElement child : owner.getChildren()) {
        if (child instanceof ClassDecl || child instanceof FunctionDecl
            || child instanceof VarDecl || child instanceof Param
            || child instanceof GosuUsesDecl) {
          if (nameOf(child) != null) {
            out.add(new GosuStructureElement(child));
          }
        }
        else if (!(child instanceof com.intellij.psi.PsiWhiteSpace)
            && !(child instanceof com.intellij.psi.PsiComment)
            && child.getFirstChild() != null) {
          collectInto(child, out);
        }
      }
    }

    private static @Nullable String nameOf(PsiElement e) {
      if (e instanceof GosuUsesDecl uses) {
        return uses.getQualifiedName();
      }
      if (e instanceof FunctionDecl fn) {
        return fn.getName(); // falls back to "construct"
      }
      if (e instanceof com.intellij.psi.PsiNameIdentifierOwner owner) {
        return owner.getName();
      }
      return null;
    }

    @Override
    public void navigate(boolean requestFocus) {
      if (canNavigate() && element.getContainingFile() != null
          && element.getContainingFile().getVirtualFile() != null) {
        new OpenFileDescriptor(element.getProject(),
            element.getContainingFile().getVirtualFile(),
            element.getTextOffset()).navigate(requestFocus);
      }
    }

    @Override
    public boolean canNavigate() {
      return element.isValid() && element.getContainingFile() != null
          && element.getContainingFile().getVirtualFile() != null;
    }

    @Override
    public boolean canNavigateToSource() {
      return canNavigate();
    }
  }
}
