import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  id("java")
  id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "gosu.gsp"
// Development builds use a timestamp; releases can set -PpluginVersion=0.3.0.
val buildStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
val releaseVersion = providers.gradleProperty("pluginVersion").orNull
require(releaseVersion == null || releaseVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?"))) {
  "pluginVersion must have the form 0.3.0 or 0.3.0-rc.1"
}
version = releaseVersion ?: "0.2.$buildStamp"

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    bundledPlugin("com.intellij.java")
    bundledPlugin("intellij.testRunner.plugin")
    testFramework(TestFrameworkType.Platform)
    testFramework(TestFrameworkType.Plugin.Java)
    // Target: current IDEA (Community and Ultimate share the platform since 2025.3).
    // Override with: ./gradlew -PplatformVersion=2025.3 buildPlugin
    val localPlatformPath = providers.gradleProperty("localPlatformPath").orNull
    if (localPlatformPath != null) {
      local(localPlatformPath)
    } else {
      intellijIdea(providers.gradleProperty("platformVersion").getOrElse("2026.2.1"))
    }
  }

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.0")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.0")
}

// The isolated compiler worker also runs with users' JDK 17 installations.
val compilerBridge = sourceSets.create("compilerBridge")
tasks.named<JavaCompile>(compilerBridge.compileJavaTaskName) {
  options.release.set(17)
}
tasks.jar { from(compilerBridge.output.classesDirs) }
sourceSets.test { runtimeClasspath += compilerBridge.output.classesDirs }


intellijPlatform {
  pluginConfiguration {
    name = "Gosu GSP Support"
    version = project.version.toString()
    // Keep the full description in src/main/resources/META-INF/plugin.xml.
    ideaVersion {
      // Build 262 == IDEA 2026.2. No upper bound: keep loading on newer IDEs.
      sinceBuild = "262"
    }
  }
}

tasks {
  test {
    useJUnitPlatform()
    systemProperty("gosu.test.jdk", System.getProperty("java.home"))
    providers.gradleProperty("gosuSdkHome").orNull?.let { systemProperty("gosu.test.sdk", it) }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}
