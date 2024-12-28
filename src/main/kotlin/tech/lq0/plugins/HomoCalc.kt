package tech.lq0.plugins

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.interceptor.GroupSwitch
import kotlin.math.absoluteValue

@Component
class HomoCalc {

    @Listener
    @GroupSwitch("HomoCalc")
    @Filter("恶臭论证 {{count,-?\\d+}}")
    suspend fun OneBotMessageEvent.calc(
        @FilterValue("count") number: String,
    ) = reply(homoCalc(number))

    val binSet = mapOf(
        1073741824 to "114514*((1+1)*4514+(11*(45-14)+(11-4+5-1-4)))+(11*4514+(114*5*14+(-(1-14)*5*14+(11-4-5+14))))",
        536870912 to "114514*((1145+1)*4+(114-5-1-4))+(114*51*4+(114*51+4+(11*4*5-14)))",
        268435456 to "114514*(114*5*1*4+(11*4+5*1*4))+(1+14514+(-1-14*(5-14)))",
        134217728 to "114514*(1145+14+(1*14-5/1+4))+(1+14*514+(114-5+14))",
        67108864 to "114514*(114*5+14+(-11+4-5+14))+((1+1)*451*4+(11+45/1-4))",
        33554432 to "114514*(11+4*5*14+(-11+4-5+14))+(114*(5-1)*4+(1-14+5+14))",
        16777216 to "114514*(11+45*(-(1-4)))+(11*4514+(114*5*14+(1+14+514+(11-4+5+1-4))))",
        8388608 to "114514*(1*14*5-1+4)+(114*51*4+(114*51+4+(-11+4+5+14)))",
        4194304 to "114514*(11+4*5+1+4)+(114*514+(11451+4+(114*(-5)*(1-4)+(-11+45+1+4))))",
        2097152 to "114514*(1+1+4*5*1-4)+(114*51*4+(11451+4+(1145+14+(1*(-1)+45-14))))",
        1048576 to "114514*(11-4+5+1-4)+(1145*14+((11+451)*4+(-1-1+4+5*14)))",
        524288 to "114514*(-11-4+5+14)+(114*514+(1+14*514+((1+145)*(-(1-4))+(11/(45-1)*4))))",
        262144 to "114514*(-11+4-5+14)+(114*51*4+((1+1)*4514+(11+4*51*4+(11-4*5+14))))",
        131072 to "114514+(1145*14+(1*14+514))",
        65536 to "114*514+(11*45*14+(-11/4+51/4))",
        32768 to "114*51*4+((1+1)*4514+(11*45-14+(11*(-4)+51-4)))",
        16384 to "1145*14+((11-4)*51-4+(11/(45-1)*4))",
        8192 to "114*5*14+((1+1)*4+51*4)",
        4096 to "(1+1)*451*4+(11*(45-1)+4)",
        2048 to "-11+4*514+(11*(-4)+51-4)",
        1024 to "1*(1+4)*51*4+(-11-4+5+14)",
        512 to "1+1-4+514",
        256 to "(114-51)*4+(-11-4+5+14)",
        128 to "1+1+(4+5)*14",
        64 to "1-1+4*(5-1)*4",
        32 to "1+1*45-14",
        16 to "1+1+4+5+1+4",
        8 to "1+1+4+5+1-4",
        4 to "1+1+4-5-1+4",
        2 to "(-1)*(1+(4+5)/(1-4))",
        1 to "1+1*4*(5-1-4)",
    )

    fun homoCalc(numberStr: String): String {
        val num = numberStr.toIntOrNull() ?: return "在？你管这叫int32整数？"

        if (num == 0) return "0 = 1+1-4+5+1-4"
        val isNegative = num < 0
        val result = buildList {
            var temp = num.absoluteValue
            while (temp > 0) {
                for ((key, value) in binSet) {
                    if (key <= temp) {
                        add(value)
                        temp -= key
                        break
                    }
                }
            }
        }.joinToString("+")

        return if (isNegative) {
            "$numberStr = -($result)"
        } else {
            "$numberStr = $result"
        }
    }

}
