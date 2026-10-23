# Getting started

## Compatibility

The configured target is IntelliJ IDEA 2026.2.1. The plugin descriptor requires platform build 262 or later and declares no upper bound; this is not a guarantee of compatibility with every future IDE release. The plugin depends on the platform's Java and test-runner plugins. Validate the exact IDE edition/build you intend to use.

Building the plugin requires JDK 21. Running Gosu uses the JDK selected for your project and must satisfy your SDK's requirements. The compiler bridge is compiled for Java 17, but that alone does not establish compatibility with every JDK/SDK combination. SDK integration has been exercised with Gosu 1.18.11. macOS is the existing development environment; other operating systems require runtime smoke testing.

## Configure the SDK, JDK, and classpath

Open **Settings → Languages & Frameworks → Gosu**.

| Setting | Meaning |
| --- | --- |
| Gosu SDK home | The directory containing the SDK's `bin` and `lib` folders, not their parent directory |
| JDK home | Java used to run Gosu; also sets the IntelliJ project JDK. A macOS `.jdk` bundle is accepted. |
| Script classpath | One directory or JAR per line; use **Add directory or JAR…** to select entries |
| Check compilation in the editor | Enables optional checks in a separate compiler process |

A blank JDK setting uses the project JDK, falling back to the IDE environment when no project JDK is available. `~/` is expanded in SDK and launcher paths. Classpath entries may also use `~/`; relative entries are resolved against the project directory.

Use package roots: if `learning.Greeter` is in `samples/learning/learning/Greeter.gs`, add `samples/learning`. A directory containing JARs does not automatically include all those JARs; add them individually. JARs in the Gosu SDK's `lib` directory are indexed automatically.

Libraries configured only in IntelliJ's **Project Structure** can contribute to completion, but must also be added to **Script classpath** when required to run a script. Wait for indexing after changing settings.

Use **Review / check configuration…** to inspect effective paths, SDK JARs, source roots, and path errors. This preview does not run code or save settings.

## Run a script

Open a `.gsp` file and choose its gutter Run action or context-menu Run action. You can also create a **Gosu Script** configuration in **Run → Edit Configurations…**.

Configurations with an empty **Gosu executable** field, or the legacy value `gosu`, use the configured SDK launcher. An explicit executable overrides it. With no SDK configured, the launcher falls back to `gosu` on `PATH`. Program arguments follow the script path.

The Run console turns compiler locations such as `[line:… col:…]` and stack-trace locations such as `File.gs:42` into source links. Compiler locations preserve the column as well as the line.

## Run tests

The built-in runner uses a small convention and requires no extra test library:

- The class name ends in `Test` and has a public no-argument constructor, including an implicit one.
- Public instance methods named `test…`, with no parameters and no return value, are tests.
- Optional public `beforeTest()` and `afterTest()` methods provide setup and cleanup.
- Each test receives a fresh class instance. Exceptions and failed assertions fail the test; cleanup is attempted after setup or test failure.

Use the gutter action beside the class or test method. The **Gosu Tests** configuration uses project SDK/JDK/classpath settings and the test source root. Results include duration, output, and stack traces. Selecting a test navigates to its declaration; Stop terminates the test process.

Try [`GreetingTest.gs`](../samples/learning/learning/GreetingTest.gs). Tests execute saved source and can perform whatever operations that source requests. This runner does not implement JUnit or `gw.test.TestClass`; those base classes are rejected rather than run with an incomplete lifecycle.

## Check compilation while editing

Enable **Check compilation in the editor** after setting SDK and JDK paths. The checker passes the current, potentially unsaved `.gsp`, `.gs`, or `.gsx` document to the SDK in a separate process; it does not run the program. Dependencies from other files must be saved.

Checks include Gosu classpath entries and project source roots. The worker has a 20-second timeout and a 256 MB heap limit. Results from an older document version are discarded. SDK/JDK failures appear as configuration warnings rather than syntax errors.

Known compiler messages receive additional explanations and examples. The original SDK diagnostic is preserved. Unknown messages are not translated or guessed.

## Troubleshooting

| Problem | Check |
| --- | --- |
| `.gsp` opens as Groovy Server Pages | Assign `*.gsp` to **Gosu GSP** in **Editor → File Types**. |
| Run cannot find Gosu | SDK home must contain the platform launcher in `bin` and compiler JARs in `lib`; check executable permissions on Unix. |
| Completion works but execution cannot find a class | Add the library/source root to **Script classpath**, even if it is in Project Structure. |
| No completion after a dot | Wait for indexing, verify imports and classpath, and check whether the receiver's type can be inferred. See the feature limits. |
| Compiler checks report a configuration warning | Use the configuration preview, verify JDK/SDK compatibility, and try Gosu 1.18.11. |
| Updated dependency diagnostics seem stale | Save the dependency files; only the active document is supplied as an unsaved snapshot. |
| Plugin does not load | Check the IDE build (262 minimum) and required bundled Java/test-runner plugins. |
| Build cannot download the target IDE | Check connectivity and the configured version; developers can use a local installation as described in CONTRIBUTING.md. |

For colors, use **Editor → Color Scheme → Gosu**. Inferred variable hints can be disabled under **Inlay Hints → Inferred variable types**.
