# Follow Mode Integration Guide

Branch: `feature/follow-mode`

```bash
git fetch origin
git checkout feature/follow-mode
```

---

## What Was Built

Person-following robot controller running entirely on-device. Uses MediaPipe face detection + DeepSORT tracking + PID control to follow the nearest person within 0.5m. Sends `cmd_vel` commands to the chassis through the existing ChassisProxy (rosbridge JSON).

The original Java implementation (RobotController.java) was ported to Kotlin and integrated into the existing Jackie app architecture — Jetpack Compose UI, WebSocket chassis proxy, theme-driven home screen.

---

## Files Changed

### New Files

| File | Lines | Description |
|------|-------|-------------|
| `app/src/main/java/com/gow/smaitrobot/follow/KalmanTrack.kt` | 168 | 8-state Kalman filter (cx, cy, w, h + velocities). Predict/update cycle with constant-velocity motion model. Matrix math utilities (matMul, transpose, inv4, etc.) |
| `app/src/main/java/com/gow/smaitrobot/follow/DeepSortTracker.kt` | 105 | Multi-face tracker. IOU cost matrix + Hungarian assignment. Spawns new tracks for unmatched detections, prunes after 5 misses, confirms after 3 hits. |
| `app/src/main/java/com/gow/smaitrobot/follow/PidController.kt` | 25 | PID controller with kp/ki/kd gains, integral accumulator, derivative term. |
| `app/src/main/java/com/gow/smaitrobot/follow/FollowController.kt` | 280 | Main controller. Owns MediaPipe FaceLandmarker, CameraX pipeline, DeepSORT tracker, 6-state FSM, and PID controllers. Sends cmd_vel Twist JSON through a chassis sender callback. |
| `app/src/main/java/com/gow/smaitrobot/ui/follow/FollowScreen.kt` | 130 | Compose UI screen. Large start/stop button, live FSM state text, distance readout. Cleans up on navigation away. |
| `app/src/main/assets/face_landmarker.task` | 3.7MB | MediaPipe FaceLandmarker model file (binary). |

### Modified Files

| File | What Changed |
|------|-------------|
| `app/build.gradle.kts` | Added dependencies: `com.google.mediapipe:tasks-vision:0.10.14`, `androidx.camera:camera-camera2:1.3.1`, `camera-lifecycle:1.3.1`, `camera-view:1.3.1` |
| `app/src/main/assets/default_theme.json` | Added "Follow Me" card entry (icon: "follow", action: "navigate:follow") |
| `app/src/main/java/com/gow/smaitrobot/navigation/Screen.kt` | Added `Screen.Follow` sealed class object |
| `app/src/main/java/com/gow/smaitrobot/navigation/AppNavigation.kt` | Created `FollowController` instance wired to ChassisProxy. Added `composable<Screen.Follow>` route. |
| `app/src/main/java/com/gow/smaitrobot/ui/home/HomeViewModel.kt` | Added `"follow" -> Screen.Follow` to `parseCardAction()` |
| `app/src/main/java/com/gow/smaitrobot/ui/home/HomeScreen.kt` | Added `DirectionsWalk` icon import and `"follow"` icon mapping |

### Untouched (no changes)

- Voice pipeline (CaeAudioManager, ConversationViewModel, ConversationScreen)
- VideoStreamManager (server video stream)
- WebSocketRepository
- ChassisProxy
- TtsAudioPlayer
- All test files

---

## How It Works

```
CameraX (on-device front camera, ~30fps)
  -> MediaPipe FaceLandmarker (up to 4 faces, live stream mode)
    -> Bounding rects from 468 landmarks
      -> DeepSORT (predict -> IOU match -> Hungarian assign -> update)
        -> Lock onto first confirmed track (3+ consecutive hits)
          -> Estimate distance: (0.165m * focal_length_px) / face_width_px
            -> FSM tick (every frame, on main thread):
               FOLLOWING:    PID(pan error, distance error) -> cmd_vel
               SCAN_ROTATE:  Rotate 45 deg CCW, check for face
               OBSTACLE_TURN: Rotate 90 deg CW, then clear check
               COLLISION_STOP: Full stop for 3 seconds
               COLLISION_TURN: Rotate 45 deg CW after collision pause
               CLEAR_CHECK:  Verify path clear, resume following
                -> cmd_vel JSON -> ChassisProxy -> chassis rosbridge
```

---

## What Needs to Be Done (Testing & Calibration)

### 1. Calibrate Focal Length (Required)

File: `FollowController.kt` line 74

```kotlin
private const val FOCAL_LENGTH_PX = 600.0  // calibrate per camera
```

Current value is a generic estimate. To calibrate on the actual camera:
1. Stand exactly 0.5m from the camera
2. Read face bounding box width from logcat (`FollowController` tag)
3. Compute: `focal_length = (0.165 * face_width_px) / 0.5`
4. Update the constant

### 2. Camera Selection (May Need Change)

File: `FollowController.kt` line 165

```kotlin
CameraSelector.DEFAULT_FRONT_CAMERA
```

If the robot uses an external USB camera instead of front-facing, change to:
```kotlin
CameraSelector.DEFAULT_BACK_CAMERA
```

Or implement external camera selection — see `VideoStreamManager.kt` lines 152-167 for the pattern used by the existing video pipeline (prefers LENS_FACING_EXTERNAL, falls back to LENS_FACING_FRONT).

### 3. Wire Proximity / LIDAR Sensor (Optional)

File: `FollowController.kt` lines 230-234

The OBSTACLE_TURN and COLLISION states exist in the FSM but no real sensor data feeds them. Currently CLEAR_CHECK immediately resumes following.

To wire real sensor data:
- Subscribe to the chassis LIDAR rosbridge topic
- Call into the FSM collision handler when obstacle detected within 0.3m
- The FSM already handles COLLISION_STOP (3s pause) -> COLLISION_TURN (45 deg) -> CLEAR_CHECK

### 4. Tune PID Gains (If Needed)

File: `FollowController.kt` lines 261-262

```kotlin
private val panPid = PidController(0.003, 0.0001, 0.001)   // centering face horizontally
private val distPid = PidController(0.0003, 0.0, 0.0005)   // maintaining follow distance
```

If robot oscillates: reduce kp. If robot is sluggish: increase kp.

### 5. Tune Motion Parameters (If Needed)

File: `FollowController.kt` lines 70-82

```kotlin
FOLLOW_DISTANCE_M = 0.50     // max range to track (metres)
COLLISION_DISTANCE_M = 0.10  // emergency stop distance
FACE_WIDTH_M = 0.165         // average adult face width (16.5cm)
TURN_SPEED_RAD_S = 0.4f      // rotation speed for scan/obstacle manoeuvres
COLLISION_PAUSE_MS = 3000     // how long to freeze after collision
```

### 6. Camera Conflict (Be Aware)

Follow Mode opens its own CameraX pipeline. The existing `VideoStreamManager` also opens the camera for streaming video to the server. If both run simultaneously, one will fail.

Follow Mode and Conversation Mode should be mutually exclusive — do not start Follow Mode while a conversation session is active.

---

## Key Code Locations for Review

| What | File | Lines |
|------|------|-------|
| FSM state machine (all 6 states) | `FollowController.kt` | 198-258 |
| PID drive-toward-face logic | `FollowController.kt` | 262-275 |
| cmd_vel JSON format (rosbridge Twist) | `FollowController.kt` | 290-306 |
| Distance estimation (pinhole model) | `FollowController.kt` | 194-197 |
| MediaPipe configuration | `FollowController.kt` | 116-133 |
| Kalman predict/update math | `KalmanTrack.kt` | 38-61 |
| IOU matching + Hungarian assignment | `DeepSortTracker.kt` | 25-50, 72-106 |
| Chassis proxy wiring | `AppNavigation.kt` | 98-108 |
| Home screen card entry | `default_theme.json` | card with action "navigate:follow" |

---

## Build & Install

```bash
git checkout feature/follow-mode
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

New dependencies (auto-resolved by Gradle):
- `com.google.mediapipe:tasks-vision:0.10.14`
- `androidx.camera:camera-camera2:1.3.1`
- `androidx.camera:camera-lifecycle:1.3.1`
- `androidx.camera:camera-view:1.3.1`

No server-side changes required. The follow mode runs entirely on-device and sends chassis commands through the existing ChassisProxy.
