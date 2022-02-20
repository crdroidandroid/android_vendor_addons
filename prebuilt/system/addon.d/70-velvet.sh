#!/sbin/sh
#
# ADDOND_VERSION=2
#
# /system/addon.d/70-velvet.sh
#
. /tmp/backuptool.functions

list_files() {
cat <<EOF
system_ext/priv-app/Velvet/Velvet.apk
EOF
}

# Find OUTFD like magisk does; for reference, check: https://github.com/topjohnwu/Magisk/blob/master/scripts/addon.d.sh
OUTFD=$(ps | grep -v 'grep' | grep -oE 'update(.*) 3 [0-9]+' | cut -d" " -f3)
[ -z $OUTFD ] && OUTFD=$(ps -Af | grep -v 'grep' | grep -oE 'update(.*) 3 [0-9]+' | cut -d" " -f3)
[ -z $OUTFD ] && OUTFD=$(ps | grep -v 'grep' | grep -oE 'status_fd=[0-9]+' | cut -d= -f2)
[ -z $OUTFD ] && OUTFD=$(ps -Af | grep -v 'grep' | grep -oE 'status_fd=[0-9]+' | cut -d= -f2)

ui_print() { echo -e "ui_print $1\nui_print" >> /proc/self/fd/$OUTFD; }

mount_extras() {
  local ab_device=$(getprop ro.build.ab_update)
  local dynamic_partition=$(getprop ro.boot.dynamic_partitions)
  if [ -z "$ab_device" ]; then
    for block in product system_ext vendor; do
      if [ -e /$block ]; then
        if [ "$dynamic_partition" = "true" ]; then
          mount -o ro -t auto /dev/block/mapper/$block /$block 2>/dev/null
          blockdev --setrw /dev/block/mapper/$block 2>/dev/null
          mount -o rw,remount -t auto /dev/block/mapper/$block /$block 2>/dev/null
        else
          mount -o ro -t auto /$block 2>/dev/null
          mount -o rw,remount -t auto /$block 2>/dev/null
        fi
      fi
    done
  fi
}

unmount_extras() {
  umount /product /system_ext /vendor 2>/dev/null
}

if [ -z $backuptool_ab ]; then
  SYS=$S
  TMP=/tmp
else
  SYS=/postinstall/system
  TMP=/postinstall/tmp
fi

case "$1" in
  backup)
    list_files | while read -r FILE DUMMY; do
      backup_file "$S"/"$FILE"
    done
    unmount_extras
  ;;
  restore)
    list_files | while read -r FILE REPLACEMENT; do
      R=""
      [ -n "$REPLACEMENT" ] && R="$S/$REPLACEMENT"
      [ -f "$C/$S/$FILE" ] && restore_file "$S"/"$FILE" "$R"
    done
  ;;
  pre-backup)
    mount_extras
  ;;
  pre-restore)
    mount_extras
  ;;
  post-restore)
    # Set permissions
    for i in $(list_files); do
      chown root:root "$SYS/$i"
      chmod 644 "$SYS/$i"
      chmod 755 "$(dirname "$SYS/$i")"
    done
    unmount_extras
    chmod 600 $SYS/build.prop
  ;;
esac
