import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


val db by lazy {
    Database.connect(
        url = Config.DB_URL,
        driver = Config.DB_DRIVER,
        user = Config.DB_USER,
        password = Config.DB_PASSWORD
    )
}

suspend fun <T : Table, O> T.dbQuery(block: T.() -> O): O = suspendTransaction(db) {
    block()
}

data class Member(val name: String, val id:String)
@Serializable
data class Movement(val tag:String,val start:Long,val end:Long?,val coordinates:Coordinates)

@Serializable
data class Coordinates(val x:Double,val y:Double)

object MovementTable: Table("movements"){
    private val tag= text("tag")
    private val start = long("start")
    private val end = long("end").nullable()
    private val coordx = double("coordx")
    private val coordy = double("coordy")

    suspend fun putMovement(movement: Movement){
        dbQuery {
            insert{
                it[tag] = movement.tag
                it[start] = movement.start
                it[end] = movement.end
                it[coordx] = movement.coordinates.x
                it[coordy] = movement.coordinates.y
            }
        }
    }
    suspend fun groupMovements(){
    }
}

fun createTable(){
    transaction(db) {
        SchemaUtils.create(MovementTable)
    }
}