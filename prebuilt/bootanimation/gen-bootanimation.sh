#!/usr/bin/env bash
set -euo pipefail

OUT="${1:?out zip path}"
INTERMEDIATES="${2:?genDir}"
TAR1440="${3:?bootanimation_1440.tar}"
TAR1080="${4:?bootanimation_1080.tar}"
TAR720="${5:?bootanimation_720.tar}"
TARDEFAULT="${6:?bootanimation.tar}"
DESC_TXT="${7:?desc.txt}"
SCREEN_WIDTH="${8:?screen width}"
SCREEN_HEIGHT="${9:?screen height}"
FPS="${10:-30}"
SOONG_ZIP="${11:?path to soong_zip}"

echo "Generating bootanimation.zip -> $OUT"
rm -rf "$(dirname "$OUT")"
mkdir -p "$(dirname "$OUT")"
rm -rf "$INTERMEDIATES"
mkdir -p "$INTERMEDIATES"

# IMAGEWIDTH = min(max-dims flip for landscape), pick tar by width, write desc
if [[ "$SCREEN_HEIGHT" -lt "$SCREEN_WIDTH" ]]; then
  IMAGEWIDTH="$SCREEN_HEIGHT"
else
  IMAGEWIDTH="$SCREEN_WIDTH"
fi

tar_to_use="$TARDEFAULT"
desc_w=450
desc_h=450

if [[ "$IMAGEWIDTH" -eq 1440 ]]; then
  tar_to_use="$TAR1440"
  desc_w=900; desc_h=900
elif [[ "$IMAGEWIDTH" -eq 1080 ]]; then
  tar_to_use="$TAR1080"
  desc_w=680; desc_h=680
elif [[ "$IMAGEWIDTH" -eq 720 ]]; then
  tar_to_use="$TAR720"
  desc_w=450; desc_h=450
fi

# Extract frames
tar xfp "$tar_to_use" -C "$INTERMEDIATES"

# Write desc
echo "${desc_w} ${desc_h} ${FPS}" > "${INTERMEDIATES}/desc.txt"
cat "$DESC_TXT" >> "${INTERMEDIATES}/desc.txt"

"$SOONG_ZIP" -L 0 -o "$OUT" -C "$INTERMEDIATES" -D "$INTERMEDIATES"
