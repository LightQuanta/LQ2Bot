package tech.lq0.utils

import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private fun getOrCreateConfig(componentName: String, fileName: String): File {
    val file = File("./lq2bot/config/$componentName/$fileName")
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
        println(("读取配置文件 $componentName/$fileName 出错: $e"))
        null
    }
}

/**
 * 根据组件名称和文件名保存配置文件
 * @param componentName 组件名称
 * @param fileName 文件名
 * @param content 文件内容
 * @param autoBackup 是否自动备份，默认开启
 */
fun saveConfig(componentName: String, fileName: String, content: String, autoBackup: Boolean = true) {
    if (autoBackup) {
        val fileNameWithoutExtension = fileName.substringBeforeLast(".")
        val fileExtension = fileName.substringAfterLast(".")

        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH_mm_ss")
        val formattedDateTime = currentDateTime.format(formatter)

        val fileNameWithTime = "$fileNameWithoutExtension - $formattedDateTime.$fileExtension"
        val backupFile = File("./lq2bot/backup/$componentName/$fileNameWithTime")
        if (!backupFile.exists()) {
            backupFile.parentFile.mkdirs()
            backupFile.createNewFile()
        }
        backupFile.writeText(getOrCreateConfig(componentName, fileName).readText())
    }
    getOrCreateConfig(componentName, fileName).writeText(content)
}
