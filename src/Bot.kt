import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Example class for TDLib usage from Java.
 */
fun printErr(string: Any) {
    System.err.print(string)
}

fun printlnErr(string: Any) {
    System.err.println(string)
}

val newLine: String = System.getProperty("line.separator")

class Bot constructor(private val api_id: Int, private val api_hash: String, private val phoneNumber: String, private val password: String) {
    lateinit var client: Client
    lateinit var me: User
    private lateinit var authorizationState: AuthorizationState
    private val authorizationLock = ReentrantLock()
    private val gotAuthorization = authorizationLock.newCondition()

    // 感興趣的handler清單
    private val handlerList = mutableListOf<Interest>()

    @Volatile
    private var haveAuthorization = false

    @Volatile
    private var needQuit = false

    @Volatile
    private var canQuit = false

    private val authorizationRequestHandler = Client.ResultHandler {
        when (it.constructor) {
            Error.CONSTRUCTOR -> {
                println("Receive an error:$newLine$it")
                onAuthorizationStateUpdated(null) // repeat last action
            }
            Ok.CONSTRUCTOR -> {
            }
            else -> printlnErr("Receive wrong response from TDLib:$newLine$it")
        }
    }

    private fun promptString(prompt: String): String {
        print(prompt)
        val reader = BufferedReader(InputStreamReader(System.`in`))
        try {
            return reader.readLine()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return ""
    }

    /**
     * 註冊感興趣的handler
     */
    fun addInterest(interest: Interest) {
        handlerList.add(interest)
    }

    fun addInterest(updateConstructor: Int, handler: Client.ResultHandler) {
        handlerList.add(Interest(updateConstructor, handler))
    }

    private val updateHandler = Client.ResultHandler {
        when (it.constructor) {
            UpdateAuthorizationState.CONSTRUCTOR -> onAuthorizationStateUpdated((it as UpdateAuthorizationState).authorizationState)
            else -> {
                // 處理感興趣的update
                for (inst in handlerList) {
                    when (it.constructor) {
                        inst.updatesConstructor -> inst.handler.onResult(it)
                    }
                }
            }
        }
    }

    fun start() {
        Client.execute(SetLogVerbosityLevel(0))
        if (Client.execute(SetLogStream(LogStreamFile("tdlib.log", 1 shl 8, false))) is Error) {
            throw Error("Write access to the current directory is required")
        }
        // create client
        client = Client.create(updateHandler, null, null)
    }

    fun isClosed(): Boolean {
        return canQuit
    }

    private fun onAuthorizationStateUpdated(authorizationState: AuthorizationState?) {
        authorizationState?.let {
            this.authorizationState = it
        }
        when (authorizationState?.constructor) {
            AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                val parameters = TdlibParameters()
                with(parameters) {
                    databaseDirectory = "tdlib"
                    useMessageDatabase = true
                    useSecretChats = true
                    apiId = api_id
                    apiHash = api_hash
                    systemLanguageCode = "en"
                    deviceModel = "Desktop"
                    applicationVersion = "1.0"
                    enableStorageOptimizer = true
                }
                client.send(SetTdlibParameters(parameters), authorizationRequestHandler)
            }
            AuthorizationStateWaitEncryptionKey.CONSTRUCTOR -> client.send(CheckDatabaseEncryptionKey(), authorizationRequestHandler)
            AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                if (phoneNumber.length > 20) { // greater than 20 is bot token
                    client.send(CheckAuthenticationBotToken(phoneNumber), authorizationRequestHandler)
                } else {
                    client.send(SetAuthenticationPhoneNumber(phoneNumber, null), authorizationRequestHandler)
                }
            }
            AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR -> {
                val link = (authorizationState as AuthorizationStateWaitOtherDeviceConfirmation).link
                println("Please confirm this login link on another device: $link")
            }
            AuthorizationStateWaitCode.CONSTRUCTOR -> {
                val code = promptString("Please enter authentication code: ")
                client.send(CheckAuthenticationCode(code), authorizationRequestHandler)
            }
            AuthorizationStateWaitRegistration.CONSTRUCTOR -> {
                val firstName = promptString("Please enter your first name: ")
                val lastName = promptString("Please enter your last name: ")
                client.send(RegisterUser(firstName, lastName), authorizationRequestHandler)
            }
            AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                client.send(CheckAuthenticationPassword(password), authorizationRequestHandler)
            }
            AuthorizationStateReady.CONSTRUCTOR -> {
                haveAuthorization = true
                authorizationLock.lock()
                try {
                    gotAuthorization.signal()
                } finally {
                    authorizationLock.unlock()
                }
            }
            AuthorizationStateLoggingOut.CONSTRUCTOR -> {
                haveAuthorization = false
                print("Logging out")
            }
            AuthorizationStateClosing.CONSTRUCTOR -> {
                haveAuthorization = false
                print("Closing")
            }
            AuthorizationStateClosed.CONSTRUCTOR -> {
                print("Closed")
                if (!needQuit) {
                    client = Client.create(updateHandler, null, null) // recreate client after previous has closed
                } else {
                    canQuit = true
                }
            }
            else -> printlnErr("Unsupported authorization state:$newLine$authorizationState")
        }
    }
}