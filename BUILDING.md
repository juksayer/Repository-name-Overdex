# Local build environment

The terminal may not have `java` on `PATH` or `JAVA_HOME` set. Gradle then exits before compiling with `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.`

This checkout currently has a local JetBrains Runtime at `.jbr/` (untracked). From the repository root, run Gradle with:

```sh
JAVA_HOME="$PWD/.jbr" ./gradlew :app:testDebugUnitTest
```

If `.jbr/` is absent on another machine, point `JAVA_HOME` at an installed compatible JDK, such as Android Studio's bundled `jbr` directory. Keep the JDK path machine-local; do not commit the runtime.

## Testing on a phone with existing data

Back up the entire private app directory before running instrumentation tests on
a user's phone, including databases, shared preferences, calibration profiles,
and match files. A calibration-only backup does not protect teams or owned cards.
Verify that the backup is readable before beginning, and preserve it until the
post-test data checks pass.

`gradle.properties` sets
`android.injected.androidTest.leaveApksInstalledAfterRun=true`. Keep this enabled:
the Android Gradle Plugin otherwise uninstalls the app after connected tests,
which deletes its private data. Do not override it to false on a user's device.
After any test-tool upgrade, verify the runner still honors this setting on a
disposable emulator before using a real phone.

For targeted phone checks, prefer installing updates with `adb install -r` and
running the selected tests with `adb shell am instrument`, using an explicit
device serial. Do not uninstall the app or clear its data as a test cleanup step.
Check the calibration, teams, owned cards, and match list afterward; a passing
test result alone does not establish that user data survived.
