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
class Bot constructor(val api_id: Int, val api_hash: String, val phoneNumber: String, val password: String) {
    lateinit var client: Client
    private var authorizationState: AuthorizationState? = null
    private val newLine = System.getProperty("line.separator")
    private val authorizationLock: Lock = ReentrantLock()
    private val gotAuthorization = authorizationLock.newCondition()

    // 感興趣的handler清單
    val handlerList = mutableListOf<Interest>()

    @Volatile
    private var haveAuthorization = false

    @Volatile
    private var needQuit = false

    @Volatile
    private var canQuit = false

    private val authorizationRequestHandler = Client.ResultHandler {
        when (it.constructor) {
            Error.CONSTRUCTOR -> {
                System.err.println("Receive an error:$newLine$it")
                onAuthorizationStateUpdated(null) // repeat last action
            }
            Ok.CONSTRUCTOR -> {
            }
            else -> System.err.println("Receive wrong response from TDLib:$newLine$it")
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

    // 處理感興趣的update
    private val updateHandler = Client.ResultHandler {
        when (it.constructor) {
            UpdateAuthorizationState.CONSTRUCTOR -> onAuthorizationStateUpdated((it as UpdateAuthorizationState).authorizationState)
            else -> {
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

    private fun onAuthorizationStateUpdated(authorizationState: AuthorizationState?) {
        if (authorizationState != null) {
            this.authorizationState = authorizationState
        }
        when (authorizationState?.constructor) {
            AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                val parameters = TdlibParameters()
                parameters.databaseDirectory = "tdlib"
                parameters.useMessageDatabase = true
                parameters.useSecretChats = true
                parameters.apiId = api_id
                parameters.apiHash = api_hash
                parameters.systemLanguageCode = "en"
                parameters.deviceModel = "Desktop"
                parameters.applicationVersion = "1.0"
                parameters.enableStorageOptimizer = true
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
            else -> System.err.println("Unsupported authorization state:$newLine$authorizationState")
        }
    }
}