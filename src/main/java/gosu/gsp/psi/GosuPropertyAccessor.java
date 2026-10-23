package gosu.gsp.psi;

import com.intellij.psi.PsiElement;
import gosu.gsp.resolve.GosuSource;

/** Property accessors are callable declarations syntactically, but accessed as properties. */
public enum GosuPropertyAccessor {
  GET, SET;

  public static GosuPropertyAccessor of(PsiElement element) {
    if (!(element instanceof GosuDecls.VarDecl)) return null;
    var tokens = GosuSource.tokenize(element.getText());
    if (tokens.size() < 2 || !tokens.get(0).text().equals("property")) return null;
    return switch (tokens.get(1).text()) { case "get" -> GET; case "set" -> SET; default -> null; };
  }
}
