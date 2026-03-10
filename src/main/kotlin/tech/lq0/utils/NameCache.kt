package tech.lq0.utils

/**
 * 根据b站用户UID获取缓存的用户名称
 */
fun getUIDNameString(uid: String) = if (
    uid in UIDNameCache.get()
    && uid !in sensitiveLivers.get()
    && !UIDNameCache.get()[uid]!!.isSensitive()
) "UID: $uid(${UIDNameCache.get()[uid]})" else "UID: $uid"