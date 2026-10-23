# Features and limits

Details of what the plugin supports and where it still falls short.

## Editing and structure

The lexer recognizes Gosu keywords, comments, numeric and string literals, interpolation, annotations, operators, and named-argument/property labels. Contextual names such as `application`, `session`, `request`, and `execution` remain identifiers. Semantic highlighting distinguishes declarations, calls, parameters, locals, and fields.

A tolerant parser builds a usable PSI tree while code is incomplete. Structure View includes classes, functions, parameters, fields, variables, imports, enum constants, and separate getter/setter entries. Folding, breadcrumbs, brace/quote matching, commenting, TODO indexing, live templates, and word-selection expansion support everyday editing.

Formatting uses two-space indentation and conservative spacing rules. It is not a complete style engine or a source-rewriting formatter.

## Completion and type inference

Basic completion includes keywords, local declarations, and members of recognized receiver types. It supports Gosu classes, public Java members, static/instance separation, and Java getters exposed as Gosu properties. Method insertion adds call parentheses.

Type inference handles explicit declarations, constructors, literals, some method results and call chains, Java generic substitutions, simple Gosu generic classes, collection indexing, loop elements, and homogeneous collection literals. Known argument types help distinguish overloads. Java generic methods and nested Java generics are supported in common cases.

Block parameters have a local scope. Unambiguous Java functional-interface arguments can supply their types, for example with `List<String>.forEach`. General inference for Gosu blocks, enhancement blocks, complex generic constraints, custom type loaders, and dynamic types remains incomplete. Ambiguous overloads are not resolved to an arbitrary target.

Enhancement methods and properties are available from the current package, imports, and SDK `gw.lang.enhancements`. Simple enhancement generic parameters can propagate return types. Completion also offers applicable unimported enhancements and adds `uses` when selected.

Class completion after at least two typed letters can add `uses`; **Alt+Enter** on an unresolved class name offers matching imports. Conflicting short names are inserted fully qualified. Property-initializer completion suggests writable, not-yet-used properties after `:` in `new Person() { :… }`. Named-argument completion uses Gosu parameter names and does not guess names from Java bytecode.

**Type-Matching completion** filters expressions by the expected type in explicit initializers, assignments, return expressions, and call arguments. It includes Java assignability, Gosu subtypes, and simple literals. It does not synthesize call chains or fully match inherited Gosu generics. Unknown contexts fall back to ordinary completion.

## Documentation and hints

Parameter Info shows recognized overloads and the active argument, accounting for commas inside strings and nested calls. Quick Documentation shows declarations, Gosu documentation comments, source files, and JavaDoc when sources/documentation are available.

Completion and documentation identify a method's declaring type and origin: Java, Gosu, a base class, or an enhancement. Inferred-type hints avoid labeling unknown types. Type explanations trace supported cases through annotations, literals, constructors, variables, method results, and block/loop context.

Learning aids include keyword/operator documentation, type explanations, and compiler-message explanations, with short examples for `uses`, `enhancement`, properties, blocks, null operators, casts, loops, and resource handling.

## Navigation

Go to Declaration resolves local symbols, type names, imports, and recognized members across Gosu files and Java dependencies. Resolution considers the current package, explicit imports, and wildcard imports. Go to Symbol uses stub indexes.

Type Hierarchy displays Gosu subtypes and Gosu/Java supertypes. Inheritance markers and Go to Implementation navigate recognized overrides, interface implementations, and property accessors. Indexing must finish before these results are complete.

Call Hierarchy provides callers and callees for resolved Gosu calls, including overloads and calls to Java methods. Scripts appear as separate nodes; recursive expansion is bounded. The callers search covers Gosu source, not every JVM language or runtime dispatch target. Unresolved dynamic calls are omitted.

## Refactoring and generation

Rename, Find Usages, Safe Delete, and read/write highlighting use resolved references; they are not a complete cross-language or reflection-aware analysis. Optimize Imports removes unused/duplicate `uses`, sorts uncomplicated import blocks, and preserves recognized enhancement imports.

| Action | Supported scope | Important limits |
| --- | --- | --- |
| Extract Variable | An entire local `var` initializer or entire return expression; preserves evaluation site and explicit type | Standalone instruction lines only; no partial expressions, field initializers, or blocks/lambdas |
| Extract Method | Whole initializer/return expressions, or selected simple declaration/call statements; inferred inputs and one output | No control-flow statements, nested blocks, input mutation, multiple outputs, or unknown types |
| Change Signature | Rename/retype/add/remove/reorder parameters and update recognized Gosu calls and parameter references | Overrides, overloads, default parameters, interpolation, and unresolved uses require manual work; removing used parameters and reordering/removing effectful arguments is blocked |
| Move Class | A single top-level type in a matching `Name.gs` file; update package, qualified uses, and imports | Requires a matching package directory; checks destination collisions and `internal` access |
| Move Method | Public static methods between ordinary Gosu classes in the same package | Rejects dependence on source-class context; target imports must already suffice; instance methods are unsupported |

Refactoring changes support Undo. Change Signature and Move show affected files before applying changes. Calls through reflection, other languages, external libraries, and text configuration are not migrated. Changing a parameter type may require manual changes to the body, surfaced by SDK diagnostics.

Generation actions implement missing methods, offer base methods to override, add explicit variable types, create a function from a supported unresolved standalone call, and generate property accessors. Override generation considers Gosu and Java supertypes and basic Java interface generic substitutions. It skips final/static/private methods, existing implementations, and Java methods with their own type parameters. Generated method bodies throw `UnsupportedOperationException` until implemented; final fields receive only a getter.

## Inspections and null behavior

Inspections detect unused variables/parameters and imports, offer missing imports, and analyze local null flow. The data-flow inspection tracks assignments, `if/else` branches, guard clauses ending in `return`/`throw`, null comparisons, fallback expressions, and code after a terminated path.

It is local to a function or script. Unknown calls and property reads invalidate facts that may no longer hold. Loops, exception flow, compound conditions, fields, blocks, and interpolation are not fully analyzed. The absence of a warning does not prove null safety.

Null-related intentions insert `?.` or wrap a value in `(value ?: fallback)`. These change behavior deliberately. Gosu's null-safe call can produce a primitive default such as `0` from `name?.length()`, and does not automatically protect every subsequent call in a chain. SDK diagnostics check type compatibility of fallback values.

## Execution and compiler integration

Script configurations and the convention-based test runner use project SDK/JDK/classpath settings. Console locations link back to source. Optional compiler checks use a bounded external SDK process and accept unsaved content from the active document. See [Getting started](GETTING_STARTED.md) for lifecycle details and setup.

There is no debugger, full JUnit/`gw.test.TestClass` runner, or guarantee of compatibility with proprietary Gosu environments and custom type systems.
