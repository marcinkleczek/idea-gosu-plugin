package gosu.gsp.highlight;

import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiWhiteSpace;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.psi.GosuDecls.ClassDecl;
import gosu.gsp.psi.GosuDecls.FunctionDecl;
import gosu.gsp.psi.GosuDecls.Param;
import gosu.gsp.psi.GosuDecls.VarDecl;
import gosu.gsp.psi.GosuRefExpr;
import gosu.gsp.psi.GosuReference;
import gosu.gsp.psi.GosuTypeRef;
import gosu.gsp.psi.GosuUsesDecl;
import org.jetbrains.annotations.NotNull;

/**
 * Semantic highlighting: declarations, calls, members and types get their own
 * colors; the lexer alone cannot tell them apart (all are identifiers).
 */
public final class GosuAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof ClassDecl decl) {
      paintName(decl, GosuHighlightKeys.CLASS_NAME, holder);
    }
    else if (element instanceof FunctionDecl decl) {
      paintName(decl, GosuHighlightKeys.FUNCTION_DECL, holder);
    }
    else if (element instanceof Param decl) {
      paintName(decl, GosuHighlightKeys.PARAMETER, holder);
    }
    else if (element instanceof VarDecl decl) {
      paintName(decl, decl.isMember()
          ? GosuHighlightKeys.FIELD : GosuHighlightKeys.LOCAL_VARIABLE, holder);
    }
    else if (element instanceof GosuTypeRef ref) {
      paintName(ref.getName(), ref, GosuHighlightKeys.CLASS_NAME, holder);
    }
    else if (element instanceof GosuRefExpr ref) {
      annotateRef(ref, holder);
    }
    else if (element instanceof GosuUsesDecl uses) {
      paintLastSegment(uses, holder);
    }
    else if (element.getNode() != null
        && element.getNode().getElementType() == GosuTokenTypes.LABEL) {
      paint(element, GosuHighlightKeys.LABEL_ARG, holder);
    }
  }

  private static void annotateRef(GosuRefExpr ref, AnnotationHolder holder) {
    if (GosuReference.afterDot(ref)) {
      paintName(ref.getName(), ref, GosuHighlightKeys.FIELD, holder);
      return;
    }
    if (GosuReference.followedByParen(ref)) {
      paintName(ref.getName(), ref, GosuHighlightKeys.FUNCTION_CALL, holder);
      return;
    }
    if (ref.getReference() != null && ref.getReference().resolve() instanceof FunctionDecl) {
      paintName(ref.getName(), ref, GosuHighlightKeys.FUNCTION_CALL, holder);
    }
    else if (ref.getReference() != null && ref.getReference().resolve() instanceof ClassDecl) {
      paintName(ref.getName(), ref, GosuHighlightKeys.CLASS_NAME, holder);
    }
  }

  /** Colors the last segment of {@code uses a.b.C} as the imported type. */
  private static void paintLastSegment(GosuUsesDecl uses, AnnotationHolder holder) {
    PsiElement last = null;
    for (PsiElement child : uses.getChildren()) {
      if (child.getNode() != null
          && child.getNode().getElementType() == GosuTokenTypes.IDENTIFIER) {
        last = child;
      }
    }
    if (last != null) {
      paint(last, GosuHighlightKeys.CLASS_NAME, holder);
    }
  }

  private static void paintName(PsiNameIdentifierOwner decl, TextAttributesKey key,
      AnnotationHolder holder) {
    if (decl.getNameIdentifier() != null) {
      paint(decl.getNameIdentifier(), key, holder);
    }
  }

  private static void paintName(String name, PsiElement scope, TextAttributesKey key,
      AnnotationHolder holder) {
    if (name == null) {
      return;
    }
    for (PsiElement child : scope.getChildren()) {
      if (!(child instanceof PsiWhiteSpace) && child.getNode() != null
          && child.getNode().getElementType() == GosuTokenTypes.IDENTIFIER) {
        paint(child, key, holder);
        return;
      }
    }
  }

  private static void paint(PsiElement target, TextAttributesKey key, AnnotationHolder holder) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .range(target)
        .textAttributes(key)
        .create();
  }
}
