# Grow with Green

An Android app for managing indoor and outdoor grows — plants, grow spaces, watering and feeding schedules, and a photo journal, all laid out on a calendar.

## Features

- **Environments** — indoor, outdoor or greenhouse spaces with growing medium, light cycles (including a planned flip, e.g. 18/6 → 12/12) and a nutrient lineup.
- **Plants** — profile photo, plant type, breeder and strain (suggestions from real breeder catalogs, or type your own), genetics and stage.
- **Schedules** — water and feed every N days. Each logged watering restarts the countdown; missed ones show as overdue. One-tap Water/Feed buttons on each plant.
- **Calendar** — month view of waterings, feedings, light changes, harvest milestones and journal entries.
- **Journal** — notes and photos (camera or gallery), plus automatic history: waterings with pH/PPM, stage changes, new profile photos.
- **Harvest** — drying (days) and curing (weeks) with reminders when done; harvested and dead plants (with cause) move to an archive.
- **Reminders** — a daily notification of what's due, including overdue tasks.
- **Achievements** — badges for milestones, shown on the plant that earned them.
- **Backup** — save everything (database and photos) to Google Drive or any location via the system file picker; restore after a reinstall.

## Building

Requirements: JDK 17 and the Android SDK (Android Studio includes both).

```sh
./gradlew assembleDebug        # build the debug APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # run unit tests
```

Or open the project in Android Studio and press Run.

Tech: Kotlin, Jetpack Compose (Material 3), Room, WorkManager, Coil. Min SDK 26, target SDK 35.
