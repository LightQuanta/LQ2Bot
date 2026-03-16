package tech.lq0.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
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

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AiComponent

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
class AiFunctionScanner(private val applicationContext: ApplicationContext) : InitializingBean {

    override fun afterPropertiesSet() {
        val beans = applicationContext.getBeansWithAnnotation(AiComponent::class.java)
        for (bean in beans.values) {
            bean::class.members.filterIsInstance<KFunction<*>>().forEach { function ->
                val annotation = function.findAnnotation<AiFunction>() ?: return@forEach

                val name = annotation.name.ifEmpty { function.name }
                val description = annotation.description

                val parameters = function.parameters
                require(
                    parameters.getOrNull(1)?.type?.isSubtypeOf(typeOf<OneBotNormalGroupMessageEvent>()) ?: false
                ) { "Only extension function of OneBotNormalGroupMessageEvent is supported!" }

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