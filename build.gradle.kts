import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    java
    `maven-publish`
}

group = "io.github.team-sneakymouse"

// Keep build outputs on a native FS when the checkout sits on FUSE/NTFS (e.g. /mnt/files).
layout.buildDirectory.set(
    file("${System.getProperty("user.home")}/.cache/${rootProject.name}-gradle-build"),
)

// Requires a git checkout with at least one commit (CI and local clones).
version =
    providers.exec {
        workingDir(rootDir)
        commandLine("git", "show", "-s", "--format=%ct:%h", "--abbrev=12", "HEAD")
    }.standardOutput.asText.map { commit ->
        val (timestamp, hash) = commit.trim().split(":", limit = 2)
        val date =
            DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochSecond(timestamp.toLong()))
        "$date-$hash"
    }.get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val velocityApi = "4.2.0"

/**
 * On Windows, a bare `bash` resolves to WSL's System32\bash.exe before PATH is searched.
 * Use Git for Windows' bash (located via `git --exec-path`) when it exists.
 */
fun bashExecutable(): String {
    if (!System.getProperty("os.name").startsWith("Windows")) return "bash"
    val gitExecPath =
        providers.exec { commandLine("git", "--exec-path") }.standardOutput.asText.get().trim()
    val gitBash = file(gitExecPath).resolve("../../../bin/bash.exe").normalize()
    return if (gitBash.isFile) gitBash.absolutePath else "bash"
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:$velocityApi")
    annotationProcessor("com.velocitypowered:velocity-api:$velocityApi")

    testImplementation("com.velocitypowered:velocity-api:$velocityApi")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
        options.release.set(25)
    }

    // Velocity reads the plugin version from the annotation; feed it the git-derived version.
    val generateBuildInfo by registering {
        val outDir = layout.buildDirectory.dir("generated/sources/buildinfo")
        val ver = project.version.toString()
        inputs.property("version", ver)
        outputs.dir(outDir)
        doLast {
            val file = outDir.get().file("com/sneakyreconnect/BuildInfo.java").asFile
            file.parentFile.mkdirs()
            file.writeText(
                "package com.sneakyreconnect;\n\n" +
                    "final class BuildInfo {\n" +
                    "    static final String VERSION = \"$ver\";\n" +
                    "    private BuildInfo() {}\n" +
                    "}\n",
            )
        }
    }
    sourceSets.main { java.srcDir(generateBuildInfo) }

    jar {
        archiveBaseName.set(rootProject.name)
        archiveFileName.set("${rootProject.name}-${project.version}.jar")
    }

    test {
        useJUnitPlatform()
    }

    val publishPreflight by registering(Exec::class) {
        group = "verification"
        description = "Catch sneakyrp publish footguns (gradlew mode, wrapper jar, workflow_dispatch)."
        workingDir = rootDir
        commandLine(bashExecutable(), "scripts/publish-preflight.sh", rootDir.absolutePath)
    }

    check {
        dependsOn(publishPreflight)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "sneakyreconnect"
            from(components["java"])
            pom {
                name.set("SneakyReconnect")
                description.set("Velocity plugin that sends players back to a server after it restarts.")
                url.set("https://github.com/Team-Sneakymouse/SneakyReconnect")
                // Mother Mouse only deploys velocity-plugin artifacts to proxies, via /plugins update.
                properties.put("sneakyrp.deployment.type", "velocity-plugin")
                licenses {
                    license {
                        name.set("GNU General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("team-sneakymouse")
                        name.set("Team Sneakymouse")
                        url.set("https://github.com/Team-Sneakymouse")
                    }
                }
                scm {
                    url.set("https://github.com/Team-Sneakymouse/SneakyReconnect")
                    connection.set("scm:git:https://github.com/Team-Sneakymouse/SneakyReconnect.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sneakyrp"
            url = uri("https://maven.sneakyrp.com/releases")
            credentials(PasswordCredentials::class)
            authentication {
                create<org.gradle.authentication.http.BasicAuthentication>("basic")
            }
        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(tasks.check)
}
