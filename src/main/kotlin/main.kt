import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration.Companion.days


class Application {

    companion object{
        @JvmStatic
    suspend fun main() {
    info("Starting")

    createTable()

    val client = HttpClient(CIO)
    if (DEBUG) {
        Config.printAllLoadedValues()
    }

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    scope.dispatchPeriodicWork(1.days, 1.days) {
        info("Starting grouping movements")
        MovementTable.groupMovements()
    }
    val accessToken = getAccessToken(client) ?: run {
        error("Wrong credentials")
        throw RuntimeException()
    }
    info("Access token: $accessToken")

    val circleId = getCircleId(client, accessToken) //No exception, token just generated
    info("Circle ID: $circleId")

    val members = getCircleMembers(client, accessToken, circleId)
    val strmembers = buildString {
        appendLine()
        members.forEach {
            appendLine("[${it.name},${it.id}]")
        }
    }
    info("Members: $strmembers")

    if (members.isEmpty()) {
        error("No circle member found")
        return
    }
    pollingLoop(client, circleId, accessToken, members)
}}
}


