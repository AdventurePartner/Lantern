package org.lantern.uix.enums

enum class Anchor(val key: String, val mountPoint: MountPoint) {
    TOP_LEFT("top-left", MountPoint.MOUNT_1_FRONT),
    TOP_CENTER("top-center", MountPoint.MOUNT_1_MIDDLE),
    TOP_RIGHT("top-right", MountPoint.MOUNT_1_BACK),
    CENTER_LEFT("center-left", MountPoint.MOUNT_2_FRONT),
    CENTER("center", MountPoint.MOUNT_2_MIDDLE),
    CENTER_RIGHT("center-right", MountPoint.MOUNT_2_BACK),
    BOTTOM_LEFT("bottom-left", MountPoint.MOUNT_3_FRONT),
    BOTTOM_CENTER("bottom-center", MountPoint.MOUNT_3_MIDDLE),
    BOTTOM_RIGHT("bottom-right", MountPoint.MOUNT_3_BACK);

    companion object {
        private val BY_KEY = entries.associateBy { it.key }
        fun fromKey(key: String): Anchor? = BY_KEY[key]
    }
}
