uses learning.LearningPerson

// Completion after ':' lists writable properties.
var person = new LearningPerson() { :Name = "Ada" }

function greet(recipient : LearningPerson, count : int) {
  for (i in 0..|count) {
    print("Hello " + recipient.Name)
  }
}

// Completion after ':' lists named arguments.
greet(:recipient = person, :count = 2)

// Quick Documentation on 'uses', 'var', '?.' or '?:' explains the syntax.
var display = person.Name?.toUpperCase() ?: "UNKNOWN"
print(display)
