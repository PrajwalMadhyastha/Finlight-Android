# Finlight

Finlight is a privacy-focused, on-device personal finance management application for Android (API Level 24+). Built entirely with Jetpack Compose, the application incorporates a sophisticated, multi-layered SMS parsing engine, on-device machine learning, and a custom design system termed "Project Aurora".

> **Mission Statement:** To provide a secure, efficient, and private financial management tool. User data remains strictly on the device, ensuring complete privacy.

## Project Aurora Design System

Finlight utilizes a custom UI design language that prioritizes depth, lighting, and fluid motion. The application implements theme-aware animated backgrounds and frosted glass effects to deliver a highly functional and cohesive user interface.

| Dashboard & Privacy Mode                    | Spending Analysis Hub                       |
|---------------------------------------------|---------------------------------------------|
| ![Privacy Mode](docs/gifs/Privacy_mode.gif) | ![Analysis Hub](docs/gifs/Analysis_hub.gif) |

## Core Architecture and Philosophy

Finlight is developed around three foundational principles:

1.  **Privacy:** All financial data is stored locally within an encrypted SQLCipher database. The application operates without remote servers or user accounts. Secure, automated backups can be configured using Google Drive.

2.  **Intelligent Automation:** The application utilizes a multi-stage SMS parsing engine to accurately extract and categorize transaction data. This system employs:
    *   **User-Defined Rules:** Customizable rules for high-priority transaction matching.
    *   **On-Device Machine Learning:** A TensorFlow Lite model that pre-filters messages to minimize noise and enhance parsing accuracy.
    *   **Heuristic & Generic Parsers:** An adaptive engine utilizing extensive regular expression patterns, capable of learning from manual corrections.

3.  **Design System:** The "Project Aurora" design system ensures a consistent and high-quality user experience across all components, adhering to modern UI/UX standards.

## Feature Overview

*   **Spending Analysis Hub:** An analytical tool for visualizing expenditure. Users can aggregate data by category, tag, or merchant, and apply complex, cross-dimensional filters.
*   **Intelligent Budget Summary:** The dashboard delivers context-aware financial forecasting by analyzing spending velocity and projecting month-end totals.
*   **Automated Travel Mode:** Automatically identifies and tags transactions within a specified trip duration and processes foreign currency conversions.
*   **Smart Account Merging:** Proactively identifies and suggests the merging of duplicate accounts (e.g., "ICICI Bank" and "ICICI - xx1234") utilizing Levenshtein distance algorithms.
*   **Transaction Splitting:** Enables the division of a single transaction (e.g., a consolidated grocery receipt) into multiple, individually categorized entries.
*   **Tagging System:** Facilitates the organization of transactions via custom tags, enhancing search and filtering capabilities.
*   **Customizable Dashboard:** Offers a modular interface allowing users to arrange dashboard components according to their preferences.
*   **Privacy Mode:** Obscures sensitive financial figures across the dashboard with a single interaction.

## Technology Stack

*   **Core:** [Kotlin](https://kotlinlang.org/), [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html), [Flow](https://developer.android.com/kotlin/flow)
*   **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material Design 3](https://m3.material.io/)
*   **Architecture:** Model-View-ViewModel (MVVM), Repository Pattern, Multi-module architecture (`:app`, `:core`, `:analyzer`)
*   **Database:** [Room](https://developer.android.com/training/data-storage/room) integrated with [SQLCipher](https://www.zetetic.net/sqlcipher/) for robust database encryption
*   **Background Processing:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
*   **On-Device ML:** [TensorFlow Lite](https://www.tensorflow.org/lite)
*   **Testing:** JUnit, Mockito, Robolectric, Espresso

## Installation and Setup

1.  Clone the repository:
    ```bash
    git clone https://github.com/PrajwalMadhyastha/Finlight-Android.git
    ```
2.  Navigate to the project root and create a `local.properties` file. You may use `local.properties.template` as a starting point. This is necessary for configuring release signing credentials.
3.  Open the project using the latest stable release of Android Studio.
4.  Build and deploy the application to an emulator or a physical device running Android 7.0 (API level 24) or higher.

## Contributing

Contributions are welcome. Please refer to the [**CONTRIBUTING.md**](CONTRIBUTING.md) file for guidelines on how to participate in the development of this project.

## License

This project is licensed under the MIT License. Please see the [**LICENSE**](LICENSE) file for further details.