package gosu.gsp.hierarchy;

import com.intellij.ide.hierarchy.*;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.psi.*;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import javax.swing.*;
import java.util.*;

/** Native Hierarchy tool window, with lazy expansion and navigable PSI descriptors. */
public final class GosuCallHierarchyBrowser extends CallHierarchyBrowserBase {
  public GosuCallHierarchyBrowser(PsiElement target) { super(target.getProject(), target); }
  @Override public JComponent getComponent() { return this; }
  @Override protected PsiElement getElementFromDescriptor(HierarchyNodeDescriptor descriptor) { return descriptor.getPsiElement(); }
  @Override protected void createTrees(Map<? super String, ? super JTree> trees) {
    trees.put(getCallerType(), createTree(false));
    trees.put(getCalleeType(), createTree(false));
  }
  @Override protected JPanel createLegendPanel() { return null; }
  @Override protected boolean isApplicableElement(PsiElement e) { return e instanceof gosu.gsp.psi.GosuDecls.FunctionDecl || e instanceof PsiMethod || e instanceof gosu.gsp.psi.GosuFile; }
  @Override protected Comparator<NodeDescriptor<?>> getComparator() { return Comparator.comparing(Object::toString); }
  @Override protected HierarchyTreeStructure createHierarchyTreeStructure(String type, PsiElement element) {
    return structure(element, type.equals(getCallerType()));
  }
  public static HierarchyTreeStructure structure(PsiElement element, boolean upwards) {
    return new HierarchyTreeStructure(element.getProject(), new Descriptor(null, element, true)) {
      @Override protected Object[] buildChildren(HierarchyNodeDescriptor descriptor) {
        PsiElement current = descriptor.getPsiElement();
        if (current == null || !current.isValid()) return new Object[0];
        List<Object> result = new ArrayList<>();
        for (PsiElement next : GosuCalls.related(current, upwards)) {
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
      myName = GosuCalls.name(element);
      myHighlightedText = new com.intellij.openapi.roots.ui.util.CompositeAppearance();
      myHighlightedText.getEnding().addText(myName);
      installIcon(element, myIsBase);
      return true;
    }
  }
}
