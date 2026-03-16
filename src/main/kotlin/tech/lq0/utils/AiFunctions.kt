package tech.lq0.utils

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import org.springframework.stereotype.Component
import tech.lq0.plugins.*

@AiComponent
@Component
class AiFunctions {
    @AiFunction("发送功能无效提示，在无法识别请求时调用")
    suspend fun OneBotNormalGroupMessageEvent.invalid() {
        invalidCall()
    }

    @AiFunction("展示当前正在开播的主播列表")
    suspend fun OneBotNormalGroupMessageEvent.showLive() {
        showCurrentlyLive()
    }

    @AiFunction("显示该群订阅了哪些主播")
    suspend fun OneBotNormalGroupMessageEvent.showSubscribe() {
        showAllSubscribe()
    }

    @AiFunction("来点猫图，发送一张随机的猫图（若用户说的是哈基米，也将其视为猫）")
    suspend fun OneBotNormalGroupMessageEvent.randomCat() {
        sendCatPic()
    }

    @AiFunction("随机抽取一个虚拟主播（管人，vtb，vtuber）进行展示，该功能也称为今天看谁/现在看谁", "dd")
    suspend fun OneBotNormalGroupMessageEvent.randomDD() {
        dd()
    }

    @AiFunction("用户主动抽取的禁言抽奖，将用户随机禁言一段时间")
    suspend fun OneBotNormalGroupMessageEvent.mute() {
        randomMute(true)
    }
}

suspend fun OneBotNormalGroupMessageEvent.invalidCall() {
    directlySend("无法识别要调用的功能，请输入/help查看帮助")
}