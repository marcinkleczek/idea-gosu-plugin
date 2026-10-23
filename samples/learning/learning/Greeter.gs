package learning

class Greeter {
  property get Prefix() : String {
    return "Hello"
  }

  function greet(name : String) : String {
    return Prefix + ", " + name
  }
}
