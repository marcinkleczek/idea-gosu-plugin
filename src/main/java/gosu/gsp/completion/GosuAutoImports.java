package gosu.gsp.completion;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.stubs.StubIndex;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import gosu.gsp.resolve.*;
import gosu.gsp.stubs.GosuStubIndexKeys;
import java.util.*;
import java.util.function.Predicate;

/** Candidate discovery and a shared, conflict-aware import edit for completion and Alt+Enter. */
public final class GosuAutoImports {
  private GosuAutoImports() {}
  public record Candidate(String name, String qualifiedName, PsiElement target) {}

  public static List<Candidate> candidates(PsiElement context, Predicate<String> matches) {
    if (DumbService.isDumb(context.getProject())) return List.of();
    var scope = GlobalSearchScope.allScope(context.getProject());
    Map<String, Candidate> result = new TreeMap<>();
    for (String name : StubIndex.getInstance().getAllKeys(GosuStubIndexKeys.CLASSES, context.getProject())) {
      ProgressManager.checkCanceled();
      if (!matches.test(name)) continue;
      for (ClassDecl cls : StubIndex.getElements(GosuStubIndexKeys.CLASSES, name, context.getProject(), scope, ClassDecl.class)) {
        if (!GosuEnhancements.isEnhancement(cls)) result.putIfAbsent(GosuTypes.qualifiedName(cls), new Candidate(name, GosuTypes.qualifiedName(cls), cls));
      }
      if (result.size() >= 200) break;
    }
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(context.getProject());
    for (String name : cache.getAllClassNames()) {
      ProgressManager.checkCanceled();
      if (!matches.test(name)) continue;
      for (PsiClass cls : cache.getClassesByName(name, scope)) {
        String qualified = cls.getQualifiedName();
        if (qualified != null && cls.hasModifierProperty(PsiModifier.PUBLIC)) result.putIfAbsent(qualified, new Candidate(name, qualified, cls));
      }
      if (result.size() >= 200) break;
    }
    return new ArrayList<>(result.values());
  }

  public static void insert(PsiFile file, int referenceStart, int referenceEnd, String qualified) {
    PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
    Document doc = manager.getDocument(file);
    if (doc == null) return;
    String simple = qualified.substring(qualified.lastIndexOf('.') + 1);
    List<String> imports = GosuSource.imports(file);
    PsiElement existing = GosuTypes.find(simple, file);
    String existingName = existing instanceof ClassDecl c ? GosuTypes.qualifiedName(c)
        : existing instanceof PsiClass c ? c.getQualifiedName() : null;
    boolean conflict = imports.stream().anyMatch(i -> i.endsWith("." + simple) && !i.equals(qualified))
        || (existingName != null && !existingName.equals(qualified));
    if (conflict) {
      doc.replaceString(referenceStart, referenceEnd, qualified);
      manager.commitDocument(doc);
      return;
    }
    String pkg = qualified.contains(".") ? qualified.substring(0, qualified.lastIndexOf('.')) : "";
    if (imports.contains(qualified) || qualified.equals(existingName) || pkg.equals(GosuSource.packageName(file)) || pkg.equals("java.lang")) return;
    int insert = 0;
    var tokens = GosuSource.tokens(file);
    for (var token : tokens) {
      if (token.text().equals("package") || token.text().equals("uses")) {
        int line = doc.getLineNumber(token.start());
        insert = doc.getLineEndOffset(line);
        if (insert < doc.getTextLength()) insert++;
      } else if (insert == 0) {
        // Keep leading file comments above newly inserted imports.
        insert = token.start();
        break;
      }
    }
    String prefix = insert > 0 && doc.getCharsSequence().charAt(insert - 1) != '\n' ? "\n" : "";
    doc.insertString(insert, prefix + "uses " + qualified + "\n");
    manager.commitDocument(doc);
  }

  public static boolean canImportEnhancement(PsiFile file, String qualified) {
    String simple = qualified.substring(qualified.lastIndexOf('.') + 1);
    if (GosuSource.imports(file).stream().anyMatch(i -> i.endsWith("." + simple) && !i.equals(qualified))) return false;
    PsiElement existing = GosuTypes.find(simple, file);
    return existing == null || existing instanceof ClassDecl cls && qualified.equals(GosuTypes.qualifiedName(cls));
  }

  public static boolean addEnhancementImport(PsiFile file, String qualified) {
    if (!canImportEnhancement(file, qualified)) return false;
    insert(file, 0, 0, qualified);
    return true;
  }

  public static boolean preserveImport(gosu.gsp.psi.GosuUsesDecl uses) {
    String name = uses.getQualifiedName().trim();
    if (name.endsWith(".*") || DumbService.isDumb(uses.getProject())) return true;
    PsiElement target = GosuTypes.find(name, uses);
    // Unknown imports can be type loaders or enhancements unavailable to the PSI index.
    return target == null || GosuEnhancements.isEnhancement(target);
  }
}
