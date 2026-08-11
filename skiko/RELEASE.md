# Release processes

## Teamcity

To publish a version of Skiko:
1. Open [Publish release](https://buildserver.labs.intellij.net/buildConfiguration/Skiko_PublishRelease).
2. Click "Run" button.
3. Specify the desired version in "Skiko Release Version" text field on the "Parameters" tab.
4. Choose the desired branch and commit on the "Changes" tab.
5. Optionally, you can check "Put the build to the queue top" option in the "General" tab to speed up a deployment
   (please be mindful about it!).

## Publishing

##### Publish JVM target to Maven local
```bash
./gradlew publishToMavenLocal
```

##### Publish all targets to Maven Local
```bash
./gradlew publishToMavenLocal -Pskiko.native.enabled=true -Pskiko.wasm.enabled=true -Pskiko.android.enabled=true
```
Use flag `-Pskiko.debug=true` to build with debug build type.
Artifact will be published to mavenLocal with postfix "+debug", for example "0.0.0-SNAPSHOT+debug".

##### Publish to `build/repo` directory
```bash
./gradlew publishToBuildRepo
```

##### Publish to Compose repo
Set up environment variables `COMPOSE_REPO_USERNAME` and `COMPOSE_REPO_KEY`.
```bash
./gradlew publishToComposeRepo
```

##### Publish to all repositories
```bash
./gradlew publish
```

##### Publish local version
```bash
./gradlew <PUBLISH_TASK> -Pdeploy.version=0.2.0 -Pdeploy.release=true
```

##### Code signing

macOS for Apple Silicon builds aimed for distribution require mandatory code signing,
so use command like
```bash
./gradlew -Psigner="Apple Distribution: Nikolay Igotti (N462MKSJ7M)" <PUBLISH_TASK>
```
to codesign the JNI library.
Use `security find-identity -v -p codesigning` to find valid signing identities.

## GitHub tag release for desktop Kotlin/Native

The `Publish Skiko Native to Maven Central` workflow publishes these targets when a tag such as
`0.152.0` or `v0.152.0` is pushed:

* `linuxX64`
* `linuxArm64`
* `mingwX64`

Configure these GitHub Actions repository secrets:

* `GRADLE_PROPERTIES`: the complete contents of the release `~/.gradle/gradle.properties` file,
  including `mavenCentralUsername`, `mavenCentralPassword`, `signing.keyId`, and
  `signing.password`. The workflow replaces `signing.secretKeyRingFile` with its runner-local path.
* `GPG_SECRET_KEY_RING_BASE64`: the base64-encoded contents of the file referenced by
  `signing.secretKeyRingFile`.

For example, create the second secret locally without printing the key into the terminal log:

```bash
base64 -w 0 ~/.gradle/secring.gpg > skiko-secret-key.base64
```

The Linux and Windows jobs create signed Maven repositories. A final job merges the repositories,
checks the native-only `skiko` root plus all three target modules, and publishes one Maven Central
deployment. The root Gradle module metadata points to `skiko-linuxx64`, `skiko-linuxarm64`, and
`skiko-mingwx64`; its temporary AWT compiler target is removed from the published variants. The tag
version overrides `deploy.version` from the Gradle properties file.
