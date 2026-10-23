package gosu.gsp.editor;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;
import gosu.gsp.lexer.GosuLexer;
import org.jetbrains.annotations.NotNull;

/** Feeds `//` and `/* *​/` comment tokens to the TODO index. */
public final class GosuTodoIndexer extends LexerBasedTodoIndexer {
  @Override
  public @NotNull Lexer createLexer(OccurrenceConsumer consumer) {
    return new GosuLexer();
  }
}
