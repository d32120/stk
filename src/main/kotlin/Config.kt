import io.github.cdimascio.dotenv.dotenv
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Config {
    private val env = dotenv()
    val DB_URL: String = env["dbUrl"]
    val DB_USER: String = env["dbUser"]
    val DB_PASSWORD: String = env["dbPassword"]
    val DB_DRIVER: String = env["dbDriver"]
    val LIFE_USERNAME: String = env["lifeUsername"]
    val LIFE_PASSWORD: String = env["lifePassword"]
    val CIRCLE_NAME: String = env["circleName"]
    val LOGGER_NAME: String = env["loggerName"]

    val CONSOLE_LOGGER: String = env["consoleLogger"]

    fun printAllLoadedValues() {
        info(buildString{
            appendLine("Loaded values:")
            val entries= env.entries()
            val maxKey = entries.maxBy { it.key.length }.key.length
            for (entry in entries) {
                append(" ".repeat(4))
                append(entry.key)
                append(" ".repeat(maxKey-entry.key.length))
                append("= ")
                appendLine(entry.value)
            }
        }
        )

    }
}


private val loggers: List<Logger> by lazy {
    listOf(
        LoggerFactory.getLogger(Config.LOGGER_NAME), LoggerFactory.getLogger(Config.CONSOLE_LOGGER)
    )
}

fun info(msg: String) {
    loggers.forEach { logger ->
        logger.info(msg)
    }
}

fun error(msg: String) {
    loggers.forEach { logger ->
        logger.error(msg)
    }
}

fun error(msg: String, error: Throwable) {
    loggers.forEach { logger ->
        logger.error(msg, error)
    }
}