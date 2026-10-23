# Architecture

How the plugin is put together, and the design rules that keep it predictable. Read [FEATURES.md](FEATURES.md) for what each capability supports and [CONTRIBUTING.md](../CONTRIBUTING.md) for build and test commands.

## Overview

The plugin is a standard IntelliJ Platform language plugin written in Java 21. It registers the Gosu language and file type, builds a PSI tree with a tolerant parser, and layers editor features on top of that tree. All analysis runs inside the IDE process, except two deliberately isolated workers:

- the **compiler worker**, which parses and type-checks one document with the user's Gosu SDK;
- the **test worker**, which runs convention-based Gosu tests.

Both are launched as separate `java` processes with the user's JDK and SDK JARs, and both get their entry point from `src/compilerBridge`, a tiny source set compiled for Java 17 so it also runs on older JDKs that users may pair with their SDK.

```
IDE process                                     separate JVM (user JDK + SDK JARs)
┌───────────────────────────────────────┐       ┌───────────────────────────────┐
│ lexer → parser → PSI + stubs          │       │ compilerBridge                │
│   ↓                                   │       │  GosuCompilerBridge  (check)  │
│ resolve (types, members, imports)     │──────▶│  GosuTestBridge      (tests)  │
│   ↓                                   │ stdin/│                               │
│ completion · navigation · hierarchy   │ argv  │ no plugin code besides these  │
│ inspections · intentions · refactor   │◀──────│ two classes                   │
│ format · structure · highlight        │ stdout└───────────────────────────────┘
│ run · testing · compiler · settings   │
└───────────────────────────────────────┘
```

## Packages

All plugin code lives under `gosu.gsp` in `src/main/java`.

| Package | Role |
| --- | --- |
| `lexer` | Hand-written lexer and token types. Contextual names such as `application` or `request` stay identifiers. |
| `psi` | Tolerant parser, PSI element types, named declarations, references, property accessors, and the element factory used by rename. |
| `stubs` | Stub elements and indexes for classes, functions, enhancements, and supertypes. Only names are stored; members resolve from PSI. |
| `resolve` | Type lookup and inference: `GosuTypes` (class lookup through imports and packages), `GosuMembers` (member and type inference), `GosuCallTypes` (overload selection), `GosuEnhancements`, `GosuLambdaTypes`, `GosuSignatures`, and `GosuSource` (cached token view of a file). |
| `completion` | Completion contributors, auto-import, parameter info, inlay hints, documentation, and the explanations for types and keywords. |
| `highlight`, `structure`, `format`, `editor` | Syntax and semantic highlighting, Structure View, folding, breadcrumbs, formatter, and small editor services such as brace matching, commenting, quote handling, and import optimization. |
| `hierarchy` | Type and call hierarchies, inheritance gutter markers, and the definitions search used by Go to Implementation. |
| `inspection`, `intentions` | Unused code and import inspections, the local null data-flow analysis, and Alt+Enter actions for imports, null handling, type explanation, and code generation. |
| `refactor` | Extract Variable/Method, Change Signature, and Move, all built on `GosuEditPlan`. |
| `settings` | Project-level SDK, JDK, and classpath settings, the configuration report, and the library roots provider that exposes SDK and classpath JARs to IntelliJ's Java index. |
| `run`, `testing` | Run configurations for scripts and tests, console filters that turn diagnostics into links, and the launcher that resolves the SDK executable. |
| `compiler` | The external annotator that drives the compiler worker, the worker runner, and the learning hints for known SDK messages. |

Extension points are registered in `src/main/resources/META-INF/plugin.xml`. The plugin depends on the bundled Java plugin for Java PSI and the bundled test-runner plugin for the test console.

## Two views of a file

Most features read the file in two ways at once:

1. **PSI tree** for declarations, references, scopes, rename, and indexes. The parser never fails; incomplete code still yields a usable tree.
2. **Token list** from `GosuSource.tokens(file)`, cached per file modification, for everything that is easier to express positionally: finding the initializer of a `var`, the receiver before a dot, the argument list of a call, or the braces of a class body.

Type inference, refactorings, and the data-flow inspection combine both. When adding a feature, prefer PSI for anything that resolves names, and the token view for local syntactic shape.

## Resolution and type inference

`GosuTypes.find` looks a type up by its short or qualified name, honoring the current package, explicit `uses` imports, and wildcard imports, in Gosu source first and then in Java classes visible through the project and the SDK library roots. `GosuMembers.infer` derives a type for a declaration or expression from explicit annotations, literals, constructors, member chains, collection indexing, loop elements, and Java generic substitution.

The guiding rule is **explicit ambiguity**: when the plugin cannot determine a type or pick an overload, it returns unknown rather than guessing. `GosuCallTypes` never lets an unknown argument break a tie, and `GosuLambdaTypes` only supplies block parameter types when exactly one Java functional-interface overload matches. Completion, hints, and refactorings all treat unknown as "do less", never as "assume".

Enhancements are indexed from source and applied by target type; generic bindings on the enhancement target are carried into return types.

## Refactorings

Every refactoring produces a `GosuEditPlan`: a list of text replacements per file, validated against the current document text before anything is written. The plan rejects overlapping edits and stale documents, shows the affected files to the user, and applies all edits in one write command so Undo reverts the whole refactoring.

Refactorings reject unsupported input with a specific message instead of producing a partial result. The supported scope of each action is listed in [FEATURES.md](FEATURES.md#refactoring-and-generation). Evaluation order and side effects must be preserved, which is why Change Signature refuses to reorder effectful arguments.

## Compiler checks

`GosuCompilerAnnotator` is an `ExternalAnnotator`. On the read thread it snapshots the current document text and the configured paths. Off the UI thread, `GosuCompilerRunner` writes the snapshot into a temporary directory laid out by package, launches the worker with the SDK JARs, the bridge JAR, and the script classpath, and parses the diagnostics it prints. Results that belong to an older document revision are discarded.

The worker is bounded: a 20-second timeout, a 256 MB heap, and process destruction on cancellation. `GosuCompilerBridge` only invokes the SDK parser and type checker; it never evaluates the program or runs generated bytecode. SDK or JDK configuration failures surface as a single configuration warning, not as syntax errors.

`GosuDiagnosticExplanation` attaches a cause, a checklist, and a syntax example to a handful of well-known SDK messages. The original message is always shown first, and unknown messages are passed through unchanged.

## Running scripts and tests

Script runs use the SDK's own launcher with the configured JDK and classpath; the same `GosuClasspath` entries feed both editor indexing and the launcher's `-classpath`. `GosuConsoleFilter` turns compiler locations and stack-trace locations in the console into clickable links, tracking the current file from the compiler's header lines.

The test runner is intentionally small. `GosuTestBridge` loads the test class with the SDK, instantiates it once per `test…` method, calls optional `beforeTest`/`afterTest`, and streams results in the test-console protocol. It rejects JUnit and `gw.test.TestClass` bases rather than running them with an incomplete lifecycle.

## Indexing and stubs

Stub indexes record class, function, enhancement, and supertype names across the project so that Go to Symbol, type hierarchies, and enhancement discovery work without opening files. `GosuStubVersion` must be bumped whenever the stub format changes, which forces a reindex.

## Tests

`src/test` holds JUnit 5 unit tests for the lexer, parser, formatter, console parsing, and launcher logic, plus JUnit 4 IntelliJ fixture tests (run through the Vintage engine) that exercise completion, resolution, inspections, and refactorings on real PSI. Tests that need a Gosu SDK are conditional on the `gosu.test.sdk` system property, set from `-PgosuSdkHome`; CI runs without an SDK, so those checks must be run locally before touching the compiler or test bridges.

## Design rules

- Keep the parser tolerant and the formatter conservative.
- Return unknown instead of guessing a type, a target, or an overload.
- Make refactorings all-or-nothing and reversible.
- Keep worker processes bounded, cancellable, and free of program evaluation except where the user explicitly runs code.
- Prefer small, direct code over abstractions; most classes are final utility holders with a single responsibility.
