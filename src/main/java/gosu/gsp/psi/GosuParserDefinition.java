package gosu.gsp.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.ParserDefinition.SpaceRequirements;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.TokenSet;
import gosu.gsp.GosuLanguage;
import gosu.gsp.lexer.GosuLexer;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.stubs.GosuStubVersion;
import org.jetbrains.annotations.NotNull;

public final class GosuParserDefinition implements ParserDefinition {
  private static final IFileElementType FILE = new IStubFileElementType<>("GOSU_FILE", GosuLanguage.INSTANCE) {
    @Override
    public int getStubVersion() {
      return GosuStubVersion.VERSION;
    }
  };

  private static final TokenSet WHITE_SPACE = TokenSet.create(TokenType.WHITE_SPACE);
  private static final TokenSet COMMENTS = TokenSet.create(
      GosuTokenTypes.LINE_COMMENT, GosuTokenTypes.BLOCK_COMMENT);
  private static final TokenSet STRINGS = TokenSet.create(
      GosuTokenTypes.STRING, GosuTokenTypes.CHAR_LITERAL);

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new GosuLexer();
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new GosuParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE;
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return COMMENTS;
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return STRINGS;
  }

  @Override
  public @NotNull TokenSet getWhitespaceTokens() {
    return WHITE_SPACE;
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new GosuFile(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull ASTNode node) {
    IElementType type = node.getElementType();
    if (type == GosuElementTypes.CLASS_DECL) {
      return new GosuDecls.ClassDecl(node);
    }
    if (type == GosuElementTypes.FUNCTION_DECL) {
      return new GosuDecls.FunctionDecl(node);
    }
    if (type == GosuElementTypes.VAR_DECL) {
      return new GosuDecls.VarDecl(node);
    }
    if (type == GosuElementTypes.LAMBDA) return new GosuLambda(node);
    if (type == GosuElementTypes.PARAM) {
      return new GosuDecls.Param(node);
    }
    if (type == GosuElementTypes.USES_DECL) {
      return new GosuUsesDecl(node);
    }
    if (type == GosuElementTypes.REF_EXPR) {
      return new GosuRefExpr(node);
    }
    if (type == GosuElementTypes.TYPE_REF) {
      return new GosuTypeRef(node);
    }
    return new com.intellij.extapi.psi.ASTWrapperPsiElement(node);
  }
}
