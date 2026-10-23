package gosu.gsp.structure;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import gosu.gsp.GosuLanguage;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import gosu.gsp.psi.GosuDecls.FunctionDecl;
import gosu.gsp.psi.GosuDecls.Param;
import gosu.gsp.psi.GosuNamedDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/** Editor breadcrumbs: file › class › function. Variables stay out (noise). */
public final class GosuBreadcrumbsProvider implements BreadcrumbsProvider {
  private static final Language[] LANGUAGES = {GosuLanguage.INSTANCE};

  @Override
  public Language @NotNull [] getLanguages() {
    return LANGUAGES;
  }

  @Override
  public boolean acceptElement(@NotNull PsiElement e) {
    return e instanceof ClassDecl || e instanceof FunctionDecl;
  }

  @Override
  public @NotNull String getElementInfo(@NotNull PsiElement e) {
    if (e instanceof FunctionDecl fn) {
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
    if (e instanceof GosuNamedDecl decl) {
      return String.valueOf(decl.getName());
    }
    return "";
  }

  @Override
  public @Nullable Icon getElementIcon(@NotNull PsiElement e) {
    if (e instanceof ClassDecl) {
      return AllIcons.Nodes.Class;
    }
    if (e instanceof FunctionDecl) {
      return AllIcons.Nodes.Method;
    }
    return null;
  }

  @Override
  public @Nullable String getElementTooltip(@NotNull PsiElement e) {
    return null;
  }

  @Override
  public @Nullable PsiElement getParent(@NotNull PsiElement e) {
    PsiElement parent = e.getParent();
    while (parent != null && !(parent instanceof ClassDecl) && !(parent instanceof FunctionDecl)
        && !(parent instanceof com.intellij.psi.PsiFile)) {
      parent = parent.getParent();
    }
    return parent;
  }

  @Override
  public @NotNull List<PsiElement> getChildren(@NotNull PsiElement e) {
    List<PsiElement> out = new ArrayList<>();
    for (PsiElement child : e.getChildren()) {
      if (child instanceof ClassDecl || child instanceof FunctionDecl) {
        out.add(child);
      }
      else if (!(child instanceof com.intellij.psi.PsiWhiteSpace) && child.getFirstChild() != null) {
        out.addAll(getChildren(child));
      }
    }
    return out;
  }

  @Override
  public @NotNull List<? extends javax.swing.Action> getContextActions(@NotNull PsiElement e) {
    return List.of();
  }

  @Override
  public boolean isShownByDefault() {
    return true;
  }

  @Override
  public boolean acceptStickyElement(@NotNull PsiElement e) {
    return acceptElement(e);
  }
}
