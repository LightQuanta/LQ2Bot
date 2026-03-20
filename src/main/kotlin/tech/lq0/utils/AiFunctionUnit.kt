package tech.lq0.utils

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.ResponseFormatText
import com.openai.models.chat.completions.*
import kotlinx.schema.generator.json.ReflectionClassJsonSchemaGenerator
import kotlinx.schema.json.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import tech.lq0.config.OpenAIProperties
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.interceptor.RequireAdmin
import tech.lq0.interceptor.RequireBotAdmin
import tech.lq0.interceptor.functionEnabled
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AiFunction(val description: String = "", val name: String = "")

@Serializable
data class FunctionCall(
    val name: String,
    val parameter: JsonObject? = null
) {
    companion object {
        val INVALID = FunctionCall("invalid")

        fun parse(resp: String?, useJsonFormat: Boolean = false): FunctionCall {
            if (resp == null) return INVALID

            return if (!useJsonFormat) {
                FunctionCall(resp)
            } else {
                try {
                    return Json.decodeFromString<FunctionCall>(resp)
                } catch (e: Exception) {
                    chatLogger.warn("Invalid function call: $e")
                    INVALID
                }
            }
        }
    }
}

data class FunctionInfo(
    val name: String,
    val description: String,
    val permission: CommandPermission,
    val functionSwitch: FunctionSwitch? = null,
    val function: KFunction<*>,
    val bean: Any,
)

private val registeredFunctions = mutableMapOf<String, FunctionInfo>()

// json格式的可用函数及参数列表
val availableFunctions by lazy {
    Json.encodeToString(buildJsonArray {
        registeredFunctions.forEach { (name, info) ->
            add(buildJsonObject {
                put("name", name)
                put("description", info.description)
            })
        }
    })
}

@OptIn(ExperimentalStdlibApi::class)
@Component
class AiFunctionScanner(private val applicationContext: ApplicationContext) {
    @EventListener(ApplicationReadyEvent::class)
    fun processAnnotations() {
        val beans = applicationContext.getBeansWithAnnotation(Component::class.java)
        for (bean in beans.values) {
            bean::class.members.filterIsInstance<KFunction<*>>().forEach { function ->
                val annotation = function.findAnnotation<AiFunction>() ?: return@forEach

                val name = annotation.name.ifEmpty { function.name }
                val description = annotation.description

                val permission = if (function.findAnnotation<RequireBotAdmin>() != null) {
                    CommandPermission.BOT_ADMIN
                } else if (function.findAnnotation<RequireAdmin>() != null) {
                    CommandPermission.ADMIN
                } else {
                    CommandPermission.ALL
                }

                val functionSwitchName = function.findAnnotation<FunctionSwitch>()

                val parameters = function.parameters

                require(parameters.size in 2..3) {
                    "${function.name} in ${bean::class.simpleName} has invalid parameter count!"
                }

                require(parameters.getOrNull(1)?.type?.isSubtypeOf(typeOf<OneBotNormalGroupMessageEvent>()) ?: false) {
                    "${function.name} in ${bean::class.simpleName} is not an extension function of OneBotNormalGroupMessageEvent!"
                }

                val param = parameters.getOrNull(2)

                if (param == null) {
                    val info = FunctionInfo(name, description, permission, functionSwitchName, function, bean)
                    require(registeredFunctions.put(name, info) == null) {
                        "Function $name is already registered!"
                    }
                }
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

    if (!hasPermission(this, functionInfo.permission)) {
        directlySend("权限不足，需要${functionInfo.permission.description}权限")
        return
    }

    if (!functionEnabled(this, functionInfo.functionSwitch)) {
        directlySend("该群未启用该功能！")
        return
    }

    functionInfo.function.callSuspend(functionInfo.bean, this)
}

suspend fun OneBotNormalGroupMessageEvent.invalidCall() {
    directlySend("无法识别要调用的功能，请访问 https://docs.lq0.tech/bot 查看帮助")
}

class PromptBuilder {
    private val params = mutableListOf<ChatCompletionMessageParam>()

    fun append(param: List<ChatCompletionMessageParam>) {
        params += param
    }

    fun system(prompt: String) {
        params += ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder().content(prompt).build()
        )
    }

    fun user(prompt: String) {
        params += ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam.builder().content(prompt).build()
        )
    }

    fun build() = params.toList()
}

fun buildPrompt(builder: PromptBuilder.() -> Unit) = PromptBuilder().apply(builder).build()

fun createOpenAIClient(config: OpenAIProperties): OpenAIClient {
    return OpenAIOkHttpClient.builder()
        .apiKey(config.apiKey)
        .baseUrl(config.endpoint)
        .timeout(config.timeout.milliseconds.toJavaDuration())
        .build()
}

private val responseCache = object : LinkedHashMap<List<ChatCompletionMessageParam>, String?>() {
    override fun removeEldestEntry(eldest: Map.Entry<List<ChatCompletionMessageParam>, String?>): Boolean {
        return size > 100
    }
}

inline fun <reified T : Any> OpenAIClient.getJsonResponse(
    config: OpenAIProperties,
    prompt: List<ChatCompletionMessageParam>,
): T? {
    return Json.decodeFromString<T>(sendChat(config, prompt, true) ?: return null)
}

fun OpenAIClient.sendChat(
    config: OpenAIProperties,
    prompt: List<ChatCompletionMessageParam>,
    useJsonFormat: Boolean = false,
): String? {
    if (prompt in responseCache) {
        return responseCache[prompt]
    }

    val builder = ChatCompletionCreateParams.builder()
        .model(ChatModel.of(config.model))
        .messages(prompt)
        .maxCompletionTokens(config.maxTokens)

    if (useJsonFormat) {
        builder.responseFormat(ResponseFormatText.builder().type(JsonValue.from("json_object")).build())
    }

    return chat().completions().create(builder.build()).getText().also { responseCache[prompt] = it }
}

fun ChatCompletion.getText() = choices()
    .firstOrNull()
    ?.message()
    ?.content()
    ?.getOrNull()

val schemaCache = mutableMapOf<KClass<*>, String>()

inline fun <reified T : Any> generateSchema(): String {
    if (T::class in schemaCache) {
        return schemaCache[T::class]!!
    }

    val generator = ReflectionClassJsonSchemaGenerator.Default
    return generator.generateSchema(T::class).encodeToString().also { schemaCache[T::class] = it }
}