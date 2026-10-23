package learning

// Run this class or an individual test using the green gutter arrow.
class GreetingTest {
  var _greeting : String

  function beforeTest() { _greeting = "Hello Gosu" }
  function afterTest() { _greeting = null }

  function testUppercase() {
    if (_greeting.toUpperCase() != "HELLO GOSU") {
      throw new AssertionError("Unexpected greeting")
    }
  }

  function testLength() {
    if (_greeting.length() != 10) {
      throw new AssertionError("Expected 10 characters")
    }
  }
}
