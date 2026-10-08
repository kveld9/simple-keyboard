# Privacy Policy

Simple Keyboard does not collect, store, or transmit user data off the device.

## Network

- The app declares no `INTERNET` permission and generates zero network traffic.
- No analytics, crash reporting, attribution, advertising, or push SDKs.
- No accounts, no cloud sync, no background jobs.

## Permissions

| Permission | Purpose |
| :--- | :--- |
| `VIBRATE` | Keypress haptic feedback. |
| `READ_EXTERNAL_STORAGE` (`maxSdkVersion 32`) / `READ_MEDIA_IMAGES` | Optional image suggestions from recent screenshots. |

No microphone, camera, location, contacts, or network-state permissions. A
`ManifestPermissionsTest` regression test fails the build if a sensitive
permission is ever added.

## On-device data

- Learned words, blocked words, and settings stay in app-private storage.
- Clipboard history (`clipboard_history.db`) and clipboard images are excluded
  from cloud backup and device transfer via `backup_rules.xml`, and clipboard
  retention is user-configurable with automatic expiry.
- Backups are manual JSON exports to a user-chosen location. Auto-generated
  names follow `simplekeyboard_backup_yyyyMMdd_HHmmss.json`, and rotation keeps
  the 10 most recent files.
