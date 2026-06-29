import io.ktor.client.*
import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

inline fun dispatchPeriodicWork(
    delay: Duration,
    startDelay: Duration = 0.milliseconds,
    crossinline body: suspend () -> Unit
): CoroutineScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    scope.launch {
        delay(startDelay)
        while (isActive) {
            try {
                body()
            } catch (e: Exception) {
                println(e.stackTraceToString())
            }
            delay(delay)
        }
    }
    return scope
}


suspend fun main() {
    info("Starting")
    createTable()
    val client=HttpClient()
    Config.printAllLoadedValues()
    val accessToken= getAccessToken(client) ?: run {
        error("Wrong credentials")
        throw RuntimeException()
    }
    val circleId = getCircleId(client,accessToken) //No exception, token just generated

    val members = getCircleMembers(client, accessToken,circleId)

    if (members.isEmpty()) {
        error("No circle member found")
        return
    }

    pollingLoop(client,circleId,accessToken,members)
}

class ExpiredAccessToken : Exception()
class ReloadCircleMembers : Exception()

@Throws(ExpiredAccessToken::class)
fun dispatchPollers(
    client: HttpClient,
    circleId: String,
    authToken: String,
    members: List<Member>
): List<CoroutineScope> {
    val count = members.count()
    val delay = 150_000.milliseconds / count // 150 seconds, rounded to milliseconds, divided by the number of members
    return (0..<count).map { i ->
        dispatchPeriodicWork(
            delay = 150.seconds, startDelay = delay * i
        ) {
            val movement = getForceUpdatePosition(client, circleId, authToken, members[i].name, members[i].id)
            MovementTable.putMovement(movement)
        }
    }
}


suspend fun pollingLoop(client:HttpClient,circleId:String,accessToken:String,members:List<Member>){
    var scopes = listOf<CoroutineScope>()
    try {
        scopes = dispatchPollers(client, circleId, accessToken, members)
    } catch (_:ReloadCircleMembers) {
        scopes.forEach { it.cancel() } //cancel old scopes
        val smembers = getCircleMembers(client, accessToken,circleId)
        if (smembers.isEmpty()) {
            error("No circle member found")
            return
        }
        pollingLoop(client,circleId,accessToken,smembers) //create new scopes

    } catch(_:ExpiredAccessToken){
        scopes.forEach { it.cancel() } //as before
        val saccessToken = getAccessToken(client) ?: run{
                error("Wrong credentials")
                throw RuntimeException()
            }
        pollingLoop(client,circleId,saccessToken,members)
    }
}

