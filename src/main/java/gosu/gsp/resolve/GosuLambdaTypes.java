package gosu.gsp.resolve;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import gosu.gsp.completion.GosuCalls;
import gosu.gsp.psi.GosuLambda;
import gosu.gsp.psi.GosuDecls.Param;
import java.util.*;

/** Contextual types for Java SAM arguments; ambiguous overloads remain unknown. */
public final class GosuLambdaTypes {
  private GosuLambdaTypes() {}
  public static GosuMembers.Type infer(Param parameter, GosuLambda lambda) {
    var call = GosuCalls.at(lambda.getContainingFile(), lambda.getTextOffset());
    if (call == null) return null;
    int index = PsiTreeUtil.getChildrenOfTypeAsList(lambda, Param.class).indexOf(parameter);
    GosuMembers.Type result = null;
    for (var signature : call.signatures()) {
      if (call.parameterIndex() >= signature.parameters().size()) return null;
      String text = signature.parameters().get(call.parameterIndex());
      text = text.substring(text.indexOf(':') + 1).trim();
      // IntelliJ's substituted parameter text uses Java wildcard syntax.
      text = text.replace("? super ", "").replace("? extends ", "");
      var expected = GosuMembers.typeFromText(text, signature.target());
      if (expected == null || !(expected.javaType() instanceof PsiClassType ct) || ct.resolve() == null) return null;
      var cls = ct.resolve();
      var methods = Arrays.stream(cls.getAllMethods()).filter(m -> m.hasModifierProperty(PsiModifier.ABSTRACT)
          && !m.hasModifierProperty(PsiModifier.STATIC) && m.getContainingClass() != null
          && !"java.lang.Object".equals(m.getContainingClass().getQualifiedName())).toList();
      if (methods.size() != 1 || index >= methods.getFirst().getParameterList().getParametersCount()) return null;
      var method = methods.getFirst();
      var sub = TypeConversionUtil.getSuperClassSubstitutor(method.getContainingClass(), cls, ct.resolveGenerics().getSubstitutor());
      var type = GosuMembers.javaType(sub.substitute(method.getParameterList().getParameters()[index].getType()));
      if (type == null || result != null && !Objects.equals(type.javaType(), result.javaType())) return null;
      result = type;
    }
    return result;
  }
}
