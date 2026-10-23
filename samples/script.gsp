// Gosu script sample: declarations, object initializer, member access.
// Open in the IDE to check: semantic colors, Structure View, Ctrl+click.

uses java.util.ArrayList

class FileContainer {
  var _dest : String as DestFile
  var _base : String as BaseDir
  var _update : boolean as Update
  var _when : String as WhenManifestOnly

  function describe() : String {
    return _dest
  }
}

static class ScriptEnvironment {
  static final var WHEN_EMPTY_SKIP : String = "WHEN_EMPTY_SKIP"
}

var jarFile = "app.jar"
var dir = "/tmp/build"

var myFileContainer = new FileContainer() {
  :DestFile = jarFile,
  :BaseDir = dir,
  :Update = true,
  :WhenManifestOnly = ScriptEnvironment.WHEN_EMPTY_SKIP
}

function process(count : int) : void {
  var total = count + 1
  var items = {1, 2, 3}
  for (item in items) {
    print(item)
  }
  for (i in 0..10 index k) {
    print(i)
  }
  try {
    print(total)
  } catch (e : Exception) {
    print(e)
  }
}

print(myFileContainer.DestFile)
process(3)
