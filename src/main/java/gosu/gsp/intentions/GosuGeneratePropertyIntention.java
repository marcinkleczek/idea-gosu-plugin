package gosu.gsp.intentions;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import org.jetbrains.annotations.NotNull;

public final class GosuGeneratePropertyIntention extends PsiElementBaseIntentionAction {
  @Override public @NotNull String getText() { return "Generate property getter / setter"; }
  @Override public @NotNull String getFamilyName() { return "Gosu declarations"; }
  private VarDecl field(PsiElement element) { return PsiTreeUtil.getParentOfType(element, VarDecl.class, false); }
  private String name(VarDecl field) {
    String raw = field.getName() == null ? "" : field.getName().replaceFirst("^_+", "");
    return raw.isEmpty() ? "" : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
  }
  @Override public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    var field = field(element);
    if (DumbService.isDumb(project) || field == null || !field.isMember() || !field.getText().startsWith("var ")
        || name(field).isEmpty() || name(field).equals(field.getName()) || GosuMembers.modifiers(field).contains("static")
        || GosuExplicitTypeIntention.typeText(GosuMembers.infer(field)).isBlank()) return false;
    ClassDecl owner = PsiTreeUtil.getParentOfType(field, ClassDecl.class);
    if (owner == null || GosuOverrideMethods.insertionOffset(owner) < 0) return false;
    // Existing aliases/accessors and other members must not be shadowed.
    if (owner.getText().matches("(?s).*\\bas\\s+" + java.util.regex.Pattern.quote(name(field)) + "\\b.*")) return false;
    return PsiTreeUtil.findChildrenOfAnyType(owner, VarDecl.class, FunctionDecl.class).stream()
        .noneMatch(e -> e instanceof PsiNamedElement named && name(field).equals(named.getName()));
  }
  @Override public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!isAvailable(project, editor, element)) return;
    var field = field(element);
    ClassDecl owner = PsiTreeUtil.getParentOfType(field, ClassDecl.class);
    String type = GosuExplicitTypeIntention.typeText(GosuMembers.infer(field));
    String source = "\n  property get " + name(field) + "() : " + type + " {\n    return " + field.getName() + "\n  }\n";
    if (!GosuMembers.modifiers(field).contains("final")) source += "\n  property set " + name(field) + "(value : " + type + ") {\n    " + field.getName() + " = value\n  }\n";
    int offset = GosuOverrideMethods.insertionOffset(owner);
    editor.getDocument().insertString(offset, source);
    editor.getCaretModel().moveToOffset(offset);
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
  }
}
