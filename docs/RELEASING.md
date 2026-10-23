# Releases

## Repository setup

Repository name: `idea-gosu-plugin` (https://github.com/marcinkleczek/idea-gosu-plugin).

About description:

> Gosu support for IntelliJ IDEA: code completion, navigation, refactoring, and script execution for .gsp, .gs, and .gsx files.

Topics: `gosu`, `intellij-idea`, `intellij-plugin`, `jvm`, `java`, `developer-tools`.

The project is licensed under MIT (`LICENSE`). Before the first push, review the files to be committed for local paths, credentials, and private examples. Build output and IDE settings are covered by `.gitignore`; the Gradle Wrapper belongs in the repository.

Create an empty GitHub repository. Initialize the local repository if needed:

```sh
git init -b main
```

Stage and review the files:

```sh
git add .
git diff --cached --stat
git diff --cached --check
git status --short
```

Commit, add the remote URL provided by GitHub, and push `main`.

The **Build and test** workflow runs on pushes to `main`, pull requests, and manual dispatch. It uses Linux and JDK 21, runs tests, checks the plugin structure, and uploads the ZIP and test reports. SDK tests need to be run separately: CI does not install Gosu.

## Build a release

Set the version explicitly. For example:

```sh
./gradlew clean test buildPlugin verifyPluginStructure -PpluginVersion=0.3.0
./gradlew test -PpluginVersion=0.3.0 -PgosuSdkHome=/path/to/gosu-1.18.11
```

Without `pluginVersion`, builds use `0.2.<yyyyMMddHHmmss>`. Check the test report at `build/reports/tests/test/index.html`, including whether any SDK tests were skipped. The plugin ZIP is in `build/distributions/`.

`verifyPluginStructure` checks packaging. Use the JetBrains Plugin Verifier separately when checking compatibility with other IDE versions.

## Publish

1. Install the ZIP and try the bundled examples: completion, navigation, compiler errors, script execution, and tests.
2. Move the changelog's `Unreleased` entries into a dated version section.
3. Commit the release changes and tag the commit, for example `v0.3.0`.
4. Create a draft GitHub Release for the tag and attach the plugin ZIP from `build/distributions/`.
5. Add the tested IDE/JDK/SDK versions and release notes, then publish.

Attach the plugin ZIP, not GitHub's automatically generated source archive. Releases are published manually; CI only uploads build artifacts. JetBrains Marketplace publishing is separate.

## First-release notes

Use this as a starting point, adding the versions tested for the release:

> First release of Gosu GSP Support for IntelliJ IDEA.
>
> Supports scripts, classes, and enhancements, with Gosu/Java completion, navigation, refactoring, and script execution. A local Gosu SDK enables compiler diagnostics in the editor and the built-in test runner.
>
> Install the attached ZIP through Settings → Plugins → Install Plugin from Disk. Then configure the SDK, JDK, and classpath under Languages & Frameworks → Gosu.
>
> Advanced type inference and refactoring have known limits; see the feature reference. There is no debugger yet.

## References

- [GitHub Actions: building with Gradle](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle)
- [IntelliJ Platform Gradle Plugin tasks](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html)
