# Copyright (C) 2017 crDroid Android Project
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

ifneq ($(TARGET_SCREEN_WIDTH) $(TARGET_SCREEN_HEIGHT),$(space))
# determine the smaller dimension
TARGET_BOOTANIMATION_SIZE := $(shell \
  if [ "$(TARGET_SCREEN_WIDTH)" -lt "$(TARGET_SCREEN_HEIGHT)" ]; then \
    echo $(TARGET_SCREEN_WIDTH); \
  else \
    echo $(TARGET_SCREEN_HEIGHT); \
  fi )

# determine the bigger dimension
TARGET_BOOTANIMATION_SIZE_ALT := $(shell \
  if [ "$(TARGET_SCREEN_WIDTH)" -gt "$(TARGET_SCREEN_HEIGHT)" ]; then \
    echo $(TARGET_SCREEN_WIDTH); \
  else \
    echo $(TARGET_SCREEN_HEIGHT); \
  fi )

# first try matching and use smaller dimension bootanimation
ifneq ($(filter 480 720 1080 1440,$(TARGET_BOOTANIMATION_SIZE)),)
PRODUCT_BOOTANIMATION := vendor/addons/prebuilt/bootanimation/$(TARGET_BOOTANIMATION_SIZE).zip
else
# if not try matching and use bigger dimension bootanimation
ifneq ($(filter 480 720 1080 1440,$(TARGET_BOOTANIMATION_SIZE_ALT)),)
PRODUCT_BOOTANIMATION := vendor/addons/prebuilt/bootanimation/$(TARGET_BOOTANIMATION_SIZE_ALT).zip
else
# if not found use default bootanimation
PRODUCT_BOOTANIMATION := vendor/addons/prebuilt/bootanimation/bootanimation.zip
endif
endif

else
PRODUCT_BOOTANIMATION := vendor/addons/prebuilt/bootanimation/bootanimation.zip
endif
