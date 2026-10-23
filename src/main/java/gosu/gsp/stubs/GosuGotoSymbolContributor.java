package gosu.gsp.stubs;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import gosu.gsp.psi.GosuDecls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Goto Symbol (Ctrl+Alt+Shift+N) for classes and functions across the project. */
public final class GosuGotoSymbolContributor implements ChooseByNameContributor {
  @Override
  public String @NotNull [] getNames(@NotNull Project project, boolean includeNonProject) {
    Set<String> names = new LinkedHashSet<>();
    names.addAll(StubIndex.getInstance().getAllKeys(GosuStubIndexKeys.CLASSES, project));
    names.addAll(StubIndex.getInstance().getAllKeys(GosuStubIndexKeys.FUNCTIONS, project));
    return names.toArray(String[]::new);
  }

  @Override
  public NavigationItem @NotNull [] getItemsByName(@NotNull String name, @NotNull String pattern,
      @NotNull Project project, boolean includeNonProject) {
    GlobalSearchScope scope = includeNonProject
        ? GlobalSearchScope.allScope(project)
        : GlobalSearchScope.projectScope(project);
    List<NavigationItem> out = new ArrayList<>();
    Collection<GosuDecls.ClassDecl> classes = StubIndex.getInstance()
        .getElements(GosuStubIndexKeys.CLASSES, name, project, scope, GosuDecls.ClassDecl.class);
    for (GosuDecls.ClassDecl cls : classes) {
      out.add(new Target(cls, AllIcons.Nodes.Class));
    }
    Collection<GosuDecls.FunctionDecl> functions = StubIndex.getInstance()
        .getElements(GosuStubIndexKeys.FUNCTIONS, name, project, scope, GosuDecls.FunctionDecl.class);
    for (GosuDecls.FunctionDecl fn : functions) {
      out.add(new Target(fn, AllIcons.Nodes.Method));
    }
    return out.toArray(NavigationItem[]::new);
  }

  /** NavigationItem wrapper: our PSI does not implement it directly. */
  private record Target(PsiElement element, Icon icon) implements NavigationItem {
    @Override
    public @Nullable String getName() {
      if (element instanceof GosuDecls.ClassDecl cls) {
        return cls.getName();
      }
      if (element instanceof GosuDecls.FunctionDecl fn) {
        return fn.getName();
      }
      return null;
    }

    @Override
    public @Nullable ItemPresentation getPresentation() {
      String text = getName();
      String file = element.getContainingFile() == null
          ? "" : element.getContainingFile().getName();
      return new ItemPresentation() {
        @Override
        public @Nullable String getPresentableText() {
          return text;
        }

        @Override
        public @Nullable String getLocationString() {
          return file;
        }

        @Override
        public @Nullable Icon getIcon(boolean unused) {
          return icon;
        }
      };
    }

    @Override
    public void navigate(boolean requestFocus) {
      if (element.isValid() && element.getContainingFile() != null
          && element.getContainingFile().getVirtualFile() != null) {
        new OpenFileDescriptor(element.getProject(),
            element.getContainingFile().getVirtualFile(),
            element.getTextOffset()).navigate(requestFocus);
      }
    }

    @Override
    public boolean canNavigate() {
      return element.isValid();
    }

    @Override
    public boolean canNavigateToSource() {
      return element.isValid();
    }
  }
}
