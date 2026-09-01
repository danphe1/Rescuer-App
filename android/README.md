# Nepal Scouts Rescuer — Native Android

This directory is the Google Play mobile application foundation. It is not the web/PWA client.

## Compatibility
- minSdk 23: Android 6.0+
- targetSdk 36: Android 16 / current Google Play new-app requirement as of 2026-08-31
- Java 17 / Kotlin

## Foreground rescue tracking
Tracking starts only from a visible active-mission action after location permission is granted. `RescueLocationService` runs as a location foreground service with a persistent notification. It does not auto-track OFF_DUTY users or auto-start at boot.

Every location is inserted into Room before network delivery. WorkManager sends compact batches to `rescue-tracker-api`; original capture timestamps and client event IDs are retained for deduplication.

## Low Data Mode
Low Data Mode is ON by default for field use:
- GPS remains captured locally every ~30 seconds while active.
- Android may batch callbacks for up to ~2 minutes to reduce wakeups.
- Network uploads are compact JSON batches of up to 80 points.
- GPS capture continues when the network is unavailable.
- Map tiles are not part of the tracking service and should only load when the rescuer opens Map.
- Media should be queued locally and require explicit upload / suitable connectivity in the full native UI.

Standard mode captures approximately every 15 seconds and permits more frequent delivery.

## Security
The approved-device token is encrypted with Android Keystore AES/GCM before being stored locally. No Supabase service-role credential belongs in the app.

## Remaining native application work
The service layer is designed to be called by the full approved native Rescuer UI: login/device approval, five-action Home, Mission, Map, Check In, Message, SOS, Activity, Profile, Tracking/Privacy, and Offline Sync. Command remains a coordinator application and does not need continuous foreground GPS.
