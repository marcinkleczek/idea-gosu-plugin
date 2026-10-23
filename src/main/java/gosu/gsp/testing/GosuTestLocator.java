package gosu.gsp.testing;
import com.intellij.execution.*;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.GosuTypes;
import gosu.gsp.stubs.GosuStubIndexKeys;
import java.util.*;
public final class GosuTestLocator implements SMTestLocator {
  @Override public List<Location> getLocation(String protocol, String path, Project project, GlobalSearchScope scope) {
    if (!protocol.equals("gosutest") || DumbService.isDumb(project)) return List.of();
    String[] parts = path.split("/", 2);
    String name = parts[0].substring(parts[0].lastIndexOf('.') + 1);
    List<Location> locations = new ArrayList<>();
    for (ClassDecl cls : StubIndex.getElements(GosuStubIndexKeys.CLASSES, name, project, scope, ClassDecl.class)) {
      if (!parts[0].equals(GosuTypes.qualifiedName(cls))) continue;
      if (parts.length == 1) locations.add(new PsiLocation<>(cls));
      else for (FunctionDecl fn : PsiTreeUtil.findChildrenOfType(cls, FunctionDecl.class))
        if (parts[1].equals(fn.getName()) && PsiTreeUtil.getParentOfType(fn, ClassDecl.class) == cls) locations.add(new PsiLocation<>(fn));
    }
    return locations;
  }
}
