package tech.lq0.utils

import kotlinx.serialization.json.Json
import java.io.File

private fun getOrCreateConfig(componentName: String, fileName: String): File {
    val file = File("./lq2config/$componentName/$fileName")
    if (!file.exists()) {
        file.parentFile.mkdirs()
        file.createNewFile()
    }
    return file
}

/**
 * 根据组件名称和文件名读取配置文件
 * @param componentName 组件名称
 * @param fileName 文件名
 */
fun readConfig(componentName: String, fileName: String) = getOrCreateConfig(componentName, fileName).readText()

/**
 * 根据组件名称和文件名读取JSON配置文件
 * @param componentName 组件名称
 * @param fileName 文件名
 */
inline fun <reified T> readJSONConfigAs(componentName: String, fileName: String): T? {
    return try {
        Json.decodeFromString<T>(readConfig(componentName, fileName))
    } catch (e: Exception) {
        null
    }
}

/**
 * 根据组件名称和文件名保存配置文件
 * @param componentName 组件名称
 * @param fileName 文件名
 * @param content 文件内容
 */
fun saveConfig(componentName: String, fileName: String, content: String) =
    getOrCreateConfig(componentName, fileName).writeText(content)
