package tech.lq0.utils

import kotlinx.serialization.json.Json
import java.io.File

/**
 * 根据组件名称和文件名读取配置文件
 * @param name 组件名称
 * @param file 文件名
 */
fun readConfig(name: String, file: String) = File("./lq2config/$name/$file").readText()

/**
 * 根据组件名称和文件名读取JSON配置文件
 * @param name 组件名称
 * @param file 文件名
 */
inline fun <reified T> readJSONConfigAs(name: String, file: String) = Json.decodeFromString<T>(readConfig(name, file))

/**
 * 根据组件名称和文件名保存配置文件
 * @param name 组件名称
 * @param file 文件名
 * @param content 文件内容
 */
fun saveConfig(name: String, file: String, content: String) = File("./lq2config/$name/$file").writeText(content)
