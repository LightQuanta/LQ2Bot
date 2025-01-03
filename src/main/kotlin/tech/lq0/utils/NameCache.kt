package tech.lq0.utils

/**
 * 根据b站用户UID获取缓存的用户名称
 */
fun getUIDNameString(uid: String): String {
    return if (uid in UIDNameCache && uid !in sensitiveLivers) {
        "UID: $uid(${UIDNameCache[uid]})"
    } else {
        "UID: $uid"
    }
}