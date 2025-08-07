# Contributing to Finlight

Thank you for your interest in contributing to Finlight! We're excited to build a great app with your help.

## Code of Conduct

This project and everyone participating in it is governed by our **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)**. By participating, you are expected to uphold this code.

## How to Contribute

### Reporting Bugs & Suggesting Features
Please open an issue on GitHub using the appropriate template (Bug Report or Feature Request).

### Submitting a Pull Request
1.  Fork the repository and create your branch from `develop`.
2.  Make your changes.
3.  [cite_start]Ensure the code lints correctly by running `./gradlew ktlintCheck`[cite: 1].
4.  Make sure all unit tests pass by running `./gradlew testDebugUnitTest`.
5.  Open a pull request to the `develop` branch and fill out the PR template.

## Branching Strategy

- **`main`**: Contains production-ready code.
- **`develop`**: The main development branch. All PRs should be targeted here.
- **`feature/<feature-name>`**: Create branches from `develop` for all new features.