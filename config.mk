# Copyright (C) 2017-2018 crDroid Android Project
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
    Turbo \
    turbo.xml \
    privapp-permissions-turbo.xml \
    dialer_experience.xml \
    bootanimation.zip

PRODUCT_COPY_FILES += \
    vendor/addons/prebuilt/system/lib/libjni_latinimegoogle.so:system/lib/libjni_latinimegoogle.so

# Camera Effects for devices without a vendor partition
ifneq ($(filter shamu,$(TARGET_PRODUCT)),)
PRODUCT_COPY_FILES +=  \
    vendor/addons/prebuilt/media/LMspeed_508.emd:system/vendor/media/LMspeed_508.emd \
    vendor/addons/prebuilt/media/PFFprec_600.emd:system/vendor/media/PFFprec_600.emd
endif

DEVICE_PACKAGE_OVERLAYS += vendor/addons/overlay/common
