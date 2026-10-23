package gosu.gsp.resolve;

import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import gosu.gsp.lexer.GosuLexer;
import gosu.gsp.lexer.GosuTokenTypes;

import java.util.ArrayList;
import java.util.List;

/** Token-aware source queries shared by type inference and import resolution. */
public final class GosuSource {
  private GosuSource() {}
  public record Token(String text, IElementType kind, int start, int end) {}

  public static List<Token> tokens(PsiFile file) {
    return CachedValuesManager.getCachedValue(file,
        () -> CachedValueProvider.Result.create(tokenize(file.getText()), file));
  }

  public static List<Token> tokenize(String text) {
    List<Token> result = new ArrayList<>();
    GosuLexer lexer = new GosuLexer();
    lexer.start(text);
    while (lexer.getTokenType() != null) {
      IElementType kind = lexer.getTokenType();
      if (kind != TokenType.WHITE_SPACE && kind != GosuTokenTypes.LINE_COMMENT
          && kind != GosuTokenTypes.BLOCK_COMMENT) {
        String value = text.substring(lexer.getTokenStart(), lexer.getTokenEnd());
        if (kind == GosuTokenTypes.OPERATOR && value.matches(">{2,}")) {
          for (int i = lexer.getTokenStart(); i < lexer.getTokenEnd(); i++) result.add(new Token(">", kind, i, i + 1));
        } else result.add(new Token(value, kind, lexer.getTokenStart(), lexer.getTokenEnd()));
      }
      lexer.advance();
    }
    return result;
  }

  public static int before(List<Token> tokens, int offset) {
    int low = 0, high = tokens.size();
    while (low < high) {
      int mid = (low + high) >>> 1;
      if (tokens.get(mid).end() <= offset) low = mid + 1;
      else high = mid;
    }
    return low - 1;
  }

  public static boolean dot(Token t) { return t.text().equals(".") || t.text().equals("?."); }

  public static String packageName(PsiFile file) {
    List<Token> ts = tokens(file);
    for (int i = 0; i < ts.size(); i++) {
      if (ts.get(i).text().equals("package")) return qualified(ts, i + 1);
      if (ts.get(i).text().equals("class")) break;
    }
    return "";
  }

  public static List<String> imports(PsiFile file) {
    List<String> imports = new ArrayList<>();
    List<Token> ts = tokens(file);
    for (int i = 0; i < ts.size(); i++) {
      if (ts.get(i).text().equals("uses")) imports.add(qualified(ts, i + 1));
    }
    return imports;
  }

  private static String qualified(List<Token> ts, int from) {
    StringBuilder name = new StringBuilder();
    for (int i = from; i < ts.size(); i += 2) {
      if (ts.get(i).kind() != GosuTokenTypes.IDENTIFIER && !ts.get(i).text().equals("*")) break;
      name.append(ts.get(i).text());
      if (i + 1 >= ts.size() || !ts.get(i + 1).text().equals(".")) break;
      name.append('.');
    }
    return name.toString();
  }
}
