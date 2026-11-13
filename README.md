# Messify

An SMS/MMS app for Android with a Material 3 Expressive look: dynamic colour,
tonal surfaces, grouped message bubbles and a card-style conversation list.

## Credits

Messify is not written from scratch. It is based on, and gratefully builds on:

- **[LineageOS Messaging](https://github.com/LineageOS/android_packages_apps_Messaging)**,
  the messaging app maintained by The LineageOS Project, which this repository
  was forked from.
- **[AOSP Messaging](https://android.googlesource.com/platform/packages/apps/Messaging/)**,
  the original app by The Android Open Source Project, which LineageOS
  Messaging is itself based on.

All credit for the underlying app belongs to those projects and their
contributors. Their authorship is preserved in this repository's commit
history and in the copyright headers of the source files.

## What is different here

- Material 3 dynamic-colour day/night theme in place of the coloured action bar
- Conversation list drawn as a segmented stack of rounded cards
- Tonal message bubbles whose corners tighten within a run of messages
- Pill-shaped composer with a round send button
- Theme-tinted icons, restyled contact picker, media picker and snackbar

## Building

The app builds inside an Android source tree (LineageOS or AOSP) with Soong;
see `Android.bp`. There is no standalone Gradle build yet.

## Licence

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

    Copyright (C) 2015 The Android Open Source Project
    Copyright (C) The LineageOS Project

Files changed for Messify carry the upstream copyright notices unchanged, as
the licence requires. Licences of bundled third-party components are listed in
`assets/licenses.html`.
