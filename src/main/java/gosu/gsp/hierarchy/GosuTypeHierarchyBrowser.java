package gosu.gsp.hierarchy;

import com.intellij.ide.hierarchy.*;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.psi.*;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import javax.swing.*;
import java.util.*;

/** Native Hierarchy tool window, with lazy expansion and navigable PSI descriptors. */
public final class GosuTypeHierarchyBrowser extends TypeHierarchyBrowserBase {
  public GosuTypeHierarchyBrowser(PsiElement target) { super(target.getProject(), target); }
  @Override public JComponent getComponent() { return this; }
  @Override protected boolean isInterface(PsiElement e) { return e instanceof PsiClass c ? c.isInterface() : e.getText().startsWith("interface"); }
  @Override protected boolean canBeDeleted(PsiElement e) { return false; }
  @Override protected String getQualifiedName(PsiElement e) { return GosuHierarchy.name(e); }
  @Override protected PsiElement getElementFromDescriptor(HierarchyNodeDescriptor descriptor) { return descriptor.getPsiElement(); }
  @Override protected void createTrees(Map<? super String, ? super JTree> trees) {
    createTreeAndSetupCommonActions(trees, getTypeHierarchyType());
    createTreeAndSetupCommonActions(trees, getSubtypesHierarchyType());
    createTreeAndSetupCommonActions(trees, getSupertypesHierarchyType());
  }
  @Override protected JPanel createLegendPanel() { return null; }
  @Override protected boolean isApplicableElement(PsiElement e) { return e instanceof ClassDecl || e instanceof PsiClass; }
  @Override protected Comparator<NodeDescriptor<?>> getComparator() { return Comparator.comparing(Object::toString); }
  @Override protected HierarchyTreeStructure createHierarchyTreeStructure(String type, PsiElement element) {
    return structure(element, type.equals(getSupertypesHierarchyType()));
  }
  public static HierarchyTreeStructure structure(PsiElement element, boolean upwards) {
    return new HierarchyTreeStructure(element.getProject(), new Descriptor(null, element, true)) {
      @Override protected Object[] buildChildren(HierarchyNodeDescriptor descriptor) {
        PsiElement current = descriptor.getPsiElement();
        if (current == null || !current.isValid()) return new Object[0];
        List<Object> result = new ArrayList<>();
        for (PsiElement next : upwards ? GosuHierarchy.parents(current) : GosuHierarchy.children(current)) {
          boolean cycle = false;
          for (NodeDescriptor<?> ancestor = descriptor; ancestor != null; ancestor = ancestor.getParentDescriptor()) {
            if (ancestor instanceof HierarchyNodeDescriptor h && h.getPsiElement() != null && h.getPsiElement().isEquivalentTo(next)) { cycle = true; break; }
          }
          if (!cycle) result.add(new Descriptor(descriptor, next, false));
        }
        return result.toArray();
      }
    };
  }
  private static final class Descriptor extends HierarchyNodeDescriptor {
    Descriptor(NodeDescriptor<?> parent, PsiElement element, boolean base) { super(element.getProject(), parent, element, base); update(); }
    @Override public boolean update() {
      PsiElement element = getPsiElement();
      if (element == null) return false;
      myName = GosuHierarchy.name(element);
      myHighlightedText = new com.intellij.openapi.roots.ui.util.CompositeAppearance();
      myHighlightedText.getEnding().addText(myName);
      installIcon(element, myIsBase);
      return true;
    }
  }
}
