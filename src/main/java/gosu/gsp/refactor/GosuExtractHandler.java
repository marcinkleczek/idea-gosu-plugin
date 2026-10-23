package gosu.gsp.refactor;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.*;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.codeInsight.FileModificationService;
import org.jetbrains.annotations.NotNull;

public final class GosuExtractHandler implements RefactoringActionHandler {
  private final boolean method;
  public GosuExtractHandler() { this(false); }
  public GosuExtractHandler(boolean method) { this.method = method; }
  @Override public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, DataContext context) {
    if (DumbService.isDumb(project)) return;
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    int start = editor.getSelectionModel().getSelectionStart(), end = editor.getSelectionModel().getSelectionEnd();
    String title = method ? "Extract Method (Gosu)" : "Extract Variable (Gosu)";
    if (method ? GosuExtractMethod.plan(file, start, end) == null : GosuExtractVariable.selection(file, start, end) == null) {
      Messages.showInfoMessage(project, method ? "Select the whole expression after var/return, or separate lines with declarations and calls. Parameter and result types must be known. One output is supported; mutating input variables, blocks, and control flow require manual refactoring." : "Select the whole initializer of a local var or the whole expression after return. The statement must be on its own line. Blocks and assignments inside the expression are not supported.", title);
      return;
    }
    String snapshot = file.getText();
    String name = Messages.showInputDialog(project, method ? "New method name:" : "New variable name:", title, null, method ? "extractedMethod" : "extractedValue", new InputValidator() {
      public boolean checkInput(String input) { return GosuExtractVariable.validName(file, input); }
      public boolean canClose(String input) { return checkInput(input); }
    });
    if (name == null || !snapshot.equals(editor.getDocument().getText()) || !FileModificationService.getInstance().preparePsiElementForWrite(file)) return;
    WriteCommandAction.runWriteCommandAction(project, title, null, () -> {
      editor.getDocument().setText(method ? GosuExtractMethod.transform(file, start, end, name) : GosuExtractVariable.transform(file, start, end, name));
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      editor.getSelectionModel().removeSelection();
    }, file);
  }
  @Override public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext context) {
    Messages.showInfoMessage(project, "Select an expression in the Gosu editor.", "Extract Variable (Gosu)");
  }
}
