interface CommandListener {
    fun onCommand(chatId: Long, senderId: Int, msgId: Long, command: String, arg: String)
}