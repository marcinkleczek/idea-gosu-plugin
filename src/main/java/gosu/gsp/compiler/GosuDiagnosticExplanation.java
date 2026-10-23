package gosu.gsp.compiler;

/** Local learning hints for known SDK messages; never replaces the compiler diagnosis. */
public final class GosuDiagnosticExplanation {
  private GosuDiagnosticExplanation() {}

  public record Hint(String cause, String suggestion, String example) {}

  public static Hint explain(String message) {
    String text = message.strip();
    if (text.matches("You must explicitly coerce .+ to .+ using the as keyword\\."))
      return new Hint("Gosu requires an explicit conversion between these types.",
          "Check the expected type. Use a compatible value or convert it deliberately with the as operator; the conversion may still fail at run time.",
          "var text : String = 123 as String");
    if (text.matches("The type \".+\" cannot be converted to \".+\""))
      return new Hint("The value's type does not match the type required at this location.",
          "Compare both types in the message. Check the assignment, argument, or function result, and any earlier errors. Adding as alone does not guarantee a valid conversion.",
          "var count : int = 3");
    if (text.matches("Could not resolve symbol for : .+"))
      return new Hint("The compiler does not recognize this name in the current scope.",
          "Check the spelling and the declaration site. A variable declared inside a block may not be visible outside it.",
          "var name = \"Ada\"\nprint(name)");
    if (text.matches("\".+\" is not a valid type\\."))
      return new Hint("The compiler does not recognize this type.",
          "Check the name, the uses declaration, and the SDK/JDK/classpath in the Gosu settings. The library that provides the type must be visible to the compiler.",
          "uses java.util.ArrayList\nvar names = new ArrayList<String>()");
    if (text.matches("Wrong number of arguments to function .+\\.  Expected \\d+, got \\d+\\."))
      return new Hint("The number of arguments does not match the function signature.",
          "Compare Expected and got in the message. Open Parameter Info and check the available overloads.",
          "function greet(name : String) { print(name) }\ngreet(\"Ada\")");
    if (text.equals("Missing return statement."))
      return new Hint("The function declares a result, but not every required path returns a value.",
          "Add a return with a value of the proper type, including the relevant conditional branches, or make the function void if it should not return anything.",
          "function greeting() : String { return \"Hello\" }");
    if (text.matches("Property, \".+\", of class, \".+\", is not writable\\."))
      return new Hint("This property cannot be written to at this location.",
          "It may have only a getter or an inaccessible setter. Check its declaration and use the provided method for changing state. The getter is read-only.",
          "var length = \"Ada\".length");
    if (text.matches("No function descriptor found for function, .+, on class, .+"))
      return new Hint("The compiler found no matching method on the given type.",
          "Check the name, the receiver type, and the arguments. If the method comes from an enhancement, check its uses declaration and the classpath. Completion after the dot can help.",
          "var upper = \"Ada\".toUpperCase()");
    return null;
  }

  public static String tooltip(String message) {
    Hint hint = explain(message);
    String original = "<b>Gosu SDK message</b><pre>" + escape(message) + "</pre>";
    if (hint == null) return "<html>" + original + "<p>No detailed explanation is available for this message. "
        + "Check the reported location and any earlier errors; later errors may be caused by them.</p></html>";
    return "<html>" + original + "<p><b>What does it mean?</b> " + escape(hint.cause())
        + "</p><p><b>What to check</b> " + escape(hint.suggestion())
        + "</p><p><b>Syntax example (not an automatic fix)</b></p><pre>"
        + escape(hint.example()) + "</pre></html>";
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }
}
