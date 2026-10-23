package gosu.gsp.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.GosuLanguage;
import gosu.gsp.lexer.GosuTokenTypes;

/** Creates throwaway identifier leaves for rename support. */
final class GosuElementFactory {
  private GosuElementFactory() {
  }

  static PsiElement createIdentifier(Project project, String name) {
    GosuFile file = (GosuFile) PsiFileFactory.getInstance(project)
        .createFileFromText("dummy.gsp", GosuLanguage.INSTANCE, name);
    GosuRefExpr ref = PsiTreeUtil.findChildOfType(file, GosuRefExpr.class, false);
    com.intellij.lang.ASTNode id =
        ref == null ? null : ref.getNode().findChildByType(GosuTokenTypes.IDENTIFIER);
    if (id == null || id.getPsi() == null) {
      throw new IllegalArgumentException("Not an identifier: " + name);
    }
    return id.getPsi();
  }
}
