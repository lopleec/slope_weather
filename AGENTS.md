# Project instructions

## Android

- Use the package namespace `com.lopleec.xxx`; this application is `com.lopleec.slopeweather`.
- Before an ADB install, inspect the connected device and Android users.
- Install only in the main space with `adb -s <serial> install --user 0 -r <apk>`.
- Verify installation for user 0 and compare clone-profile installation state before and after. Do not remove cloned applications.

## Publication

- README must provide complete English and Chinese sections, with English first.
- Commit messages, release notes and public documentation must be understandable without a chat transcript.
- Do not use assistant first-person narration, conversational history or references that require private context.
- Keep signing keys, signing passwords, local SDK paths, private diagnostics and build caches out of public artifacts.
