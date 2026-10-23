package gosu.gsp.refactor;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import gosu.gsp.hierarchy.GosuCalls;
import gosu.gsp.editor.GosuNamesValidator;
import java.util.*;

/** Project-source moves. Refuses access or resolution changes it cannot prove safe. */
public final class GosuMove {
  private GosuMove() {}
  public record ClassPlan(GosuEditPlan edits, PsiFile file, PsiDirectory root, String packageName) {
    public void apply() {
      edits.validate();
      var files = new ArrayList<PsiElement>(edits.results().keySet()); files.add(root);
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(files)) return;
      WriteCommandAction.runWriteCommandAction(file.getProject(), "Move Class (Gosu)", null, () -> {
        edits.validate();
        PsiDirectory directory = root;
        for (String part : packageName.split("\\.")) {
          var child = directory.findSubdirectory(part);
          directory = child == null ? directory.createSubdirectory(part) : child;
        }
        if (directory.findFile(file.getName()) != null) throw new IllegalArgumentException("The target file already exists.");
        // All conflict and writability checks precede the first source edit.
        directory.checkAdd(file);
        edits.applyInWriteAction();
        com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil.doMoveFile(file, directory);
      }, edits.results().keySet().toArray(PsiFile[]::new));
    }
  }
  public static ClassPlan classPlan(ClassDecl cls, String destinationPackage) {
    var file = cls.getContainingFile();
    String oldPackage = GosuSource.packageName(file);
    if (!file.getName().equals(cls.getName() + ".gs") || PsiTreeUtil.getParentOfType(cls, ClassDecl.class) != null
        || PsiTreeUtil.getChildrenOfTypeAsList(file, ClassDecl.class).size() != 1)
      throw new IllegalArgumentException("Move Class supports a single top-level class in a file named Name.gs.");
    if (destinationPackage.equals(oldPackage) || destinationPackage.isBlank()) throw new IllegalArgumentException("Enter a different, non-empty destination package.");
    for (String part : destinationPackage.split("\\.", -1)) if (!new GosuNamesValidator().isIdentifier(part, cls.getProject())) throw new IllegalArgumentException("Invalid package name.");
    if (GosuTypes.find(destinationPackage + "." + cls.getName(), cls) != null) throw new IllegalArgumentException("The destination type already exists.");
    PsiDirectory root = file.getContainingDirectory();
    if (root == null) throw new IllegalArgumentException("The file must be inside the project.");
    if (!oldPackage.isBlank()) {
      var parts = oldPackage.split("\\.");
      for (int i = parts.length - 1; i >= 0; i--) {
        if (root == null || !root.getName().equals(parts[i])) throw new IllegalArgumentException("The file path does not match its package.");
        root = root.getParentDirectory();
      }
    }
    if (root == null || !root.isWritable()) throw new IllegalArgumentException("The source directory is not writable.");
    PsiDirectory existing = root;
    for (String part : destinationPackage.split("\\.")) { existing = existing == null ? null : existing.findSubdirectory(part); }
    if (existing != null && (!existing.isWritable() || existing.findFile(file.getName()) != null)) throw new IllegalArgumentException("The destination directory is not writable or already contains a file with this name.");
    for (var declaration : PsiTreeUtil.collectElements(cls, e -> e instanceof FunctionDecl || e instanceof VarDecl || e instanceof ClassDecl))
      if (GosuMembers.modifiers(declaration).contains("internal")) throw new IllegalArgumentException("Changing the package of a class with internal members requires manual review of accessibility.");
    var plan = new GosuEditPlan();
    String oldName = GosuTypes.qualifiedName(cls), newName = destinationPackage + "." + cls.getName();
    for (var source : GosuCalls.files(cls)) {
      replaceQualified(plan, source, oldName, newName, cls);
      Set<String> imports = new LinkedHashSet<>();
      for (var element : references(source)) {
        var ref = element.getReference();
        PsiElement target = ref == null ? null : ref.resolve();
        if (target == null) continue;
        if (source == file && !PsiTreeUtil.isAncestor(cls, target, false) && !Collections.disjoint(GosuMembers.modifiers(target), Set.of("internal", "protected")))
          throw new IllegalArgumentException("The class uses an element with restricted accessibility: " + element.getText());
        if (source != file && target.isEquivalentTo(cls) && !plan.covers(source, element.getTextOffset())) imports.add(newName);
        if (source == file && target instanceof ClassDecl dependency && dependency != cls && !PsiTreeUtil.isAncestor(cls, dependency, false)
            && GosuSource.packageName(dependency.getContainingFile()).equals(oldPackage)) imports.add(GosuTypes.qualifiedName(dependency));
      }
      if (source == file) {
        var ts = GosuSource.tokens(file);
        if (!ts.isEmpty() && ts.getFirst().text().equals("package")) {
          int last = 0;
          while (last + 1 < ts.size() && (ts.get(last + 1).kind() == gosu.gsp.lexer.GosuTokenTypes.IDENTIFIER || ts.get(last + 1).text().equals("."))) last++;
          plan.add(file, ts.getFirst().start(), ts.get(last).end(), "package " + destinationPackage);
        } else plan.add(file, 0, 0, "package " + destinationPackage + "\n");
      }
      if (GosuSource.imports(source).contains(oldName)) imports.remove(newName);
      if (source == file && oldPackage.isBlank() && !imports.isEmpty()) throw new IllegalArgumentException("The class depends on other types from the default package.");
      addImports(plan, source, imports);
    }
    plan.validate(); return new ClassPlan(plan, file, root, destinationPackage);
  }
  private static PsiElement[] references(PsiElement root) {
    return PsiTreeUtil.collectElements(root, e -> e instanceof GosuRefExpr || e instanceof GosuTypeRef);
  }
  private static void addImports(GosuEditPlan plan, PsiFile file, Set<String> imports) {
    var current = GosuSource.imports(file);
    imports.removeAll(current);
    if (imports.isEmpty()) return;
    for (String wanted : imports) for (String present : current)
      if (!present.equals(wanted) && present.substring(present.lastIndexOf('.') + 1).equals(wanted.substring(wanted.lastIndexOf('.') + 1)))
        throw new IllegalArgumentException("Import conflict in " + file.getName() + ": " + wanted);
    int insertion = 0;
    var ts = GosuSource.tokens(file);
    if (!ts.isEmpty() && ts.getFirst().text().equals("package")) {
      insertion = file.getText().indexOf('\n', ts.getFirst().end());
      insertion = insertion < 0 ? file.getTextLength() : insertion + 1;
    }
    plan.add(file, insertion, insertion, String.join("", imports.stream().map(i -> "uses " + i + "\n").toList()));
  }
  private static void replaceQualified(GosuEditPlan plan, PsiFile file, String oldName, String newName, ClassDecl moved) {
    var wanted = GosuSource.tokenize(oldName);
    var ts = GosuSource.tokens(file);
    for (int i = 0; i + wanted.size() <= ts.size(); i++) {
      boolean match = true;
      for (int j = 0; j < wanted.size(); j++) if (!ts.get(i + j).text().equals(wanted.get(j).text())) { match = false; break; }
      if (match && (i == 0 || !ts.get(i - 1).text().equals("."))) {
        // Single names are updated via resolved imports, never by lexical replacement.
        var reference = file.findReferenceAt(ts.get(i + wanted.size() - 1).start());
        var resolved = reference == null ? null : reference.resolve();
        boolean explicitImport = i > 0 && ts.get(i - 1).text().equals("uses");
        if (wanted.size() > 1 && (explicitImport || resolved != null && resolved.isEquivalentTo(moved)))
          plan.add(file, ts.get(i).start(), ts.get(i + wanted.size() - 1).end(), newName);
        i += wanted.size() - 1;
      }
    }
  }
  public static GosuEditPlan methodPlan(FunctionDecl fn, ClassDecl destination) {
    ClassDecl owner = PsiTreeUtil.getParentOfType(fn, ClassDecl.class);
    if (owner == null || owner == destination || !GosuMembers.modifiers(fn).contains("static") || !fn.getText().startsWith("function"))
      throw new IllegalArgumentException("Move Method supports static methods moved to another Gosu class.");
    if (!Collections.disjoint(GosuMembers.modifiers(fn), Set.of("private", "protected", "internal"))) throw new IllegalArgumentException("The method must be public; changing its accessibility is a separate decision.");
    if (!GosuSource.packageName(owner.getContainingFile()).equals(GosuSource.packageName(destination.getContainingFile())))
      throw new IllegalArgumentException("Move the method to a class in the same package; change the package with Move Class.");
    if (!GosuSource.imports(destination.getContainingFile()).containsAll(GosuSource.imports(owner.getContainingFile())))
      throw new IllegalArgumentException("The destination class must already have the imports required by the source file.");
    if (!destination.getText().startsWith("class ")) throw new IllegalArgumentException("The destination must be an ordinary Gosu class.");
    for (FunctionDecl other : PsiTreeUtil.findChildrenOfType(destination, FunctionDecl.class)) if (Objects.equals(other.getName(), fn.getName())) throw new IllegalArgumentException("A method with this name already exists in the destination class.");
    if (GosuSource.tokenize(fn.getText()).stream().anyMatch(t -> Set.of("this", "super").contains(t.text())) || fn.getText().contains("${") || fn.getText().contains("<%"))
      throw new IllegalArgumentException("The method depends on class context or interpolation and must be moved manually.");
    for (var element : references(fn)) {
      var ref = element.getReference();
      var target = ref == null ? null : ref.resolve();
      if (target == null && element instanceof GosuRefExpr && !element.getText().equals("print")) throw new IllegalArgumentException("Unresolved reference in the method: " + element.getText());
      if (target != null && !PsiTreeUtil.isAncestor(fn, target, false) && PsiTreeUtil.isAncestor(owner, target, false) && target != fn)
        throw new IllegalArgumentException("The method uses other members of the source class: " + element.getText());
    }
    var plan = new GosuEditPlan();
    var bodyEdits = new GosuEditPlan();
    String targetName = GosuTypes.qualifiedName(destination) + "." + fn.getName();
    int from = fn.getTextOffset();
    var all = GosuSource.tokens(fn.getContainingFile());
    int previous = GosuSource.before(all, from);
    while (previous >= 0 && Set.of("public", "private", "protected", "internal", "static", "final").contains(all.get(previous).text())) from = all.get(previous--).start();
    if (previous >= 0 && (all.get(previous).text().startsWith("@") || all.get(previous).text().equals(")"))) throw new IllegalArgumentException("An annotated method requires manual review.");
    for (var file : GosuCalls.files(fn)) for (var ref : PsiTreeUtil.findChildrenOfType(file, GosuRefExpr.class)) {
      if (!Objects.equals(ref.getName(), fn.getName())) continue;
      var target = GosuCalls.target(ref);
      if (target == null) throw new IllegalArgumentException("Ambiguous call in " + file.getName());
      if (!target.isEquivalentTo(fn)) continue;
      int start = ref.getTextOffset();
      var ts = GosuSource.tokens(file);
      int dot = GosuSource.before(ts, start);
      if (dot >= 0 && ts.get(dot).text().equals(".")) {
        var receiver = GosuMembers.receiverAt(ref, start);
        if (receiver == null || !receiver.staticAccess()) throw new IllegalArgumentException("The evaluation of the call receiver cannot be dropped.");
        int first = dot - 1;
        while (first >= 2 && ts.get(first - 1).text().equals(".")) first -= 2;
        start = ts.get(first).start();
      }
      (PsiTreeUtil.isAncestor(fn, ref, false) ? bodyEdits : plan).add(file, start, ref.getTextRange().getEndOffset(), targetName);
    }
    String source = fn.getContainingFile().getText();
    String body = fn.getText();
    if (!bodyEdits.results().isEmpty()) {
      String rewritten = bodyEdits.results().get(fn.getContainingFile());
      int suffixLength = source.length() - fn.getTextRange().getEndOffset();
      body = rewritten.substring(fn.getTextOffset(), rewritten.length() - suffixLength);
    }
    int insertion = destination.getTextRange().getEndOffset() - 1;
    if (destination.getContainingFile().getText().charAt(insertion) != '}') throw new IllegalArgumentException("The destination class has no closing brace.");
    // Public visibility preserves calls that used to be within the source class.
    plan.add(destination.getContainingFile(), insertion, insertion, "\n  public static " + body + "\n");
    plan.add(fn.getContainingFile(), from, fn.getTextRange().getEndOffset(), "");
    plan.validate(); return plan;
  }
}
