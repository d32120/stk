import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Throws(ExpiredAccessToken::class, ReloadCircleMembers::class)
fun dispatchPollers(
    client: HttpClient,
    circleId: String,
    authToken: String,
    members: List<Member>
) {
    val count = members.count()
    val delay = 150_000.milliseconds / count // 150 seconds, rounded to milliseconds, divided by the number of members
    val scope = CoroutineScope(Dispatchers.Default)
    for (i in 0..<count) {
        scope.dispatchPeriodicWork(
            delay = 150.seconds, startDelay = delay * i
        ) {
            val movement = getForceUpdatePosition(client, circleId, authToken, members[i].name, members[i].id)
            // When the function throws, every work is cancelled
            MovementTable.putMovement(movement)
        }
    }
}


suspend fun pollingLoop(client: HttpClient, circleId: String, accessToken: String, members: List<Member>) {
    debug{"Starting polling"}
    try {
        dispatchPollers(client, circleId, accessToken, members)
    } catch (_: ReloadCircleMembers) {
        val smembers = getCircleMembers(client, accessToken, circleId)
        if (smembers.isEmpty()) {
            error("No circle member found")
            return
        }
        pollingLoop(client, circleId, accessToken, smembers) //create new scopes

    } catch (_: ExpiredAccessToken) {
        val saccessToken = getAccessToken(client) ?: run {
            error("Wrong credentials")
            throw RuntimeException()
        }
        pollingLoop(client, circleId, saccessToken, members)
    }
}
