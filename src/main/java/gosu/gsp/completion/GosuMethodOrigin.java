package gosu.gsp.completion;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gosu.gsp.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.resolve.*;

/** Explains the declaration behind a suggestion, without guessing an unresolved receiver. */
public final class GosuMethodOrigin {
  private GosuMethodOrigin() {}
  public static String describe(PsiElement target, GosuMembers.Type receiver) {
    PsiElement owner;
    String language;
    if (target instanceof PsiMethod method) {
      owner = method.getContainingClass();
      language = method.isConstructor() ? "Java constructor" : "Java method";
    } else if (target instanceof FunctionDecl || GosuPropertyAccessor.of(target) != null) {
      owner = PsiTreeUtil.getParentOfType(target, ClassDecl.class);
      language = target instanceof FunctionDecl ? "Gosu method" : "Gosu property";
      if (owner instanceof ClassDecl cls && GosuEnhancements.isEnhancement(cls)) {
        return "Added by the enhancement " + GosuTypes.qualifiedName(cls)
            + " for the type " + GosuEnhancements.targetText(cls) + ".";
      }
      if (owner == null) return "Gosu function declared in the file " + target.getContainingFile().getName() + ".";
    } else return "";
    if (owner == null) return language + ".";
    String name = owner instanceof PsiClass cls ? cls.getQualifiedName() : GosuTypes.qualifiedName((ClassDecl)owner);
    boolean inherited = receiver != null && receiver.declaration() != null && !receiver.declaration().isEquivalentTo(owner);
    return language + (inherited ? ", inherited from " : ", declared in ") + name + ".";
  }
  public static String suffix(PsiElement target, GosuMembers.Type receiver) {
    String description = describe(target, receiver);
    return description.isEmpty() ? "" : " — " + description;
  }
  public static GosuMembers.Type receiver(PsiElement usage) {
    if (usage == null || !(usage.getContainingFile() instanceof GosuFile)) return null;
    var reference = PsiTreeUtil.getParentOfType(usage, GosuRefExpr.class, false);
    if (reference == null) return null;
    return GosuMembers.receiverAt(reference, reference.getTextOffset());
  }
  public static String html(PsiElement target, PsiElement usage) {
    String text = describe(target, receiver(usage));
    if (text.isEmpty()) return "";
    return "<p><b>Origin:</b> " + escape(text) + "</p>";
  }
  public static String append(String document, String section) {
    if (section.isEmpty()) return document;
    if (document == null) return section;
    int end = document.toLowerCase(java.util.Locale.ROOT).lastIndexOf("</body>");
    if (end < 0) end = document.toLowerCase(java.util.Locale.ROOT).lastIndexOf("</html>");
    return end < 0 ? document + section : document.substring(0, end) + section + document.substring(end);
  }
  private static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
