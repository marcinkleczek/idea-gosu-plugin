package gosu.gsp.hierarchy;

import com.intellij.ide.hierarchy.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.FunctionDecl;

public final class GosuCallHierarchyProvider implements HierarchyProvider {
  @Override public PsiElement getTarget(DataContext context) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);
    var file = CommonDataKeys.PSI_FILE.getData(context);
    var editor = CommonDataKeys.EDITOR.getData(context);
    if (editor != null && file instanceof GosuFile) {
      element = file.findElementAt(Math.min(editor.getCaretModel().getOffset(), Math.max(0, file.getTextLength() - 1)));
      var ref = PsiTreeUtil.getParentOfType(element, GosuRefExpr.class, false);
      if (ref != null) return GosuCalls.target(ref);
    }
    if (element instanceof FunctionDecl || element instanceof PsiMethod) return element;
    var fn = PsiTreeUtil.getParentOfType(element, FunctionDecl.class);
    return fn == null ? file : fn;
  }
  @Override public HierarchyBrowser createHierarchyBrowser(PsiElement target) { return new GosuCallHierarchyBrowser(target); }
  @Override public void browserActivated(HierarchyBrowser browser) { ((GosuCallHierarchyBrowser) browser).changeView(CallHierarchyBrowserBase.getCallerType()); }
}
