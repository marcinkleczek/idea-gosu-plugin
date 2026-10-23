package gosu.gsp.intentions;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;
import org.jetbrains.annotations.NotNull;
import java.util.*;

/** Creates a same-file function only when its arguments and statement context are known. */
public final class GosuCreateFunctionIntention extends PsiElementBaseIntentionAction {
  @Override public @NotNull String getText() { return "Create function from usage"; }
  @Override public @NotNull String getFamilyName() { return "Gosu declarations"; }
  public static String source(PsiElement element) {
    GosuRefExpr ref = PsiTreeUtil.getParentOfType(element, GosuRefExpr.class, false);
    if (ref == null || ref.getName() == null || ref.getReference().resolve() != null
        || GosuMembers.isMemberPosition(ref.getContainingFile(), ref.getTextOffset())) return null;
    var ts = GosuSource.tokens(ref.getContainingFile());
    int name = GosuSource.before(ts, ref.getTextRange().getEndOffset());
    if (name + 1 >= ts.size() || !ts.get(name + 1).text().equals("(")) return null;
    // A void stub is safe only for a standalone statement.
    if (name > 0 && !Set.of("{", "}", ";").contains(ts.get(name - 1).text())
        && !ref.getContainingFile().getText().substring(ts.get(name - 1).end(), ts.get(name).start()).contains("\n")) return null;
    var ends = GosuCallTypes.argumentEnds(ts, name + 1);
    if (ends == null) return null;
    int close = ends.isEmpty() ? name + 2 : ends.getLast() + 1;
    if (close + 1 < ts.size() && !Set.of(";", "}").contains(ts.get(close + 1).text())
        && !ref.getContainingFile().getText().substring(ts.get(close).end(), ts.get(close + 1).start()).contains("\n")) return null;
    List<String> params = new ArrayList<>();
    for (int i = 0; i < ends.size(); i++) {
      String type = GosuExplicitTypeIntention.typeText(GosuMembers.expression(ref, ts, ends.get(i), 0));
      if (type.isBlank()) return null;
      params.add("arg" + (i + 1) + " : " + type);
    }
    FunctionDecl caller = PsiTreeUtil.getParentOfType(ref, FunctionDecl.class);
    String modifier = caller != null && GosuMembers.modifiers(caller).contains("static") ? "static " : "";
    return "\n  " + modifier + "function " + ref.getName() + "(" + String.join(", ", params) + ") {\n    throw new java.lang.UnsupportedOperationException(\"Not implemented yet\")\n  }\n";
  }
  @Override public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return element.getContainingFile() instanceof GosuFile && !DumbService.isDumb(project) && source(element) != null;
  }
  @Override public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    String source = source(element);
    if (source == null) return;
    ClassDecl cls = PsiTreeUtil.getParentOfType(element, ClassDecl.class);
    int offset = cls == null ? editor.getDocument().getTextLength() : GosuOverrideMethods.insertionOffset(cls);
    if (offset < 0) return;
    editor.getDocument().insertString(offset, source);
    editor.getCaretModel().moveToOffset(offset + source.indexOf("throw"));
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
  }
}
