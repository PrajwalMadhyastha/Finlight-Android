# Engineering & Quality Standards

## 1. Quality Gates & Code Coverage
- **Coverage Mandate**: Maintain **>80% unit test coverage** and **<3% code duplication** on all new or modified code.
- **Sonar Coverage Exclusions**: The following paths and files are exempt from the 80% coverage mandate:
  - `**/*ViewModelFactory*.kt`
  - `**/*Screen.kt`
  - `**/ui/screens/**`
  - `**/ui/components/**`
  - `**/ui/theme/**`
  - `**/ui/NavItems.kt`
  - `**/MainActivity.kt`
  - `**/MainApplication.kt`
  - `**/utils/ShareImageGenerator.kt`
  - `**/data/db/**` (Database package)
  - `**/*_Impl*` (Generated implementations)
- Any files modified or added outside these exclusions must strictly maintain >80% test coverage.

## 2. Implementation Plan Requirements
When creating or updating implementation plans (`implementation_plan.md`), always include:
- Explicit unit test cases (including negative boundary and exception paths).
- Relevant feature test cases.
- Code coverage targets and validation plan.

## 3. Test Execution Workflow
- **Unit Tests**: Run unit tests after changes:
  ```bash
  JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest
  ```
- **Feature Tests**: After every change implementation, run and fix (if needed) the feature test suite on the connected running emulator:
  ```bash
  JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew connectedDebugAndroidTest
  ```
  *(Or run targeted feature test classes when testing specific workflows)*

## 4. Linting & Formatting Standards
- Ensure all Kotlin code conforms to style guidelines by running ktlint:
  ```bash
  JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew ktlintCheck
  ```
- Use `./gradlew ktlintFormat` to fix formatting discrepancies if needed. All lint checks must pass before completing work.
