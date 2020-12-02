import com.google.gson.Gson
import org.drinkless.tdlib.TdApi.*
import java.io.File
import java.nio.charset.Charset
import java.util.Date
import kotlin.collections.HashMap

val gson = Gson()
fun main(args: Array<String>) {
    // 載入外部函式庫tdjni
    try {
        System.loadLibrary("tdjni")
    } catch (e: UnsatisfiedLinkError) {
        e.printStackTrace()
    }

    val bot = Bot(args[0].toInt(), args[1], args[2], "")
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
    with(bot) {
        addInterest(UpdateNewMessage.CONSTRUCTOR) {
            val newMessage = it as UpdateNewMessage
            if (newMessage.message.content.constructor == MessageText.CONSTRUCTOR) {
                val msgText = newMessage.message.content as MessageText
                if (matchGoodNight.containsMatchIn(msgText.text.text)) {
                    if (newMessage.message.sender.constructor == MessageSenderUser.CONSTRUCTOR) {
                        val senderId = (newMessage.message.sender as MessageSenderUser).userId
                        val lastDate = list[senderId]
                        val nowDate = Date(System.currentTimeMillis()).date
                        val chatId = newMessage.message.chatId
                        val msgId = newMessage.message.id
                        client.send(GetUser(senderId)) { usr ->
                            val user = usr as User
                            var userName = user.firstName + user.lastName
                            nameMap[senderId.toString()]?.let {
                                userName = nameMap[senderId.toString()]!!
                            }
                            val content: InputMessageContent
                            if (lastDate == nowDate) {
                                content = InputMessageText(FormattedText("請快去睡覺", null), false, true)
                            } else {
                                content = InputMessageText(FormattedText("願明天對${userName}來說也是美好的一天", null), false, true)
                                list[senderId] = nowDate
                            }
                            client.send(SendMessage(chatId, 0, msgId, null, null, content), null)
                        }
                    }
                }
            }
        }
        // 註冊bot指令
        addCommandListener(object : CommandListener {
            override fun onCommand(chatId: Long, senderId: Int, msgId: Long, command: String, arg: String) {
                if (command == "/myname") {
                    val content: InputMessageContent
                    if (arg.isEmpty()) {
                        content = InputMessageText(FormattedText("不明白", null), false, true)
                    } else {
                        nameMap[senderId.toString()] = arg
                        content = InputMessageText(FormattedText("OK", null), false, true)
                        val f = File(USERNAME_FILE)
                        if (!f.exists()) {
                            f.createNewFile()
                        }
                        f.writeText(gson.toJson(nameMap), Charset.forName("utf-8"))
                    }
                    client.send(SendMessage(chatId, 0, msgId, null, null, content), null)
                }
            }
        })
        start()
    }
    while (!bot.isClosed()) {
        Thread.sleep(1000)
    }
}