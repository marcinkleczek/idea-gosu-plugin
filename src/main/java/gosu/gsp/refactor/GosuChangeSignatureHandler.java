package gosu.gsp.refactor;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.GosuSignatures;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.*;

public final class GosuChangeSignatureHandler implements ChangeSignatureHandler {
  @Override public PsiElement findTargetMember(PsiElement element) {
    var ref = PsiTreeUtil.getParentOfType(element, GosuRefExpr.class, false);
    if (ref != null) return gosu.gsp.hierarchy.GosuCalls.target(ref);
    return PsiTreeUtil.getParentOfType(element, FunctionDecl.class, false);
  }
  @Override public String getTargetNotFoundMessage() { return "Place the caret on a Gosu function or one of its calls."; }
  @Override public void invoke(Project project, Editor editor, PsiFile file, DataContext context) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    invoke(project, new PsiElement[]{file.findElementAt(Math.min(editor.getCaretModel().getOffset(), Math.max(0, file.getTextLength() - 1)))}, context);
  }
  @Override public void invoke(Project project, PsiElement[] elements, DataContext context) {
    if (DumbService.isDumb(project)) return;
    PsiElement target = elements.length == 0 ? null : findTargetMember(elements[0]);
    if (!(target instanceof FunctionDecl fn)) { Messages.showInfoMessage(project, getTargetNotFoundMessage(), "Change Signature (Gosu)"); return; }
    new SignatureDialog(fn).show();
  }
  private static final class SignatureDialog extends DialogWrapper {
    private final FunctionDecl function;
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"Current parameter", "New name", "Type", "Argument for new parameter"}, 0) {
      @Override public boolean isCellEditable(int row, int column) { return column != 0; }
    };
    private final JTable table = new JTable(model);
    SignatureDialog(FunctionDecl function) {
      super(function.getProject()); this.function = function;
      for (var param : PsiTreeUtil.getChildrenOfTypeAsList(function, Param.class)) model.addRow(new Object[]{param.getName(), param.getName(), GosuSignatures.declaredType(param), ""});
      setTitle("Change Signature: " + function.getName()); init();
    }
    @Override protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout(8, 8));
      panel.add(new JLabel("Edit the parameters. Gosu calls will be updated; dynamic bindings need manual review."), BorderLayout.NORTH);
      panel.add(new JScrollPane(table), BorderLayout.CENTER);
      JPanel buttons = new JPanel();
      for (String label : new String[]{"Add", "Remove", "Move Up", "Move Down"}) {
        JButton button = new JButton(label); buttons.add(button);
        button.addActionListener(e -> {
          if (table.isEditing()) table.getCellEditor().stopCellEditing();
          int row = table.getSelectedRow();
          if (label.equals("Add")) model.addRow(new Object[]{"", "param", "String", "\"\""});
          else if (row >= 0) {
            if (label.equals("Remove")) model.removeRow(row);
            else { int next = row + (label.equals("Move Up") ? -1 : 1); if (next >= 0 && next < model.getRowCount()) { model.moveRow(row, row, next); table.setRowSelectionInterval(next, next); } }
          }
        });
      }
      panel.add(buttons, BorderLayout.SOUTH); panel.setPreferredSize(new java.awt.Dimension(780, 300)); return panel;
    }
    @Override protected void doOKAction() {
      if (table.isEditing()) table.getCellEditor().stopCellEditing();
      try {
        List<GosuChangeSignature.Parameter> parameters = new ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) parameters.add(new GosuChangeSignature.Parameter(value(row, 0), value(row, 1), value(row, 2), value(row, 3)));
        var plan = GosuChangeSignature.plan(function, parameters);
        if (Messages.showOkCancelDialog(function.getProject(), "New parameter list:\n" + String.join(", ", parameters.stream().map(p -> p.name() + " : " + p.type()).toList())
            + "\n\nFiles to change:\n" + plan.summary(), "Apply Change Signature", "Apply", "Cancel", Messages.getQuestionIcon()) != Messages.OK) return;
        plan.apply("Change Signature (Gosu)"); super.doOKAction();
      } catch (IllegalArgumentException failure) { setErrorText(failure.getMessage()); }
    }
    private String value(int row, int column) { return Objects.toString(model.getValueAt(row, column), "").strip(); }
  }
}
