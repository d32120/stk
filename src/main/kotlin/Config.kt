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


    val SPACE_PRECISION:Double = env["spacePrecision"].toDouble()
    val TIME_PRECISION_MILLIS:Double = env["timePrecisionMillis"].toDouble()


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


private val logger by lazy {
    LoggerFactory.getLogger(Config.LOGGER_NAME)
}

fun info(msg: String) {
    logger.info(msg)

}

fun error(msg: String) {
    logger.error(msg)
}