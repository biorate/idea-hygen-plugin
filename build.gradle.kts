plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "com.biorate"
version = "0.4.0"

val ideaHome = "/home/llevkin/bin/idea-IU-253.30387.90"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // The JetBrains repository host (www.jetbrains.com/intellij-repository) is not
        // resolvable in this environment, so the local IDE installation is used as the
        // platform source. See design.md D1.
        local(ideaHome)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testImplementation(files("$ideaHome/lib/testFramework.jar"))
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
        }
    }
}

tasks {
    test {
        // The local IDE's bin/idea64.vmoptions contains -javaagent:ja-netfilter.jar, which
        // IPG forwards to the test JVM and which breaks the platform test framework.
        // The provider is dropped; the vmoptions are re-applied here without javaagents.
        jvmArgumentProviders.removeIf {
            it.javaClass.name.endsWith("IntelliJPlatformArgumentProvider")
        }

        val vmOptions = file("$ideaHome/bin/idea64.vmoptions").readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("-javaagent") }
        jvmArgs(vmOptions)
        // The local IDE launcher supplies these at runtime; the test JVM needs them
        // explicitly once IPG's argument provider is removed.
        jvmArgs(
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
            "--add-opens=java.base/java.text=ALL-UNNAMED",
            "--add-opens=java.base/java.time=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
            "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
            "--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED",
            "--add-exports=java.base/sun.nio.fs=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED",
        )

        val testRoot = layout.buildDirectory.dir("idea-test").get().asFile
        systemProperty("idea.home.path", ideaHome)
        systemProperty("idea.force.use.core.classloader", "true")
        systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
        // The bundled TextMate plugin claims `.ejs.t` files through its own overrider in
        // the test IDE, so the whitelist keeps it (and other unrelated bundled plugins) out
        // and file types stay ours. The bundled "JavaScript" plugin is listed because
        // HygenHostDetection resolves the JavaScript/TypeScript languages through the
        // platform file-type registry.
        systemProperty("idea.load.plugins.id", "com.biorate.idea-hygen-plugin,JavaScript")
    }
}
