package gosu.gsp.completion;

import com.intellij.codeInsight.hints.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import gosu.gsp.psi.GosuDecls.VarDecl;
import gosu.gsp.resolve.GosuMembers;
import gosu.gsp.resolve.GosuSignatures;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;

/** Inferred variable types; explicit annotations and unknown types get no redundant hint. */
public final class GosuTypeInlayHintsProvider implements InlayHintsProvider<NoSettings> {
  private static final SettingsKey<NoSettings> KEY = new SettingsKey<>("gosu.inferred.types");
  @Override public @NotNull NoSettings createSettings() { return new NoSettings(); }
  @Override public @NotNull String getName() { return "Inferred variable types"; }
  @Override public @NotNull SettingsKey<NoSettings> getKey() { return KEY; }
  @Override public String getPreviewText() { return "var message = \"Hello Gosu\""; }
  @Override public @NotNull ImmediateConfigurable createConfigurable(@NotNull NoSettings settings) {
    return listener -> new JPanel();
  }
  @Override public InlayHintsCollector getCollectorFor(@NotNull PsiFile file, @NotNull Editor editor,
      @NotNull NoSettings settings, @NotNull InlayHintsSink sink) {
    return new FactoryInlayHintsCollector(editor) {
      @Override public boolean collect(@NotNull PsiElement element, @NotNull Editor editor, @NotNull InlayHintsSink sink) {
        if (element instanceof VarDecl variable && variable.getNameIdentifier() != null && !DumbService.isDumb(variable.getProject())) {
          String type = inferredLabel(variable);
          if (!type.isEmpty()) sink.addInlineElement(variable.getNameIdentifier().getTextRange().getEndOffset(), true, getFactory().smallText(": " + type));
        }
        return true;
      }
    };
  }
  public static String inferredLabel(VarDecl variable) {
    if (!GosuSignatures.declaredType(variable).isEmpty()) return "";
    return GosuMembers.typeLabel(GosuMembers.infer(variable));
  }
}
