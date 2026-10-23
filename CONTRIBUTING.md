# Contributing

Bug reports and patches are welcome. A small Gosu file that reproduces a problem is often the most useful starting point.

The project is licensed under the [MIT License](LICENSE). By contributing, you agree that your contributions are licensed under the same terms. Please follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Development environment

Use JDK 21 and the committed Gradle Wrapper. Set `JAVA_HOME` to your JDK installation, then run:

```sh
./gradlew test buildPlugin
./gradlew runIde
```

Use `gradlew.bat` on Windows. The sandbox IDE is separate from your normal installation; open a sample file there to try your changes. Builds need network access for dependencies and the target IDE.

To use an existing IDE installation:

```sh
./gradlew -PlocalPlatformPath="/path/to/IntelliJ IDEA" test buildPlugin
```

On macOS, the path can be `/Applications/IntelliJ IDEA.app`. Alternatively, `-PplatformVersion=<version>` changes the downloaded IDE. If you change the target, also check the minimum build in `plugin.xml` and the required Java version.

## Tests

```sh
./gradlew test
./gradlew test -PgosuSdkHome=/path/to/gosu-1.18.11
```

Tests include JUnit 5 unit tests and JUnit 4 IntelliJ editor/PSI fixtures via the Vintage engine. SDK-dependent test classes/methods are conditional, and some editor tests perform additional compiler assertions only when `gosuSdkHome` is supplied. The default CI job does not install Gosu. Run the SDK-enabled suite before changing compiler or test bridges and before releasing SDK-dependent behavior.

Reports are in `build/reports/tests/test/index.html`. Add regression coverage for changed behavior, including unsupported/ambiguous cases. Use real PSI/editor fixtures for resolution, completion, and refactoring rather than only matching text. Lexer changes need lexer tests; formatting changes need spacing and preservation checks.

## Code layout

| Directory under `src/main/java/gosu/gsp` | Responsibility |
| --- | --- |
| `lexer`, `psi`, `stubs` | Tokenization, tolerant parsing, references, and indexes |
| `resolve`, `completion` | Types, members, completion, imports, documentation, and hints |
| `highlight`, `structure`, `format`, `editor` | Editor integration and presentation |
| `hierarchy`, `refactor`, `intentions`, `inspection` | Navigation, transformations, and analysis |
| `settings`, `run`, `testing`, `compiler` | Project configuration, execution, tests, and compiler diagnostics |

`src/compilerBridge` contains worker code compiled for Java 17. `src/main/resources/META-INF/plugin.xml` registers extensions. `samples` contains small examples that can be shared publicly. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes how the pieces fit together.

## Working on the code

- Keep changes focused and use simple code before adding abstractions.
- Preserve tolerant parsing of incomplete source and conservative formatting.
- Do not invent a resolved target or inferred type for ambiguous input.
- Preserve evaluation order and side effects in refactorings; reject unsupported transformations with a clear explanation.
- Preserve cancellation, timeout, stale-result handling, and process cleanup in compiler checks.
- Write documentation and UI text in English.
- Update the feature guide when adding a capability or changing its supported scope.
- Do not commit IDE settings, SDKs, build products, logs, credentials, or proprietary sample code.

## Pull requests

Explain what was broken or missing and how the change helps. For editor changes, include a before/after example or a screenshot from the sandbox.

Run `./gradlew test buildPlugin` before opening a PR. Try UI changes in the sandbox too, and mention the IDE and SDK versions you tested.
