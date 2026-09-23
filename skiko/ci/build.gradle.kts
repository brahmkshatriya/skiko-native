import org.kohsuke.github.*
import org.jetbrains.compose.internal.publishing.*

val skiko = SkikoProperties(project)
val mavenCentral = MavenCentralProperties(project)
val GITHUB_REPO = "JetBrains/skiko"
val skikoArtifacts = SkikoArtifacts(groupId = skiko.deployGroup)
val skikoSkottieArtifacts = SkikoArtifacts(
    groupId = skiko.deployGroup,
    artifactIdPrefix = "skiko-skottie",
    displayName = "Skiko Skottie",
    pomDescription = "Kotlin Skia Skottie bindings",
)
val skikoGraphiteArtifacts = SkikoArtifacts(
    groupId = skiko.deployGroup,
    artifactIdPrefix = "skiko-graphite",
    displayName = "Skiko Graphite",
    pomDescription = "Kotlin Skia Graphite bindings",
)

val skikoArtifactIds: List<String> =
    listOf(
        skikoArtifacts.commonArtifactId,
        skikoArtifacts.jvmArtifactId,
        skikoArtifacts.jvmRuntimeArtifactIdFor(OS.Windows, Arch.X64),
        skikoArtifacts.jvmRuntimeArtifactIdFor(OS.Windows, Arch.Arm64),
        skikoArtifacts.jvmRuntimeArtifactIdFor(OS.Linux, Arch.X64),
        skikoArtifacts.jvmRuntimeArtifactIdFor(OS.Linux, Arch.Arm64),
        skikoArtifacts.jvmRuntimeArtifactIdFor(OS.MacOS, Arch.X64),
        skikoArtifacts.jvmRuntimeArtifactIdFor(OS.MacOS, Arch.Arm64),
        skikoArtifacts.jvmAdditionalRuntimeArtifactIdFor("angle", OS.Windows, Arch.X64),
        skikoArtifacts.jvmAdditionalRuntimeArtifactIdFor("angle", OS.Windows, Arch.Arm64),
        skikoArtifacts.jsArtifactId,
        skikoArtifacts.wasmArtifactId,
        skikoArtifacts.nativeArtifactIdFor(OS.Linux, Arch.X64),
        skikoArtifacts.nativeArtifactIdFor(OS.Linux, Arch.Arm64),
        skikoArtifacts.nativeArtifactIdFor(OS.MacOS, Arch.Arm64),
        skikoArtifacts.nativeArtifactIdFor(OS.MacOS, Arch.X64),
        skikoArtifacts.nativeArtifactIdFor(OS.IOS, Arch.X64),
        skikoArtifacts.nativeArtifactIdFor(OS.IOS, Arch.Arm64),
        skikoArtifacts.nativeArtifactIdFor(OS.IOS, Arch.Arm64, isUikitSim = true),
        skikoArtifacts.nativeArtifactIdFor(OS.TVOS, Arch.X64),
        skikoArtifacts.nativeArtifactIdFor(OS.TVOS, Arch.Arm64),
        skikoArtifacts.nativeArtifactIdFor(OS.TVOS, Arch.Arm64, isUikitSim = true),
        skikoArtifacts.jvmRuntimeAllArtifactId,

        skikoSkottieArtifacts.commonArtifactId,
        skikoSkottieArtifacts.jvmArtifactId,
        skikoSkottieArtifacts.jvmRuntimeArtifactIdFor(OS.Windows, Arch.X64),
        skikoSkottieArtifacts.jvmRuntimeArtifactIdFor(OS.Windows, Arch.Arm64),
        skikoSkottieArtifacts.jvmRuntimeArtifactIdFor(OS.Linux, Arch.X64),
        skikoSkottieArtifacts.jvmRuntimeArtifactIdFor(OS.Linux, Arch.Arm64),
        skikoSkottieArtifacts.jvmRuntimeArtifactIdFor(OS.MacOS, Arch.X64),
        skikoSkottieArtifacts.jvmRuntimeArtifactIdFor(OS.MacOS, Arch.Arm64),
        skikoSkottieArtifacts.jsArtifactId,
        skikoSkottieArtifacts.wasmArtifactId,
        skikoSkottieArtifacts.nativeArtifactIdFor(OS.Linux, Arch.X64),
        skikoSkottieArtifacts.nativeArtifactIdFor(OS.Linux, Arch.Arm64),
        skikoSkottieArtifacts.nativeArtifactIdFor(OS.MacOS, Arch.Arm64),
        skikoSkottieArtifacts.nativeArtifactIdFor(OS.MacOS, Arch.X64),
        skikoSkottieArtifacts.nativeArtifactIdFor(OS.IOS, Arch.X64),
        skikoSkottieArtifacts.nativeArtifactIdFor(OS.IOS, Arch.Arm64),
        skikoSkottieArtifacts.nativeArtifactIdFor(OS.IOS, Arch.Arm64, isUikitSim = true),
        skikoSkottieArtifacts.nativeArtifactIdFor(OS.TVOS, Arch.X64),
        skikoSkottieArtifacts.nativeArtifactIdFor(OS.TVOS, Arch.Arm64),
        skikoSkottieArtifacts.nativeArtifactIdFor(OS.TVOS, Arch.Arm64, isUikitSim = true),
        skikoSkottieArtifacts.jvmRuntimeAllArtifactId,

        skikoGraphiteArtifacts.commonArtifactId,
        skikoGraphiteArtifacts.jvmArtifactId,
        skikoGraphiteArtifacts.jvmRuntimeArtifactIdFor(OS.Windows, Arch.X64),
        skikoGraphiteArtifacts.jvmRuntimeArtifactIdFor(OS.Windows, Arch.Arm64),
        skikoGraphiteArtifacts.jvmRuntimeArtifactIdFor(OS.Linux, Arch.X64),
        skikoGraphiteArtifacts.jvmRuntimeArtifactIdFor(OS.Linux, Arch.Arm64),
        skikoGraphiteArtifacts.jvmRuntimeArtifactIdFor(OS.MacOS, Arch.X64),
        skikoGraphiteArtifacts.jvmRuntimeArtifactIdFor(OS.MacOS, Arch.Arm64),
        skikoGraphiteArtifacts.jvmRuntimeAllArtifactId,
        skikoGraphiteArtifacts.nativeArtifactIdFor(OS.MacOS, Arch.Arm64),
        skikoGraphiteArtifacts.nativeArtifactIdFor(OS.MacOS, Arch.X64),
        skikoGraphiteArtifacts.nativeArtifactIdFor(OS.IOS, Arch.X64),
        skikoGraphiteArtifacts.nativeArtifactIdFor(OS.IOS, Arch.Arm64),
        skikoGraphiteArtifacts.nativeArtifactIdFor(OS.IOS, Arch.Arm64, isUikitSim = true),
        skikoGraphiteArtifacts.nativeArtifactIdFor(OS.TVOS, Arch.X64),
        skikoGraphiteArtifacts.nativeArtifactIdFor(OS.TVOS, Arch.Arm64),
        skikoGraphiteArtifacts.nativeArtifactIdFor(OS.TVOS, Arch.Arm64, isUikitSim = true),
)

val downloadSkikoArtifactsFromComposeDev by tasks.registering(DownloadFromSpaceMavenRepoTask::class) {
    modulesToDownload.set(skikoMavenModules(skiko.deployVersion))
    spaceRepoUrl.set("https://packages.jetbrains.team/maven/p/cmp/dev")
}

val createGithubRelease by tasks.registering {
    dependsOn(downloadSkikoArtifactsFromComposeDev)

    doLast {
        check(skiko.isRelease) { "This task should only be called for releases!" }
        val gh = connectToGitHub()
        val githubVersion = skiko.releaseGithubVersion
        val githubCommit = skiko.releaseGithubCommit
        val githubPrerelease = skiko.releaseGithubPrerelease
        val repo = gh.getRepository(GITHUB_REPO)
        val release = repo.createRelease("v$githubVersion")
            .name("Version $githubVersion")
            .generateReleaseNotes(true)
            .prerelease(githubPrerelease)
            .commitish(githubCommit)
            .create()

        val artifactsToUpload = skikoMavenModules(skiko.deployVersion).get().map { module ->
            val baseName = "${module.artifactId}-${module.version}"
            val pom = module.localDir.resolve("$baseName.pom")
            val regex = "<packaging>([a-zA-Z0-9]+)</packaging>".toRegex()
            val ext = regex.find(pom.readText())?.groupValues?.getOrNull(1) ?: "jar"
            module.localDir.resolve("$baseName.$ext").also {
                check(it.exists()) {
                    "'$it' does not exist"
                }
            }
        }
        for (artifact in artifactsToUpload) {
            logger.info("Uploading '$artifact'")
            release.uploadAsset(artifact, "application/zip")
        }
    }
}

val deleteGithubRelease by tasks.registering {
    doLast {
        val gh = connectToGitHub()
        val repo = gh.getRepository(GITHUB_REPO)
        repo.listReleases().firstOrNull { it.tagName == "v${skiko.releaseGithubVersion}" }?.delete()
    }
}

val uploadSkikoArtifactsToMavenCentral by tasks.registering(UploadToSonatypeTask::class) {
    dependsOn(downloadSkikoArtifactsFromComposeDev)

    deployName.set("Skiko ${skiko.deployVersion}")
    modulesToUpload.set(skikoMavenModules(skiko.deployVersion))

    user.set(mavenCentral.user)
    password.set(mavenCentral.password)
    publishAfterUploading.set(mavenCentral.publishAfterUploading)
}

/** Publishes only the KMP root and the Linux x64 native KLIB built by this checkout. */
val uploadNativeSkikoArtifactsToMavenCentral by tasks.registering(UploadToSonatypeTask::class) {
    dependsOn(
        ":publishKotlinMultiplatformPublicationToBuildRepoRepository",
        ":publishLinuxX64PublicationToBuildRepoRepository",
    )

    deployName.set("Skiko Native ${skiko.deployVersion}")
    modulesToUpload.set(
        provider {
            listOf(skikoArtifacts.commonArtifactId, skikoArtifacts.nativeArtifactIdFor(OS.Linux, Arch.X64))
                .map { artifactId ->
                    ModuleToUpload(
                        groupId = skiko.deployGroup,
                        artifactId = artifactId,
                        version = skiko.deployVersion,
                        localDir =
                            rootProject.layout.buildDirectory
                                .dir("repo/${skiko.deployGroup.replace('.', '/')}/$artifactId/${skiko.deployVersion}")
                                .get()
                                .asFile,
                    )
                }
        }
    )

    user.set(mavenCentral.user)
    password.set(mavenCentral.password)
    publishAfterUploading.set(true)
}

/** Publishes only the Windows x64 native KLIB built by this checkout. */
val uploadWindowsNativeSkikoArtifactToMavenCentral by tasks.registering(UploadToSonatypeTask::class) {
    dependsOn(":publishMingwX64PublicationToBuildRepoRepository")

    deployName.set("Skiko Windows Native ${skiko.deployVersion}")
    modulesToUpload.set(
        provider {
            val artifactId = skikoArtifacts.nativeArtifactIdFor(OS.Windows, Arch.X64)
            listOf(
                ModuleToUpload(
                    groupId = skiko.deployGroup,
                    artifactId = artifactId,
                    version = skiko.deployVersion,
                    localDir =
                        rootProject.layout.buildDirectory
                            .dir("repo/${skiko.deployGroup.replace('.', '/')}/$artifactId/${skiko.deployVersion}")
                            .get()
                            .asFile,
                )
            )
        }
    )

    user.set(mavenCentral.user)
    password.set(mavenCentral.password)
    publishAfterUploading.set(true)
}

/** Uploads the Linux arm64 native KLIB previously built by the cross-compilation container. */
val uploadLinuxArm64NativeSkikoArtifactToMavenCentral by tasks.registering(UploadToSonatypeTask::class) {
    deployName.set("Skiko Linux Arm64 Native ${skiko.deployVersion}")
    modulesToUpload.set(
        provider {
            val artifactId = skikoArtifacts.nativeArtifactIdFor(OS.Linux, Arch.Arm64)
            listOf(
                ModuleToUpload(
                    groupId = skiko.deployGroup,
                    artifactId = artifactId,
                    version = skiko.deployVersion,
                    localDir =
                        rootProject.layout.buildDirectory
                            .dir("repo/${skiko.deployGroup.replace('.', '/')}/$artifactId/${skiko.deployVersion}")
                            .get()
                            .asFile,
                )
            )
        }
    )

    user.set(mavenCentral.user)
    password.set(mavenCentral.password)
    publishAfterUploading.set(true)

    doFirst {
        check(skiko.isRelease) {
            "Linux arm64 artifacts can only be uploaded with -Pdeploy.release=true"
        }
    }
}

/**
 * Uploads the native-only KMP root and repository assembled from all desktop release jobs.
 * This task intentionally has no publication-task dependencies because no single host builds all
 * desktop native artifacts.
 */
val uploadDesktopNativeSkikoArtifactsToMavenCentral by tasks.registering(UploadToSonatypeTask::class) {
    deployName.set("Skiko Desktop Native ${skiko.deployVersion}")
    modulesToUpload.set(
        provider {
            listOf(
                skikoArtifacts.commonArtifactId,
                skikoArtifacts.nativeArtifactIdFor(OS.Linux, Arch.X64),
                skikoArtifacts.nativeArtifactIdFor(OS.Linux, Arch.Arm64),
                skikoArtifacts.nativeArtifactIdFor(OS.Windows, Arch.X64),
                skikoArtifacts.nativeArtifactIdFor(OS.MacOS, Arch.X64),
                skikoArtifacts.nativeArtifactIdFor(OS.MacOS, Arch.Arm64),
            ).map { artifactId ->
                ModuleToUpload(
                    groupId = skiko.deployGroup,
                    artifactId = artifactId,
                    version = skiko.deployVersion,
                    localDir =
                        rootProject.layout.buildDirectory
                            .dir("repo/${skiko.deployGroup.replace('.', '/')}/$artifactId/${skiko.deployVersion}")
                            .get()
                            .asFile,
                )
            }
        }
    )

    user.set(mavenCentral.user)
    password.set(mavenCentral.password)
    publishAfterUploading.set(true)

    doFirst {
        check(skiko.isRelease) {
            "Desktop native artifacts can only be uploaded with -Pdeploy.release=true"
        }
    }
}

fun Project.skikoMavenModules(version: String): Provider<List<ModuleToUpload>> =
    provider {
        val artifactsDir = layout.buildDirectory.dir("skiko-artifacts").get().asFile

        skikoArtifactIds.map { artifactId ->
            val skikoGroupId = skiko.deployGroup
            ModuleToUpload(
                groupId = skikoGroupId,
                artifactId = artifactId,
                version = version,
                localDir = artifactsDir.resolve("$version/$skikoGroupId/$artifactId")
            )
        }
    }

fun connectToGitHub() =
    GitHubBuilder()
        .withOAuthToken(System.getenv("SKIKO_GH_RELEASE_TOKEN"))
        .build()
