# Changelog

## Unreleased

First public release in preparation.

### Editor support

- Highlighting, formatting, folding, and Structure View for `.gsp`, `.gs`, and `.gsx` files.
- Gosu and Java completion, enhancement discovery, imports, parameter info, documentation, and type hints.
- Navigation between files, inheritance markers, and type and call hierarchies.
- Extract Variable/Method, Change Signature, Move, and method/property generation. See the [feature reference](docs/FEATURES.md) for supported cases.
- Unused-code inspections, null analysis, and compiler diagnostics through a local Gosu SDK.
- Script execution and a test runner for classes with `test…` methods.

### Build and documentation

- English documentation and GitHub issue/PR templates.
- CI workflow that runs tests and builds the plugin ZIP.
- Release versions can be set with `-PpluginVersion`; development builds use timestamps.
- MIT license, code of conduct, security policy, and architecture overview.
- All editor messages, dialogs, and learning aids are in English.

Debugging and JUnit/`gw.test.TestClass` integration are not included.
