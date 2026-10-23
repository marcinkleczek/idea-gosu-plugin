package gosu.gsp.hierarchy;

import com.intellij.ide.hierarchy.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuDecls.ClassDecl;

public final class GosuHierarchyProvider implements HierarchyProvider {
  @Override public PsiElement getTarget(DataContext context) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);
    PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
    var editor = CommonDataKeys.EDITOR.getData(context);
    if (editor != null && file != null) {
      int offset = editor.getCaretModel().getOffset();
      var ref = file.findReferenceAt(offset);
      if (ref != null && ref.resolve() != null) element = ref.resolve();
      else element = file.findElementAt(Math.min(offset, Math.max(0, file.getTextLength() - 1)));
    }
    if (element instanceof ClassDecl || element instanceof PsiClass) return element;
    return PsiTreeUtil.getParentOfType(element, ClassDecl.class);
  }
  @Override public HierarchyBrowser createHierarchyBrowser(PsiElement target) { return new GosuTypeHierarchyBrowser(target); }
  @Override public void browserActivated(HierarchyBrowser browser) {
    ((GosuTypeHierarchyBrowser)browser).changeView(TypeHierarchyBrowserBase.getSubtypesHierarchyType());
  }
}
