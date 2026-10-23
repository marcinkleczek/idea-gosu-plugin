uses learning.Greeter
uses java.util.ArrayList

// Set Script classpath to samples/learning in the Gosu project settings.
// Try Ctrl+Space after greeter., greeter.Prefix. and names.get(0).
// Ctrl+click Greeter, greet or Prefix to navigate to their declarations.
var greeter = new Greeter()
var names = new ArrayList<String>()
names.add("Gosu")
print(greeter.greet(names.get(0).toUpperCase()))
print(greeter.Prefix.length())

// Uncomment to try a clickable stack trace in the Run console:
// throw new RuntimeException("Learning console navigation")
