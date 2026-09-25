# Local build environment

The terminal may not have `java` on `PATH` or `JAVA_HOME` set. Gradle then exits before compiling with `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.`

This checkout currently has a local JetBrains Runtime at `.jbr/` (untracked). From the repository root, run Gradle with:

```sh
JAVA_HOME="$PWD/.jbr" ./gradlew :app:testDebugUnitTest
```

If `.jbr/` is absent on another machine, point `JAVA_HOME` at an installed compatible JDK, such as Android Studio's bundled `jbr` directory. Keep the JDK path machine-local; do not commit the runtime.
