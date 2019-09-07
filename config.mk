# Copyright (C) 2017-2019 crDroid Android Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
LOCAL_PATH := $(call my-dir)
include $(call all-subdir-makefiles,$(LOCAL_PATH))

# Prebuilts
PRODUCT_PACKAGES += \
    dialer_experience.xml \
    google.xml \
    bootanimation.zip \
    SubstratumSignature \
    Longshot \
    org.pixelexperience.screenshot.xml

# Accents
PRODUCT_PACKAGES += \
    Amber \
    Black \
    Blue \
    BlueGrey \
    Brown \
    Cyan \
    DeepOrange \
    DeepPurple \
    Green \
    Grey \
    Indigo \
    LightBlue \
    LightGreen \
    Lime \
    Orange \
    Pink \
    Purple \
    Red \
    Teal \
    UserOne \
    UserTwo \
    UserThree \
    UserFour \
    UserFive \
    UserSix \
    UserSeven \
    Yellow \
    White

# Brand Accents
PRODUCT_PACKAGES += \
    AospaGreen \
    AndroidOneGreen \
    CocaColaRed \
    DiscordPurple \
    FacebookBlue \
    InstagramCerise \
    JollibeeCrimson \
    MonsterEnergyGreen \
    NextbitMint \
    OneplusRed \
    PepsiBlue \
    PocophoneYellow \
    RazerGreen \
    SamsungBlue \
    SpotifyGreen \
    StarbucksGreen \
    TwitchPurple \
    TwitterBlue \
    XboxGreen \
    XiaomiOrange

# Themes
PRODUCT_PACKAGES += \
    GBoardDark \
    GoogleIntelligenceSenseDark \
    NotificationDark \
    SettingsDark \
    SettingsIntelligenceDark \
    SystemDark \
    SysUIDark \
    WellbeingDark \
    GBoardBlack \
    GoogleIntelligenceSenseBlack \
    NotificationBlack \
    SettingsBlack \
    SettingsIntelligenceBlack \
    SystemBlack \
    SysUIBlack \
    WellbeingBlack

# QS tile styles
PRODUCT_PACKAGES += \
    QStileDefault \
    QStileCircleTrim \
    QStileCircleDualTone \
    QStileCircleGradient \
    QStileCookie \
    QStileCosmos \
    QStileDividedCircle \
    QStileDottedCircle \
    QStileDualToneCircle \
    QStileInk \
    QStileInkdrop \
    QStileJustIcons \
    QStileMountain \
    QStileNeonLike \
    QStileNinja \
    QStileOreo \
    QStileOreoCircleTrim \
    QStileOreoSquircleTrim \
    QSTileOxygen \
    QStilePokesign \
    QStileSquaremedo \
    QStileSquircle \
    QStileSquircleTrim \
    QStileTeardrop \
    QStileTriangle \
    QStileWavey

# QS header styles
PRODUCT_PACKAGES += \
    QSHeaderBlack \
    QSHeaderGrey \
    QSHeaderLightGrey \
    QSHeaderAccent \
    QSHeaderTransparent

# Switch styles
PRODUCT_PACKAGES += \
    MD2Switch \
    OnePlusSwitch \
    StockSwitch \
    Contained \
    Retro \
    Stockish \
    Narrow

# Cutout control overlays
PRODUCT_PACKAGES += \
    HideCutout \
    StatusBarStock

PRODUCT_COPY_FILES += \
    vendor/addons/prebuilt/system/lib/libjni_latinimegoogle.so:system/lib/libjni_latinimegoogle.so

DEVICE_PACKAGE_OVERLAYS += vendor/addons/overlay/common
