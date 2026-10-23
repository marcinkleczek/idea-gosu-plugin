package gosu.gsp.hierarchy;

import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import java.util.*;
import org.jetbrains.annotations.NotNull;

public final class GosuInheritanceMarkers extends RelatedItemLineMarkerProvider {
  @Override public String getName() { return "Gosu inheritance"; }
  @Override protected void collectNavigationMarkers(@NotNull PsiElement leaf, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    if (DumbService.isDumb(leaf.getProject()) || !(leaf.getParent() instanceof PsiNameIdentifierOwner declaration)
        || declaration.getNameIdentifier() != leaf) return;
    if (!(declaration instanceof ClassDecl || declaration instanceof FunctionDecl || declaration instanceof VarDecl)) return;
    var supers = declaration instanceof ClassDecl ? GosuHierarchy.parents(declaration) : GosuHierarchy.superMembers(declaration);
    if (!supers.isEmpty()) result.add(NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridingMethod).setTargets(supers)
        .setTooltipText("Go to super declaration").setPopupTitle("Super declarations").createLineMarkerInfo(leaf));
    var implementations = GosuHierarchy.implementations(declaration, true);
    if (!implementations.isEmpty()) result.add(NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridenMethod).setTargets(implementations)
        .setTooltipText("Go to implementations / subclasses").setPopupTitle("Implementations / subclasses").createLineMarkerInfo(leaf));
  }
}
