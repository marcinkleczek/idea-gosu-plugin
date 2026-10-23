package gosu.gsp.editor;

import com.intellij.codeInsight.editorActions.QuoteHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import gosu.gsp.lexer.GosuTokenTypes;

/** Auto-closes `"…"` and `'…'` (Gosu uses both for strings). */
public final class GosuQuoteHandler implements QuoteHandler {
  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    return isQuoteChar(iterator, offset) && isInsideLiteral(iterator);
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    return isQuoteChar(iterator, offset) && !isInsideLiteral(iterator);
  }

  @Override
  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    return false;
  }

  @Override
  public boolean isInsideLiteral(HighlighterIterator iterator) {
    IElementType type = iterator.getTokenType();
    return type == GosuTokenTypes.STRING || type == GosuTokenTypes.CHAR_LITERAL;
  }

  private static boolean isQuoteChar(HighlighterIterator iterator, int offset) {
    CharSequence text = iterator.getDocument().getCharsSequence();
    if (offset < 0 || offset >= text.length()) {
      return false;
    }
    char c = text.charAt(offset);
    return c == '"' || c == '\'';
  }
}
