# Gosu GSP Support

An IntelliJ IDEA plugin for Gosu scripts (`.gsp`), classes (`.gs`), and enhancements (`.gsx`). It adds code completion, navigation, refactoring, and script execution, including support for Java classes used from Gosu.

Targets **IntelliJ IDEA 2026.2.1**. Tested with **Gosu 1.18.11**.

## Features

- Syntax highlighting, formatting, folding, and Structure View.
- Completion for Gosu and Java members, enhancements, and imports. Parameter info and inferred-type hints help with method calls and generic collections.
- Go to Declaration and Implementation, Find Usages, and type and call hierarchies.
- Rename, Optimize Imports, Extract Variable/Method, Change Signature, and Move. The more complex refactorings support a limited set of cases, described in the [feature reference](docs/FEATURES.md#refactoring-and-generation).
- Unused-code and null inspections. With a local Gosu SDK, the plugin can also show compiler errors as you type.
- Run configurations for scripts and a small test runner, with source links in console output.

There is no debugger yet. Type inference is incomplete, particularly for blocks, complex generics, and dynamic types.

## Installation

Download the plugin ZIP from Releases, or [build it locally](#building).

In IntelliJ, open **Settings → Plugins → gear menu → Install Plugin from Disk…**, select the ZIP, and restart if prompted.

If `.gsp` files open as Groovy Server Pages, go to **Settings → Editor → File Types** and assign `*.gsp` to **Gosu GSP**.

## Running your first script

Open **Settings → Languages & Frameworks → Gosu** and set:

- **Gosu SDK home** — the SDK directory containing `bin` and `lib`.
- **JDK home** — the JDK to use for Gosu.
- **Script classpath** — your source roots and any required JARs, one per line.

For the examples in this repository, add `samples/learning` to the classpath. Open [`learn.gsp`](samples/learning/learn.gsp), wait for indexing to finish, and click the Run icon in the gutter.

```gosu
uses java.util.ArrayList

var names = new ArrayList<String>()
names.add("Gosu")
print(names.get(0).toUpperCase())
```

Completion after `names.get(0).` should offer `String` methods. To see compiler errors in the editor, enable **Check compilation in the editor** in Gosu settings. Highlighting and basic editing work without the SDK.

The [setup guide](docs/GETTING_STARTED.md) covers classpath details, tests, and troubleshooting.

## Building

Set `JAVA_HOME` to a **JDK 21** installation and run:

```sh
./gradlew test buildPlugin
```

Use `gradlew.bat` on Windows. The Gradle Wrapper is included; the first build downloads Gradle and the target IDE. The plugin ZIP goes into `build/distributions/`.

```sh
./gradlew runIde                         # Start a separate development IDE
./gradlew test -PgosuSdkHome=/path/to/gosu # Also test the SDK integration
```

Without `gosuSdkHome`, SDK tests are skipped. See [CONTRIBUTING.md](CONTRIBUTING.md) for the development setup and test details.

## Bugs and contributions

Please include a small Gosu example when reporting a bug, along with your plugin, IDE, JDK, and SDK versions. For completion or navigation problems, show where the caret was placed.

[Feature reference](docs/FEATURES.md) · [Architecture](docs/ARCHITECTURE.md) · [Changelog](CHANGELOG.md) · [Release instructions](docs/RELEASING.md)

## License

[MIT](LICENSE).
