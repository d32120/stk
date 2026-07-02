import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ExpiredAccessToken : Exception()
class ReloadCircleMembers : Exception()


const val DEBUG=false

inline fun debug(block:()->String){
    if(DEBUG){
        println(block())
    }
}

inline fun CoroutineScope.dispatchPeriodicWork(
    delay: Duration,
    startDelay: Duration = 0.milliseconds,
    crossinline body: suspend () -> Unit
) {
    launch {
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
}