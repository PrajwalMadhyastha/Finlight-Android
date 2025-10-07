Finlight
==========

**Finlight** is a privacy-focused, on-device personal finance management app for Android (7.0+). Built entirely with Jetpack Compose, it features a sophisticated, multi-layered SMS parsing engine, on-device machine learning, and a bespoke glassmorphism design language called **"Project Aurora"**.

> **Our Mission:** To provide a powerful, beautiful, and completely private financial tool. Your data is yours. Period. It never leaves your device.

Project Aurora: A Glimpse into Finlight
---------------------------------------

Finlight's UI is built on a custom design language that emphasizes depth, light, and motion. We use theme-aware animated backgrounds and frosted glass effects to create an experience that is both functional and beautiful.

| Dashboard & Privacy Mode                    | Spending Analysis Hub                       |
|---------------------------------------------|---------------------------------------------|
| ![Privacy Mode](docs/gifs/Privacy_mode.gif) | ![Analysis Hub](docs/gifs/Analysis_hub.gif) |

Core Philosophy
---------------

Finlight is built on three core principles:

1.  **Privacy First:** All financial data is stored in a locally encrypted SQLCipher database. There are no servers and no user accounts. Secure, automatic backups can be made to your personal Google Drive.

2.  **Intelligent Automation:** At its heart is a multi-stage SMS parser called the **"Finlight Genie"**. It uses a hierarchy of trust to accurately capture transactions:

    *   **User-Defined Rules:** High-priority custom rules give you full control.

    *   **On-Device ML:** A custom TensorFlow Lite model pre-filters messages, reducing noise and improving accuracy.

    *   **Heuristic & Generic Parsers:** A powerful engine that learns from your corrections and uses dozens of regex patterns to catch everything else.

3.  **Bespoke Design:** "Project Aurora" isn't just a theme; it's a commitment to a high-quality user experience. Every component, from buttons to dialogs, is designed to be cohesive, theme-aware, and aesthetically pleasing.


Feature Spotlight
-----------------

*   **Spending Analysis Hub:** A powerful tool to visualize and drill down into your spending. Group data by category, tag, or merchant, and apply advanced, cross-dimensional filters.

*   **Intelligent Budget Summary:** The dashboard provides smart, context-aware advice by analyzing your "spending velocity" and forecasting your month-end total.

*   **Automated Travel Mode:** Automatically tags transactions within a trip's date range and handles foreign currency conversions.

*   **Smart Account Merging:** Proactively suggests and merges duplicate accounts (e.g., "ICICI Bank" and "ICICI - xx1234") using a Levenshtein distance check.

*   **Transaction Splitting:** Split a single expense (like a grocery bill) into multiple categorized items.

*   **Tagging System:** Organize transactions with custom tags, then filter by them in the search and analysis screens.

*   **Customizable Dashboard:** Drag and drop cards to create a layout that works for you.

*   **Privacy Mode:** Hide all sensitive amounts on the dashboard with a single tap.


Technology Stack
----------------

*   **Core:** 100% [Kotlin](https://kotlinlang.org/), [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html), [Flow](https://developer.android.com/kotlin/flow)

*   **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/)

*   **Architecture:** MVVM, Repository Pattern, Multi-module (:app, :core, :analyzer)

*   **Database:** [Room](https://developer.android.com/training/data-storage/room) with [SQLCipher](https://www.zetetic.net/sqlcipher/) for full database encryption.

*   **Background Jobs:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)

*   **On-Device ML:** [TensorFlow Lite](https://www.tensorflow.org/lite)

*   **Testing:** JUnit, Mockito, Robolectric, Espresso UI Tests


Getting Started
---------------

1.  git clone [**https://github.com/PrajwalMadhyastha/Finlight-Android.git**](https://github.com/PrajwalMadhyastha/Finlight-Android.git)

2.  Create a local.properties file in the root of the project. You can copy the local.properties.template file to get started. This is required for release signing configs.

3.  Open the project in the latest stable version of Android Studio.

4.  Build and run the app configuration on an emulator or a physical device (API 24+).


How to Contribute
-----------------

We welcome all contributions! Please read our [**CONTRIBUTING.md**](https://www.google.com/search?q=CONTRIBUTING.md) to get started.

License
-------

This project is licensed under the MIT License - see the [**LICENSE**](https://www.google.com/search?q=LICENSE) file for details.