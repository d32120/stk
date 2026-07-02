import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.math.abs


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
data class Movement(val tag:String,val start:Long,val end:Long,val coordinates:Coordinates)

@Serializable
data class Coordinates(val x:Double,val y:Double)

object MovementTable: Table("movements"){
    val id = integer("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)
    private val tag= text("tag")
    private val start = long("start")
    private val end = long("end")
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
    suspend fun groupMovements() {
        dbQuery {
            // Recupera tutti i movimenti ordinati per tag, coordinate e start time
            val movements = selectAll()
                .orderBy(tag to SortOrder.ASC, coordx to SortOrder.ASC, coordy to SortOrder.ASC, start to SortOrder.ASC)
                .map { row ->
                    MovementRecord(
                        id = row[id],
                        tag = row[tag],
                        start = row[start],
                        end = row[end],
                        coordx = row[coordx],
                        coordy = row[coordy]
                    )
                }
                .toList()

            if (movements.isEmpty()) return@dbQuery

            // Raggruppa i movimenti consecutivi
            val groups = mutableListOf<MovementGroup>()

            val iterator = movements.iterator()
            if(!iterator.hasNext()){
                return@dbQuery
            }
            val movement = iterator.next()
            var currentGroup = MovementGroup(
                idsToDelete = mutableListOf(),
                tag = movement.tag,
                coordx = movement.coordx,
                coordy = movement.coordy,
                start = movement.start,
                end = movement.end,
                firstId = movement.id
            )
            iterator.forEach { movement ->
                    val last = currentGroup
                    val coordxDiff = abs(movement.coordx - last.coordx)
                    val coordyDiff = abs(movement.coordy - last.coordy)
                    val timeDiff = abs(movement.start - last.end)

                    // Verifica se il movimento appartiene al gruppo corrente
                    if (movement.tag == last.tag &&
                        coordxDiff <= Config.SPACE_PRECISION &&
                        coordyDiff <= Config.SPACE_PRECISION &&
                        timeDiff <= Config.TIME_PRECISION_MILLIS
                    ) {
                        // Estendi il gruppo
                        last.end = maxOf(last.end, movement.end)
                        last.idsToDelete.add(movement.id)
                    } else {
                        // Salva il gruppo corrente e inizia uno nuovo
                        groups.add(last)
                        currentGroup = MovementGroup(
                            idsToDelete = mutableListOf(),
                            tag = movement.tag,
                            coordx = movement.coordx,
                            coordy = movement.coordy,
                            start = movement.start,
                            end = movement.end,
                            firstId = movement.id
                        )
                    }
                }
            // Aggiorna il database: modifica il primo movimento di ogni gruppo e elimina i duplicati
            groups.forEach { group ->
                if (group.idsToDelete.isNotEmpty()) {
                    // Aggiorna la riga del primo movimento con il nuovo end consolidato
                    update({ id eq group.firstId }) {
                        it[end] = group.end
                        it[coordx] = group.coordx
                        it[coordy] = group.coordy
                    }

                    // Elimina tutti gli altri movimenti del gruppo
                    group.idsToDelete.forEach { idToDelete ->
                        deleteWhere { id eq idToDelete }
                    }
                }
            }
        }
    }

    private data class MovementRecord(
        val id: Int,
        val tag: String,
        val start: Long,
        val end: Long,
        val coordx: Double,
        val coordy: Double
    )

    private data class MovementGroup(
        val idsToDelete: MutableList<Int>,
        val tag: String,
        val coordx: Double,
        val coordy: Double,
        val start: Long,
        var end: Long,
        val firstId: Int
    )
}


fun createTable(){
    transaction(db) {
        SchemaUtils.create(MovementTable)
    }
}