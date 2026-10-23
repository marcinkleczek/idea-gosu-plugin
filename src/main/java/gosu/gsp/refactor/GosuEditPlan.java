package gosu.gsp.refactor;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import java.util.*;

/** Validates all snapshots before an atomic, undoable multi-file edit. */
public final class GosuEditPlan {
  public record Edit(int start, int end, String text) {}
  private final Map<PsiFile, String> snapshots = new LinkedHashMap<>();
  private final Map<PsiFile, List<Edit>> edits = new LinkedHashMap<>();
  public void add(PsiFile file, int start, int end, String text) {
    snapshots.putIfAbsent(file, file.getText());
    edits.computeIfAbsent(file, f -> new ArrayList<>()).add(new Edit(start, end, text));
  }
  public boolean covers(PsiFile file, int offset) { return edits.getOrDefault(file, List.of()).stream().anyMatch(e -> offset >= e.start() && offset < e.end()); }
  public Map<PsiFile, String> results() {
    Map<PsiFile, String> result = new LinkedHashMap<>();
    for (var entry : edits.entrySet()) {
      var sorted = new ArrayList<>(entry.getValue());
      sorted.sort(Comparator.comparingInt(Edit::start).reversed());
      StringBuilder text = new StringBuilder(snapshots.get(entry.getKey()));
      int boundary = text.length();
      for (var edit : sorted) {
        if (edit.end() > boundary || edit.start() < 0 || edit.end() < edit.start()) throw new IllegalArgumentException("Overlapping changes require manual refactoring.");
        text.replace(edit.start(), edit.end(), edit.text()); boundary = edit.start();
      }
      result.put(entry.getKey(), text.toString());
    }
    return result;
  }
  public String summary() { return String.join("\n", edits.keySet().stream().map(f -> f.getVirtualFile() == null ? f.getName() : f.getVirtualFile().getPath()).toList()); }
  public void validate() {
    for (var entry : snapshots.entrySet()) if (!entry.getKey().isValid() || !entry.getValue().equals(entry.getKey().getText()))
      throw new IllegalArgumentException("The file changed after the refactoring was prepared. Run it again.");
    results();
  }
  public void apply(String title) {
    validate();
    if (edits.isEmpty()) return;
    var project = edits.keySet().iterator().next().getProject();
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(edits.keySet())) return;
    WriteCommandAction.runWriteCommandAction(project, title, null, this::applyInWriteAction, edits.keySet().toArray(PsiFile[]::new));
  }
  public void applyInWriteAction() {
    validate();
    var results = results();
    for (var file : results.keySet()) if (PsiDocumentManager.getInstance(file.getProject()).getDocument(file) == null) throw new IllegalArgumentException("No editable document: " + file.getName());
    for (var entry : results.entrySet()) {
      var manager = PsiDocumentManager.getInstance(entry.getKey().getProject());
      var doc = manager.getDocument(entry.getKey());
      doc.setText(entry.getValue()); manager.commitDocument(doc);
    }
  }
}
