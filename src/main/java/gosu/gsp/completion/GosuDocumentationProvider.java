package gosu.gsp.completion;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import gosu.gsp.psi.GosuDecls.*;
import gosu.gsp.psi.*;
import gosu.gsp.resolve.*;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Ctrl+Q for Gosu source comments and signatures, with native JavaDoc for Java symbols. */
public final class GosuDocumentationProvider implements DocumentationProvider {
  @Override public PsiElement getCustomDocumentationElement(Editor editor, PsiFile file, PsiElement context, int offset) {
    PsiElement syntax = file.findElementAt(Math.min(offset, Math.max(0, file.getTextLength() - 1)));
    if (GosuSyntaxDocumentation.key(syntax) != null) return syntax;
    if (syntax instanceof PsiWhiteSpace && offset > 0) {
      PsiElement previous = file.findElementAt(offset - 1);
      if (GosuSyntaxDocumentation.key(previous) != null) return previous;
    }
    PsiReference reference = file.findReferenceAt(offset);
    if (reference == null) return null;
    PsiElement resolved = reference.resolve();
    if (resolved != null) return resolved;
    return GosuCalls.signatures(reference.getElement()).isEmpty() ? null : reference.getElement();
  }

  @Override public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof PsiMember) return new JavaDocumentationProvider().getQuickNavigateInfo(element, originalElement);
    return signature(element);
  }

  @Override public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    String syntax = GosuSyntaxDocumentation.documentation(element);
    if (syntax != null) return syntax;
    if (element instanceof PsiMember) return GosuMethodOrigin.append(
        new JavaDocumentationProvider().generateDoc(element, originalElement), GosuMethodOrigin.html(element, originalElement));
    var overloads = GosuCalls.signatures(element);
    if (!overloads.isEmpty()) {
      StringBuilder html = new StringBuilder();
      for (var overload : overloads) {
        html.append("<pre>").append(escape(overload.text())).append("</pre>");
        html.append(GosuMethodOrigin.html(overload.target(), originalElement == null ? element : originalElement));
        String comment = comment(overload.target());
        if (!comment.isBlank()) html.append("<p>").append(escape(comment).replace("\n", "<br>" )).append("</p>");
      }
      return html.toString();
    }
    String signature = signature(element);
    if (signature == null) return null;
    String file = element.getContainingFile() == null ? "" : element.getContainingFile().getName();
    String comment = comment(element);
    String typeExplanation = element instanceof VarDecl || element instanceof Param ? GosuTypeExplanation.explain(element).html() : "";
    return "<pre>" + escape(signature) + "</pre><p>" + escape(comment).replace("\n", "<br>")
        + "</p><span style=\"color:gray\">" + escape(file) + "</span>" + GosuMethodOrigin.html(element, originalElement) + typeExplanation;
  }

  private static String signature(PsiElement element) {
    var callable = GosuSignatures.of(element);
    if (callable != null) return callable.text();
    if (element instanceof ClassDecl cls) return (GosuEnhancements.isEnhancement(cls) ? "enhancement " : "class ") + GosuTypes.qualifiedName(cls);
    if (element instanceof VarDecl || element instanceof Param) {
      String name = ((PsiNamedElement) element).getName();
      String type = GosuMembers.typeLabel(GosuMembers.infer(element));
      return name + (type.isBlank() ? "" : " : " + type);
    }
    return null;
  }

  public static String comment(PsiElement element) {
    if (element instanceof PsiDocCommentOwner owner && owner.getDocComment() != null) return owner.getDocComment().getText();
    PsiElement previous = element.getPrevSibling();
    while (previous != null && (previous instanceof PsiWhiteSpace
        || Set.of("public", "private", "protected", "static", "override", "final").contains(previous.getText()))) previous = previous.getPrevSibling();
    if (previous == null || !previous.getText().startsWith("/**")) return "";
    return previous.getText().replaceFirst("^/\\*\\*", "").replaceFirst("\\*/$", "")
        .replaceAll("(?m)^\\s*\\* ?", "").trim();
  }

  private static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
