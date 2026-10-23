package gosu.gsp.completion;

import com.intellij.lang.parameterInfo.*;
import com.intellij.psi.PsiElement;
import gosu.gsp.resolve.GosuSignatures;
import org.jetbrains.annotations.NotNull;

/** Ctrl+P includes all known Gosu/Java overloads, including enhancement methods. */
public final class GosuParameterInfoHandler implements ParameterInfoHandler<PsiElement, GosuSignatures.Callable> {
  @Override public PsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
    var call = GosuCalls.at(context.getFile(), context.getOffset());
    return call == null ? null : call.reference();
  }
  @Override public void showParameterInfo(@NotNull PsiElement element, @NotNull CreateParameterInfoContext context) {
    var call = GosuCalls.at(context.getFile(), context.getOffset());
    if (call == null) return;
    context.setItemsToShow(call.signatures().toArray());
    context.showHint(element, call.openOffset(), this);
  }
  @Override public PsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
    var call = GosuCalls.at(context.getFile(), context.getOffset());
    return call == null ? null : call.reference();
  }
  @Override public void updateParameterInfo(@NotNull PsiElement element, @NotNull UpdateParameterInfoContext context) {
    var call = GosuCalls.at(context.getFile(), context.getOffset());
    if (call == null || call.reference() != element) { context.removeHint(); return; }
    context.setCurrentParameter(call.parameterIndex());
  }
  @Override public void updateUI(@NotNull GosuSignatures.Callable signature, @NotNull ParameterInfoUIContext context) {
    int index = context.getCurrentParameterIndex(), from = 0, to = 0;
    if (index >= 0 && index < signature.parameters().size()) {
      from = signature.name().length() + 1;
      for (int i = 0; i < index; i++) from += signature.parameters().get(i).length() + 2;
      to = from + signature.parameters().get(index).length();
    }
    context.setupUIComponentPresentation(signature.text(), from, to, false, false, false, context.getDefaultParameterColor());
  }
  static String typeOf(PsiElement declaration) {
    String type = GosuSignatures.declaredType(declaration);
    return type.isEmpty() ? null : type;
  }
}
