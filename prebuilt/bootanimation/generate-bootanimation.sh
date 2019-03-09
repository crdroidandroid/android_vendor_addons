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
IMAGESCALEHEIGHT=$(expr $IMAGESCALEWIDTH \* 16 / 9)
BOOTFPS=40

if [ "$HALF_RES" = "true" ]; then
    IMAGEWIDTH=$(expr $IMAGEWIDTH / 2)
    BOOTFPS=30
fi

IMAGEHEIGHT=$(expr $IMAGEWIDTH \* 16 / 9)
RESOLUTION=""$IMAGEWIDTH"x"$IMAGEHEIGHT""

rm -rf $OUT >> null

for part_cnt in 0 1 2
do
    mkdir -p $OUT/bootanimation/part$part_cnt
done

if [ "$IMAGESCALEWIDTH" -ge 1440 ]; then
    tar xfp "vendor/addons/prebuilt/bootanimation/bootanimation_1440.tar" -C "$OUT/bootanimation/"
elif [ "$IMAGESCALEWIDTH" -ge 1080 ]; then
    tar xfp "vendor/addons/prebuilt/bootanimation/bootanimation_1080.tar" -C "$OUT/bootanimation/"
else
    tar xfp "vendor/addons/prebuilt/bootanimation/bootanimation_720.tar" -C "$OUT/bootanimation/"
fi

#mogrify -resize $RESOLUTION -colors 250 "$OUT/bootanimation/"*"/"*".png"

# Create desc.txt
touch $OUT/bootanimation/desc.txt
echo "$IMAGESCALEWIDTH $IMAGESCALEHEIGHT $BOOTFPS" > "$OUT/bootanimation/desc.txt"
cat "vendor/addons/prebuilt/bootanimation/desc.txt" >> "$OUT/bootanimation/desc.txt"

# Create bootanimation.zip
cd "$OUT/bootanimation"

zip -qr0 "$OUT/bootanimation.zip" .

