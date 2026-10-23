package gosu.gsp.editor;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import gosu.gsp.lexer.GosuLexer;
import org.jetbrains.annotations.NotNull;

/** What counts as a renameable / creatable identifier in Gosu. */
public final class GosuNamesValidator implements NamesValidator {

  @Override
  public boolean isKeyword(@NotNull String name, Project project) {
    return GosuLexer.getKeywords().contains(name);
  }

  @Override
  public boolean isIdentifier(@NotNull String name, Project project) {
    if (name.isEmpty() || isKeyword(name, project)) {
      return false;
    }
    if (!Character.isJavaIdentifierStart(name.charAt(0))) {
      return false;
    }
    for (int i = 1; i < name.length(); i++) {
      if (!Character.isJavaIdentifierPart(name.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}
