# Project guidance

This project implements Gosu support for IntelliJ IDEA. Read [CONTRIBUTING.md](CONTRIBUTING.md) for development commands and implementation expectations, and [docs/FEATURES.md](docs/FEATURES.md) for the supported feature scope.

The implementation now includes cross-file indexes, Java/Gosu member resolution, compiler diagnostics, hierarchies, and restricted refactorings. Earlier single-file-only design notes are obsolete.

Use JDK 21 and the Gradle Wrapper. Run tests appropriate to changes; compiler/test-bridge changes also require SDK-enabled checks. Keep the parser tolerant, formatting conservative, and ambiguous resolution explicit. Do not broaden compatibility claims without verification.
