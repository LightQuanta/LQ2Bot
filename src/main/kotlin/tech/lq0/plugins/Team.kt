package tech.lq0.plugins

import love.forte.simbot.component.onebot.v11.core.actor.OneBotMember
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.quantcat.common.annotations.ContentTrim
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.interceptor.RequireAdmin
import tech.lq0.utils.directlySend
import tech.lq0.utils.isSensitive
import java.time.LocalDate

@Component
class Team {

    data class SimpleMember(val name: String, val id: String)

    fun OneBotMember.toSimpleMember() = SimpleMember(name, id.toString())

    data class TeamInfo(val date: LocalDate, val members: MutableSet<SimpleMember>) {
        val isValid: Boolean
            get() {
                val now = LocalDate.now()!!
                return now.month == date.month && now.dayOfMonth == date.dayOfMonth
            }
    }

    val teams = mutableMapOf<Pair<String, String>, TeamInfo>()

    private fun getTeam(key: Pair<String, String>, author: OneBotMember): TeamInfo {
        val date = LocalDate.now()!!
        var team = teams.getOrPut(key) { TeamInfo(date, mutableSetOf(author.toSimpleMember())) }

        if (team.date.month != date.month || team.date.dayOfMonth != date.dayOfMonth) {
            // 跨日
            val newTeamInfo = TeamInfo(date, mutableSetOf(author.toSimpleMember()))
            teams[key] = newTeamInfo
            team = newTeamInfo
        }

        return team
    }

    @Listener
    @FunctionSwitch("Team")
    @ContentTrim
    @ChinesePunctuationReplace
    @Filter("""!(join|加入) {{name,.+}}""")
    suspend fun OneBotNormalGroupMessageEvent.join(@FilterValue("name") name: String) {
        val name = name.trimIndent().lowercase()
        if (name.isSensitive()) return

        val author = author()
        val team = getTeam(groupId.toString() to name, author)
        team.members += author.toSimpleMember()

        reply("加入${name}成功！")
    }

    @Listener
    @FunctionSwitch("Team")
    @ContentTrim
    @ChinesePunctuationReplace
    @Filter("""!(leave|quit|离开|退出) {{name,.+}}""")
    suspend fun OneBotNormalGroupMessageEvent.leave(@FilterValue("name") name: String) {
        val name = name.trimIndent().lowercase()
        if (name.isSensitive()) return

        val author = author()
        val team = getTeam(groupId.toString() to name, author)
        val simpleMember = author.toSimpleMember()

        if (simpleMember in team.members) {
            team.members -= simpleMember
            if (team.members.isEmpty()) {
                teams -= groupId.toString() to name
            }
            reply("退出${name}成功！")
        } else {
            reply("您不在${name}中！")
        }

    }

    @Listener
    @FunctionSwitch("Team")
    @ContentTrim
    @ChinesePunctuationReplace
    @Filter("!(teams|小队)")
    suspend fun OneBotNormalGroupMessageEvent.teams() {
        val teams = teams.filter {
            it.key.first == groupId.toString() && it.value.isValid
        }.map {
            val name = it.key.second
            val members = it.value.members
            "$name\n${members.joinToString { member -> "${member.name}(${member.id})" }}"
        }

        if (teams.isEmpty()) {
            directlySend("当前还没有队伍！")
        } else {
            directlySend("当前已有的队伍\n\n" + teams.joinToString("\n\n"))
        }
    }

    @Listener
    @FunctionSwitch("Team")
    @ContentTrim
    @ChinesePunctuationReplace
    @RequireAdmin
    @Filter("""!(disband|解散) {{name,.+}}""")
    suspend fun OneBotNormalGroupMessageEvent.disband(@FilterValue("name") name: String) {
        val name = name.trimIndent().lowercase()
        if (name.isSensitive()) return

        if (teams.remove(groupId.toString() to name) != null) {
            directlySend("解散${name}成功！")
        } else {
            directlySend("该队伍不存在！")
        }
    }

}