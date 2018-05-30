#!/bin/bash

WIDTH="$1"
HEIGHT="$2"
HALF_RES="$3"
OUT="$ANDROID_PRODUCT_OUT/obj/BOOTANIMATION"

if [ "$HEIGHT" -lt "$WIDTH" ]; then
    IMAGEWIDTH="$HEIGHT"
    IMAGEHEIGHT="$WIDTH"
else
    IMAGEWIDTH="$WIDTH"
    IMAGEHEIGHT="$HEIGHT"
fi

IMAGESCALEWIDTH="$IMAGEWIDTH"
IMAGESCALEHEIGHT="$IMAGEHEIGHT"

if [ "$HALF_RES" = "true" ]; then
    IMAGEWIDTH=$(expr $IMAGEWIDTH / 2)
    IMAGEHEIGHT=$(expr $IMAGEHEIGHT / 2)
fi

RESOLUTION=""$IMAGEWIDTH"x"$IMAGEHEIGHT""

for part_cnt in 0 1
do
    mkdir -p $ANDROID_PRODUCT_OUT/obj/BOOTANIMATION/bootanimation/part$part_cnt
done
tar xfp "vendor/addons/prebuilt/bootanimation/bootanimation.tar" -C "$OUT/bootanimation/"
mogrify -resize $RESOLUTION -colors 250 "$OUT/bootanimation/"*"/"*".png"

# Create desc.txt
echo "$IMAGESCALEWIDTH $IMAGESCALEHEIGHT" 60 > "$OUT/bootanimation/desc.txt"
cat "vendor/addons/prebuilt/bootanimation/desc.txt" >> "$OUT/bootanimation/desc.txt"

# Create bootanimation.zip
cd "$OUT/bootanimation"

zip -qr0 "$OUT/bootanimation.zip" .
