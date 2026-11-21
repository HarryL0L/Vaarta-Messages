#!/usr/bin/env bash
#
# Runs the app on a headless Android emulator, for machines that have no phone
# attached. It works without hardware acceleration (no /dev/kvm), which is the
# usual case in a cloud container, by using the lightweight "automated test
# device" system image.
#
#   scripts/emulator.sh start            boot, install the debug APK, set it up
#   scripts/emulator.sh shot out.png     save a screenshot
#   scripts/emulator.sh sms 5551001 hi   deliver a test SMS to the emulator
#   scripts/emulator.sh stop             shut the emulator down
#
# The SDK is installed into $ANDROID_HOME, or ~/android-sdk if that is unset.
# Build the APK first: ./scripts/fetch-deps.sh && ./gradlew assembleDebug
#
# What makes this work where a default setup does not:
#   * system-images;android-33;aosp_atd;x86_64 boots in a few minutes under
#     software emulation; the regular images take far longer and then freeze.
#     Android 13 is the oldest version the app runs on.
#   * That image ships with drawing switched off, so every screenshot is
#     black until debug.hwui.drawing_enabled is set to true and the app is
#     restarted.
#   * Without /dev/kvm the emulator needs "-accel off", and recent versions
#     also need ANDROID_I_WANT_MY_TCG=yes or they refuse to start.
#   * Do not pass "-qemu -accel tcg,thread=multi"; the emulator then never
#     finishes booting.
#   * A killed emulator leaves *.lock files in the AVD that stop the next
#     start; they are removed here.

set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
AVD_NAME="vaarta-atd"
IMAGE="system-images;android-33;aosp_atd;x86_64"
PACKAGE="io.github.harryl0l.vaarta"
LAUNCH_ACTIVITY="com.android.messaging.ui.conversationlist.ConversationListActivity"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$REPO_ROOT/build/outputs/apk/debug/Vaarta-debug.apk"

export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK" ANDROID_AVD_HOME="$SDK/avd"
ADB="$SDK/platform-tools/adb"

install_sdk() {
    if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
        echo "Installing Android command-line tools into $SDK"
        mkdir -p "$SDK/cmdline-tools"
        local zip
        zip="$(curl -fsS https://dl.google.com/android/repository/repository2-3.xml \
            | grep -o 'commandlinetools-linux-[0-9]*_latest.zip' | sort -u | tail -1)"
        curl -fsS -o "$SDK/cmdline-tools.zip" "https://dl.google.com/android/repository/$zip"
        rm -rf "$SDK/cmdline-tools/latest" "$SDK/cmdline-tools/cmdline-tools"
        unzip -q -o "$SDK/cmdline-tools.zip" -d "$SDK/cmdline-tools"
        # The tools must live in cmdline-tools/latest, or they misjudge the SDK root.
        mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
        rm -f "$SDK/cmdline-tools.zip"
    fi
    if [ ! -x "$SDK/emulator/emulator" ] || [ ! -d "$SDK/system-images/android-33/aosp_atd" ]; then
        echo "Installing the emulator and system image (about 1.5 GB)"
        yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" \
            "platform-tools" "emulator" "$IMAGE" > "$SDK/sdkmanager.log" 2>&1 || true
        [ -x "$SDK/emulator/emulator" ] || { tail -5 "$SDK/sdkmanager.log"; exit 1; }
    fi
}

create_avd() {
    [ -d "$ANDROID_AVD_HOME/$AVD_NAME.avd" ] && return
    mkdir -p "$ANDROID_AVD_HOME"
    echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd \
        -n "$AVD_NAME" -k "$IMAGE" --force > /dev/null
    local config="$ANDROID_AVD_HOME/$AVD_NAME.avd/config.ini"
    # A small screen and a fixed amount of memory keep software rendering bearable.
    sed -i '/^hw.lcd.width\|^hw.lcd.height\|^hw.lcd.density\|^hw.ramSize\|^hw.keyboard/d' "$config"
    printf 'hw.lcd.width=720\nhw.lcd.height=1520\nhw.lcd.density=280\nhw.ramSize=4096\nhw.keyboard=yes\n' \
        >> "$config"
}

wait_for_boot() {
    echo "Waiting for the emulator to boot (a few minutes without acceleration)"
    local booted=""
    for _ in $(seq 1 150); do
        booted="$(timeout 20 "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
        [ "$booted" = "1" ] && return 0
        sleep 10
    done
    echo "The emulator did not finish booting; see $SDK/emulator.log" >&2
    return 1
}

start() {
    install_sdk
    create_avd
    if ! pgrep -f "qemu-system.*$AVD_NAME" > /dev/null; then
        rm -rf "$ANDROID_AVD_HOME/$AVD_NAME.avd"/*.lock
        local accel=()
        if [ ! -e /dev/kvm ]; then
            accel=(-accel off)
            # Newer emulators refuse software emulation unless this is set.
            export ANDROID_I_WANT_MY_TCG=yes
        fi
        # setsid detaches it, so it survives the shell that started it.
        setsid nohup "$SDK/emulator/emulator" -avd "$AVD_NAME" -no-window -no-audio \
            -no-boot-anim -no-snapshot "${accel[@]}" -gpu swiftshader_indirect \
            -memory 4096 -cores 4 -no-metrics > "$SDK/emulator.log" 2>&1 < /dev/null &
    fi
    wait_for_boot

    # Without this every screenshot of the test image is black.
    "$ADB" shell setprop debug.hwui.drawing_enabled true
    "$ADB" shell svc power stayon true

    if [ -f "$APK" ]; then
        echo "Installing $APK"
        timeout 600 "$ADB" install -r "$APK" | tail -1
        for permission in READ_SMS RECEIVE_SMS SEND_SMS RECEIVE_MMS READ_CONTACTS \
                WRITE_CONTACTS READ_PHONE_STATE POST_NOTIFICATIONS; do
            "$ADB" shell pm grant "$PACKAGE" "android.permission.$permission" || true
        done
        # Often times out on a slow emulator but still takes effect.
        timeout 200 "$ADB" shell cmd role add-role-holder android.app.role.SMS "$PACKAGE" \
            > /dev/null 2>&1 || true
        "$ADB" shell am force-stop "$PACKAGE"
        "$ADB" shell am start -n "$PACKAGE/$LAUNCH_ACTIVITY" > /dev/null
        echo "Started the app. Give it about a minute to draw before taking a screenshot."
    else
        echo "No APK at $APK; build it and run '$0 start' again to install it."
    fi
}

case "${1:-}" in
    start)
        start
        ;;
    shot)
        "$ADB" exec-out screencap -p > "${2:?usage: $0 shot <file.png>}"
        ;;
    sms)
        "$ADB" emu sms send "${2:?usage: $0 sms <from> <text>}" "${3:?usage: $0 sms <from> <text>}"
        ;;
    stop)
        "$ADB" emu kill > /dev/null 2>&1 || true
        ;;
    *)
        sed -n '3,13p' "$0" | sed 's/^# \{0,1\}//'
        exit 1
        ;;
esac
