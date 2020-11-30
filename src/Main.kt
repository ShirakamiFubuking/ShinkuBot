import com.google.gson.Gson
import org.drinkless.tdlib.TdApi
import java.io.File
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.HashMap

val gson = Gson()
fun main(args: Array<String>) {
    // 載入外部函式庫tdjni
    try {
        System.loadLibrary("tdjni")
    } catch (e: UnsatisfiedLinkError) {
        e.printStackTrace()
    }

    val bot = Bot(args[0].toInt(),args[1],args[2], "")
    val matchGoodNight = Regex("""(?:晚安)""")

    // 紀錄每個人上次說晚安時間的Map
    val list = HashMap<Int, Int>()
    val nameMap = run {
        try {
            val f = File(USERNAME_FILE)
            return@run gson.fromJson(f.readText(Charset.forName("UTF-8")), HashMap<String, String>().javaClass)
        } catch (e: Exception) {
        }
        return@run HashMap<String, String>()
    }
    bot.addInterest(TdApi.UpdateNewMessage.CONSTRUCTOR) {
        val newMessage = it as TdApi.UpdateNewMessage
        if (newMessage.message.content.constructor == TdApi.MessageText.CONSTRUCTOR) {
            val msgText = newMessage.message.content as TdApi.MessageText
            if (matchGoodNight.containsMatchIn(msgText.text.text)) {
                if (newMessage.message.sender.constructor == TdApi.MessageSenderUser.CONSTRUCTOR) {
                    val senderId = (newMessage.message.sender as TdApi.MessageSenderUser).userId
                    val lastDate = list[senderId]
                    val nowDate = Date(System.currentTimeMillis()).date
                    val chatId = newMessage.message.chatId
                    val msgId = newMessage.message.id
                    bot.client.send(TdApi.GetUser(senderId)) { usr ->
                        val user = usr as TdApi.User
                        var userName = user.firstName + user.lastName
                        nameMap[senderId.toString()]?.let {
                            userName = nameMap[senderId.toString()]!!
                        }
                        if (lastDate == nowDate) {
                            val content: TdApi.InputMessageContent = TdApi.InputMessageText(TdApi.FormattedText("請快去睡覺", null), false, true)
                            bot.client.send(TdApi.SendMessage(chatId, 0, msgId, null, null, content)) {
                            }
                        } else {
                            val content: TdApi.InputMessageContent = TdApi.InputMessageText(TdApi.FormattedText("願明天對$userName" + "來說也是美好的一天", null), false, true)
                            bot.client.send(TdApi.SendMessage(chatId, 0, msgId, null, null, content)) {
                            }
                            list[senderId] = nowDate
                        }
                    }
                }
            }
        }
    }

    // 註冊bot指令
    bot.addInterest(Interest(TdApi.UpdateNewMessage.CONSTRUCTOR) {
        val newMessage = it as TdApi.UpdateNewMessage
        if (newMessage.message.content.constructor == TdApi.MessageText.CONSTRUCTOR) {
            val msgText = newMessage.message.content as TdApi.MessageText
            for (i in msgText.text.entities) {
                if (i.type.constructor == TdApi.TextEntityTypeBotCommand.CONSTRUCTOR) {
                    val cmd = msgText.text.text.subSequence(0, i.length)
                    val arg = msgText.text.text.subSequence(i.length, msgText.text.text.length).trim()
                    val senderId = (newMessage.message.sender as TdApi.MessageSenderUser).userId
                    when (cmd) {
                        "/myname" -> {
                            if (arg.isEmpty()) {
                                val content: TdApi.InputMessageContent = TdApi.InputMessageText(TdApi.FormattedText("不明白", null), false, true)
                                bot.client.send(TdApi.SendMessage(newMessage.message.chatId, 0, newMessage.message.id, null, null, content)) {
                                }
                            } else {
                                nameMap[senderId.toString()] = arg.toString()
                                val content: TdApi.InputMessageContent = TdApi.InputMessageText(TdApi.FormattedText("OK", null), false, true)
                                bot.client.send(TdApi.SendMessage(newMessage.message.chatId, 0, newMessage.message.id, null, null, content)) {
                                }
                                val f = File("user.json")
                                if (!f.exists()) {
                                    f.createNewFile()
                                }
                                f.writeText(gson.toJson(nameMap), Charset.forName("utf-8"))
                            }
                        }
                    }
                }
            }
        }
    })
    bot.start()
    while (!bot.isClosed()){
        Thread.sleep(1000)
    }
}