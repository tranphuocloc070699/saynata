#!/usr/bin/env bash
# Build the app for a real iPhone and install it. If the app is already there, it is replaced (data is kept).
# It does not open the app: open it yourself on the phone.
#
#   scripts/install-on-phone.sh                 # the first paired iPhone that is reachable
#   DEVICE=<id or name> scripts/install-on-phone.sh
#   CONFIG=Release scripts/install-on-phone.sh  # default Debug
#   PAID_ACCOUNT=1 scripts/install-on-phone.sh  # sign with the real entitlements (needs a paid developer account)
#
# A free Apple ID (Personal Team) cannot sign App Groups or "Default Translation App", so by default every target
# is signed with EnglishTranslation/FreeAccount.entitlements (empty). The app works; only the shared cache between
# the app and the share extension, and Saynata as the system translation app (TASK-42), are off.
#
# Needs: Xcode, the phone paired with this Mac (cable once, then Wi-Fi works), unlocked, Developer Mode on.
# With a free Apple ID the app stops opening after 7 days: run this again.
set -euo pipefail

cd "$(dirname "$0")/.."    # repo root

PROJECT="EnglishTranslation/EnglishTranslation.xcodeproj"
SCHEME="EnglishTranslation"
CONFIG="${CONFIG:-Debug}"
BUILD_DIR="build/device"

# Pick the phone. devicectl and xcodebuild name it differently: devicectl uses its own id, xcodebuild the
# hardware UDID. Both come from one devicectl listing. DEVICE (id, UDID or name) picks one; else the first
# paired iOS device that is not "unavailable".
devices_json=$(mktemp)
trap 'rm -f "$devices_json"' EXIT
xcrun devicectl list devices --json-output "$devices_json" >/dev/null 2>&1
# The Python sits in a variable because macOS bash 3.2 cannot parse a heredoc inside $( ).
read -r -d '' PICK_DEVICE <<'EOF2' || true
import json, sys
wanted = sys.argv[2]
for device in json.load(open(sys.argv[1]))["result"]["devices"]:
    connection, hardware = device["connectionProperties"], device["hardwareProperties"]
    names = [device["identifier"], hardware.get("udid", ""), device["deviceProperties"].get("name", "")]
    if wanted:
        ok = wanted in names
    else:
        ok = (hardware.get("platform") == "iOS" and connection.get("pairingState") == "paired"
              and connection.get("tunnelState") != "unavailable")
    if ok:
        print(device["identifier"], hardware.get("udid", ""), device["deviceProperties"].get("name", "").replace(" ", "_"))
        break
EOF2
read -r DEVICE_ID DEVICE_UDID DEVICE_NAME <<< "$(python3 -c "$PICK_DEVICE" "$devices_json" "${DEVICE:-}")" || true
if [ -z "${DEVICE_ID:-}" ] || [ -z "${DEVICE_UDID:-}" ]; then
  echo "No reachable iPhone. Unlock it, connect the cable (or join the same Wi-Fi), and try again." >&2
  echo "Paired devices: xcrun devicectl list devices" >&2
  exit 1
fi
echo "Phone: ${DEVICE_NAME//_/ } ($DEVICE_UDID)"

signing_override=()
if [ "${PAID_ACCOUNT:-0}" != "1" ]; then
  signing_override=(CODE_SIGN_ENTITLEMENTS=FreeAccount.entitlements)
  echo "Signing for a free Apple ID (no App Groups, no default translation app). PAID_ACCOUNT=1 to change."
fi

echo "Building ($CONFIG)..."
# -quiet prints only warnings and errors. -allowProvisioningUpdates lets Xcode make the signing profile;
# -allowProvisioningDeviceRegistration adds this phone to the developer account when it is not there yet
# (a paid team starts with no devices).
xcodebuild -project "$PROJECT" -scheme "$SCHEME" -configuration "$CONFIG" \
  -destination "platform=iOS,id=$DEVICE_UDID" -derivedDataPath "$BUILD_DIR" \
  -allowProvisioningUpdates -allowProvisioningDeviceRegistration -quiet ${signing_override[@]+"${signing_override[@]}"} build

APP="$BUILD_DIR/Build/Products/$CONFIG-iphoneos/$SCHEME.app"
echo "Installing $APP..."
xcrun devicectl device install app --device "$DEVICE_ID" "$APP" >/dev/null

echo "Installed. Open Saynata on the phone."
