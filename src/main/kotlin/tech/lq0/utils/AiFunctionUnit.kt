package tech.lq0.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AiFunction(val description: String = "", val name: String = "")

@Serializable
data class FunctionCall(
    val name: String,
    val parameter: JsonObject? = null
)

data class FunctionInfo(
    val name: String,
    val description: String,
    val function: KFunction<*>,
    val bean: Any,
    val parameter: KParameter? = null,
)

private val registeredFunctions = mutableMapOf<String, FunctionInfo>()

// json格式的可用函数及参数列表
val availableFunctions by lazy {
    Json.encodeToString(buildJsonArray {
        registeredFunctions.forEach { (name, info) ->
            add(buildJsonObject {
                put("name", name)
                put("description", info.description)

                // TODO 处理有参数情况
            })
        }
    })
}

@OptIn(ExperimentalStdlibApi::class)
@Component
class AiFunctionScanner(private val applicationContext: ApplicationContext) {
    @AiFunction("发送功能无效提示，在无法识别请求时调用")
    suspend fun OneBotNormalGroupMessageEvent.invalid() {
        invalidCall()
    }

    @EventListener(ApplicationReadyEvent::class)
    fun processAnnotations() {
        val beans = applicationContext.getBeansWithAnnotation(Component::class.java)
        for (bean in beans.values) {
            bean::class.members.filterIsInstance<KFunction<*>>().forEach { function ->
                val annotation = function.findAnnotation<AiFunction>() ?: return@forEach

                val name = annotation.name.ifEmpty { function.name }
                val description = annotation.description

                val parameters = function.parameters

                require(parameters.size in 2..3) {
                    "${function.name} in ${bean::class.simpleName} has invalid parameter count!"
                }

                require(parameters.getOrNull(1)?.type?.isSubtypeOf(typeOf<OneBotNormalGroupMessageEvent>()) ?: false) {
                    "${function.name} in ${bean::class.simpleName} is not an extension function of OneBotNormalGroupMessageEvent!"
                }

                val param = parameters.getOrNull(2)

                if (param == null) {
                    val info = FunctionInfo(name, description, function, bean)
                    require(registeredFunctions.put(name, info) == null) {
                        "Function $name is already registered!"
                    }
                }

                // TODO 处理有参数情况
//                    val generator = SerializationClassJsonSchemaGenerator.Default
//                    val schema = generator.generateSchema(serializer(param.javaType).descriptor)
            }
        }
    }
}

suspend fun OneBotNormalGroupMessageEvent.invokeFunction(call: FunctionCall) {
    val (name, _) = call
    chatLogger.info("[AI Function call] calling function $name")
    val functionInfo = registeredFunctions[name] ?: run {
        invalidCall()
        return
    }

    if (functionInfo.parameter == null) {
        functionInfo.function.callSuspend(functionInfo.bean, this)
    }

    // TODO 处理有参数情况
}

suspend fun OneBotNormalGroupMessageEvent.invalidCall() {
    directlySend("无法识别要调用的功能，请输入/help查看帮助")
}