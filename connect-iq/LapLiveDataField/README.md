# LAP Live Connect IQ data field

This data field is designed to be selected as a data screen inside Garmin's normal Run activity. It reads only the current `Activity.Info`; it does not start, pause, stop, or save the activity.

The vívoactive 5 screen follows the supplied reference: local clock at the top, the existing LAP turtle above the elapsed timer, current distance, pace and heart rate, and a green progress arc around the display. The heart symbol is drawn as a small native red graphic. Positions and sizes use the 390 × 390 display coordinate system and scale from the active display dimensions.

LAP sends the selected run's stable training ID and planned duration to the official Connect IQ app message API. The field stores this per-run target locally on the watch, acknowledges the same ID and duration to LAP, then advances the ring by elapsed timer divided by the planned duration. The watch continues the ring offline after receipt. The target is consumed when the activity ends and cleared at the next activity start unless LAP sends a fresh one. The most recent progress is held while the activity timer is paused. When LAP has not supplied a current target, the optional `Zielstrecke in km` setting or an active Garmin course's `distanceToDestination` can still drive the distance ring; with neither source, only the dark background ring is shown.

The Connect IQ data field refreshes once per second. The ring uses the latest measured distance while the activity timer is running, so its rate reflects the runner's actual movement rather than an assumed constant speed. Its fraction remains unchanged during a pause.

LAP's current plan prescribes time-based sessions rather than a per-run distance. Accordingly, this transfer uses the scheduled duration as its target and does not invent a distance. A distance-based target will only be sent if a future plan explicitly contains one.

The data field calls `compute()` at the Connect IQ data-field cadence (once per second). It keeps at most one `Communications.transmit()` request outstanding. New samples are dropped while that request is pending, and no failed sample is retried. The next fresh sample is sent after the SDK callback releases the slot.

## Protocol v1

The message is a Connect IQ array of eight supported primitive values:

`[version, sequence, watchEpochSeconds, timerMillis, distanceMeters, speedMetersPerSecond, heartRateBpm, timerState]`

Unavailable distance, speed, and heart rate use `-1`. Timer states sent to LAP are `2` for paused and `3` for recording. LAP accepts only version 1, rejects malformed or stale packets, and drops repeated or out-of-order samples. Pace is derived from a valid positive speed in metres per second. The watch timestamp has one-second resolution, so Android's latency value is an estimate rather than sub-second timing.

The Connect IQ ID is `7D574A829AAC4E83AF6A6E1F04E8A09B`. The Android companion listener must use the same ID. Do not change one side without changing the other.

## LAP target protocol

The Android app sends `["LAP_TARGET", 1, trainingId, durationSeconds]` to the field. The field accepts only a non-empty ID and a duration from one second to 24 hours. After storing the target properties on the watch, it sends `["LAP_TARGET_ACK", 1, trainingId, durationSeconds]` back through the same official Connect IQ channel. Android reports only that app-level acknowledgement as target receipt; the SDK send callback by itself is reported as sent, not received. This target message is independent of the existing v1 live-measurement packet and the PING test.

## Transport diagnostics and PING

The data field logs only the packet type, entry count, attempt/success/error counters, and whether a callback timed out. It never logs timer, distance, pace, or heart-rate values. The one-shot test packet is `["LAP_PING", 1, sequence]`; it contains no activity metrics. `PING_TEST_MODE` is false in the normal source build.

In Android debug builds using TETHERED transport, LAP registers listeners for both the manifest UUID and the empty simulator app ID. Both callbacks enter the same status and message validation path. A bounded 32-message set suppresses duplicate callback delivery. Wireless Garmin Connect uses only the manifest UUID.

The PING-only test build is separate from the normal live-data field. Do not install that test build as the normal coach field after the connectivity check; build the standard field with `PING_TEST_MODE` disabled.

## Build prerequisites

Install the official Garmin Connect IQ SDK Manager, sign in with the owner's Garmin developer account, install the Connect IQ SDK and the `vivoactive5` device definition. Create a local developer key outside Git, then build for `vivoactive5` with `monkeyc`. The project targets Connect IQ API 5.0 and the vívoactive 5's supported API 5.2.

No Garmin device is changed by building or running the simulator. To run on the watch, install the data field and select LAP Live as a data screen in the normal Run activity; do not replace the native Run activity.
