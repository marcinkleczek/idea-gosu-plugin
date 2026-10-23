package gosu.gsp.completion;

import com.intellij.psi.*;
import gosu.gsp.lexer.GosuTokenTypes;
import gosu.gsp.psi.GosuFile;
import gosu.gsp.psi.GosuRefExpr;
import java.util.*;

/** Short, local language notes; never evaluate examples or contact an external service. */
public final class GosuSyntaxDocumentation {
  private GosuSyntaxDocumentation() {}
  private record Note(String description, String example) {}
  private static final Map<String,Note> NOTES = Map.ofEntries(
      Map.entry("uses", new Note("Imports a type or enhancement so its short name can be used. Does not add a library to the classpath.", "uses java.util.ArrayList\nvar names = new ArrayList<String>()")),
      Map.entry("enhancement", new Note("Adds methods or properties to an existing type without changing its class. Availability depends on the package and uses imports.", "enhancement TextExtras : String {\n  function shout() : String { return this.toUpperCase() }\n}")),
      Map.entry("property", new Note("Declares a property. Reading it runs the getter, assigning to it runs the setter. Use the name without call parentheses.", "property get Name() : String { return _name }\nproperty set Name(value : String) { _name = value }\n// person.Name = \"Ada\"")),
      Map.entry("get", new Note("A getter returns the property value. Access it as object.Name, not Name().", "property get Name() : String { return _name }")),
      Map.entry("set", new Note("A setter receives the assigned value. It runs when the property is assigned.", "property set Name(value : String) { _name = value }\n// person.Name = \"Ada\"")),
      Map.entry("\\", new Note("Creates a block: a value representing a piece of code with parameters. Parameter types may be inferred from context.", "var twice = \\ n : int -> n * 2\nprint(twice(3))")),
      Map.entry("->", new Note("Separates a block's parameters from its body. In map literals it also separates a key from its value.", "var length = \\ text : String -> text.length()\nvar ages = {\"Ada\" -> 30}")),
      Map.entry("?.", new Note("Null-safe access: when the receiver is null, the member is not accessed. Useful for optional objects.", "var upper = name?.toUpperCase()")),
      Map.entry("?:", new Note("Selects the right-hand value when the left-hand one is null. Can be combined with the null-safe access operator.", "var display = name ?: \"no name\"")),
      Map.entry("var", new Note("Declares a variable. Give the type after a colon, or let Gosu infer it from the initial value.", "var name = \"Ada\"\nvar count : int = 2")),
      Map.entry("override", new Note("Marks an implementation or override of an inherited method or property. The signature must match the base declaration.", "override function run() : void {\n  print(\"Start\")\n}")),
      Map.entry("extends", new Note("Names the base type. A class inherits its accessible members; an interface may extend other interfaces.", "class Child extends Parent { }")),
      Map.entry("implements", new Note("Declares the implemented interfaces. Alt+Enter inside the class helps generate the missing methods.", "class Task implements Runnable {\n  override function run() : void { }\n}")),
      Map.entry("construct", new Note("Declares a constructor invoked by new. A constructor initializes the new instance and declares no result type.", "construct(name : String) {\n  _name = name\n}")),
      Map.entry("typeis", new Note("Tests whether an object is compatible with a type. Gosu can use the condition to narrow the type in that branch.", "if (value typeis String) {\n  print(value.length())\n}")),
      Map.entry("as", new Note("In an expression, converts to a type. In a field declaration it can also expose the field as a named property.", "var text = value as String\n// In a class: var _name : String as Name")),
      Map.entry("for", new Note("Iterates over the elements of a collection. The loop variable takes the element type.", "for (name in names) {\n  print(name)\n}")),
      Map.entry("using", new Note("Scopes the use of a resource. When the scope ends, Gosu releases the resource as defined for it.", "using (var reader = new java.io.StringReader(\"text\")) {\n  print(reader.read())\n}"))
  );
  public static String key(PsiElement element) {
    if (element == null || !(element.getContainingFile() instanceof GosuFile) || element.getFirstChild() != null
        || element.getParent() instanceof GosuRefExpr) return null;
    var kind = element.getNode().getElementType();
    if (kind != GosuTokenTypes.KEYWORD && kind != GosuTokenTypes.OPERATOR && kind != GosuTokenTypes.DOT
        && !element.getText().equals("\\")) return null;
    String text = element.getText();
    if (text.equals("?") || text.equals(".")) {
      String source = element.getContainingFile().getText();
      int start = element.getTextOffset();
      if (text.equals(".") && start > 0 && source.charAt(start - 1) == '?') return "?.";
      if (text.equals("?") && start + 1 < source.length() && source.charAt(start + 1) == '.') return "?.";
    }
    return NOTES.containsKey(text) ? text : null;
  }
  public static String documentation(PsiElement element) {
    String key = key(element);
    if (key == null) return null;
    Note note = NOTES.get(key);
    return "<b>Gosu: " + escape(key) + "</b><p>" + escape(note.description()) + "</p><pre>" + escape(note.example()) + "</pre>";
  }
  private static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
