import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.userAgent
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds

const val baseUrl= "https://api-cloudfront.life360.com/v3/"
const val newUrl = "https://api-cloudfront.life360.com/v4/"

//every function in this file throws an exception if missing a circle member (404) or unauthorized (403) or returns the movement if it's all good

fun HttpRequestBuilder.defaultHeaders(token:String){
    userAgent("com.life360.android.safetymapd/KOKO/23.50.0 android/13")
    accept(ContentType.Application.Json)
    contentType(ContentType.Application.Json)
    headers["Cache-Control"] = "no-cache"
    bearerAuth(token)

}

suspend fun getAccessToken(client: HttpClient): String? {
    info("Executing post request against ${baseUrl}oauth2/token.json")
    val result = client.post(baseUrl + "oauth2/token.json") {
        contentType(ContentType.Application.Json)
        val content = buildJsonObject {
            put("grant_type","password")           //"grant_type": "password",
            put("username",Config.LIFE_USERNAME)   //"username": "example@email.com",
            put("password",Config.LIFE_PASSWORD)   //"password": "string"
        }
        headers["Authorization"] = "Basic Y2F0aGFwYWNyQVBoZUtVc3RlOGV2ZXZldnVjSGFmZVRydVl1ZnJhYzpkOEM5ZVlVdkE2dUZ1YnJ1SmVnZXRyZVZ1dFJlQ1JVWQ==" //directly from https://github.com/pnbruckner/life360/blob/master/life360/api.py
        setBody(content.toString())
    }
    if(result.status.isSuccess()){ //either 200 or 403
        val bas = result.bodyAsText()
        debug{ bas }
        val body = parseToJsonElement(bas) as JsonObject
        return ( body["access_token"] as JsonPrimitive).content
    } else {
        error("Authentication failed with status code ${result.status.value}")
        debug{result.bodyAsText()}
        return null
    }
    /*
{
"access_token": "MDBkZDNmdBa1DBiZC00YmI0LWJiYjctMjZkNWI1YjczODkx",
"token_type": "Bearer",
"onboarding": 0,
"user": {
"id": "b42ece6c-47b8-4061-a672-d55fd8211ce3",
"firstName": "Joe",
"lastName": "Smith",
"loginEmail": "example@email.com",
"loginPhone": "+15555555555",
"avatar": "string",
"communications": [],
"locale": "en_US",
"language": "en",
"created": "2017-04-26 17:20:39",
"settings": {},
"cobranding": []
},
"cobranding": [
{ }
],
"promotions": [
{ }
],
"state": "string"
}
*/
}

@Throws(ReloadCircleMembers::class, ExpiredAccessToken::class)
suspend fun getForceUpdatePosition(client: HttpClient,circleId:String,authToken:String,name:String, id:String):Movement? {
    val requestId:String
    info("Executing post request against ${baseUrl}circles/$circleId/members/$id/request")
    client.post(baseUrl + "circles/$circleId/members/$id/request"){
        defaultHeaders(authToken)
        val body = buildJsonObject {
            put("type","location")
        }
        setBody(body.toString())
    }.run{
        if(!status.isSuccess()){
            error("Authentication failed with status ${status.value}")
            if(status.value == 404){
                error("Member with id $id not found")
                throw ReloadCircleMembers()
            } else {
                error("Auth token expired")
                throw ExpiredAccessToken()
            }
        }
        val body = parseToJsonElement(bodyAsText()) as JsonObject
        requestId = (body["requestId"] as JsonPrimitive).content

    } //checked at https://github.com/pnbruckner/life360/blob/master/life360/api.py
    info("Executing get request against ${baseUrl}circles/members/request/$requestId")

    val response  = client.get(baseUrl+"circles/members/request/$requestId"){
        defaultHeaders(authToken)
    }
    if(!response.status.isSuccess()){ //can return either 404 or 200
        debug{response.bodyAsText()}
        throw ExpiredAccessToken()
    }
    val bas = response.bodyAsText()
    debug{bas}
    var body = parseToJsonElement(bas) as JsonObject
    var countRequest=0
    while( (body["location"] == JsonNull ||
        (body["status"] as JsonPrimitive).content == "P")
        && countRequest < 15) {
        countRequest++
        delay(1.seconds)
        val response  = client.get(baseUrl+"circles/members/request/$requestId"){
            defaultHeaders(authToken)
        }
        if(!response.status.isSuccess()){ //can return either 404 or 200
                debug{response.bodyAsText()}
                throw ExpiredAccessToken()
            }
        val bas = response.bodyAsText()
        debug{bas}
        body = parseToJsonElement(bas) as JsonObject
    }
    if(countRequest>=15){
        return null
    }
    return jsonObjectToMovement(name, body)

// checked at https://github.com/pnbruckner/life360/blob/master/life360/api.py
}
fun jsonObjectToMovement(name:String, body:JsonObject):Movement{
    val location = body["location"] as JsonObject
    val y = (location["latitude"] as JsonPrimitive).content.toDouble()
    val x = (location["longitude"] as JsonPrimitive).content.toDouble()
    val startTime= (location["startTimestamp"] as JsonPrimitive).content.toLong()
    val endTime= (location["endTimestamp"] as JsonPrimitive).content.let{ if(it.isEmpty()) startTime else it.toLong()}
    return Movement(
        tag=name,
        start= startTime,
        end=endTime,
        coordinates = Coordinates(x,y)
    )
}
@Throws(ExpiredAccessToken::class,RuntimeException::class) // failed configuration
suspend fun getCircleId(client: HttpClient,authToken:String): String{
    info("Executing get request against ${newUrl}circles/")
    val circles = client.get(newUrl + "circles"){
    defaultHeaders(authToken)
}.run {
        if (!status.isSuccess()) {
            debug{bodyAsText()}
            throw ExpiredAccessToken()
        } else {
            val bas= bodyAsText()
            debug{bas}
            val body = parseToJsonElement(bas) as JsonObject

            (body["circles"] as JsonArray).map { je ->
                (je as JsonObject).run {
                    Pair(
                        (get("id") as JsonPrimitive).content,
                        (get("name") as JsonPrimitive).content
                    )
                }
            }
        }
    }
    /*[{"circles":[{"id":"b7c8ab97-f90e-4d25-9f26-77c3ecb23363","name":"Famiglia Fede Transsessuale","createdAt":"1705781484"},{"id":"a6ffe625-13a5-44f7-a14c-fa6e55f655c8","name":"marina romea bombers","createdAt":"1723302278"}]}Exception in thread "main" java.lang.ClassCastException: class kotlinx.serialization.json.JsonObject cannot be cast to class kotlinx.serialization.json.JsonArray (kotlinx.serialization.json.JsonObject and kotlinx.serialization.json.JsonArray are in unnamed module of loader 'app')
*/

    circles.find{ it.second.trim() == Config.CIRCLE_NAME }?.first.let{
        if(it==null) {
            error("Invalid circle name defined in .env: ${Config.CIRCLE_NAME}")
            throw RuntimeException()
        } else return it
    }
}

@Throws(ExpiredAccessToken::class)
suspend fun getCircleMembers(client: HttpClient,authToken:String,circleId:String): List<Member>{
info("Executing get request against ${baseUrl}circles/$circleId/members")
    val members = client.get(baseUrl + "circles/$circleId/members"){
        defaultHeaders(authToken)
    }.run {
        if(!status.isSuccess()){
            debug{bodyAsText()}
            throw ExpiredAccessToken()
        } else {
            val bas = bodyAsText()
            debug{bas}
            val body = (parseToJsonElement(bas) as JsonObject)["members"] as JsonArray
            body.map { a->
                a as JsonObject
                Member(
                    (a["firstName"] as JsonPrimitive).content +" "+ (a["lastName"] as JsonPrimitive).content,
                    (a["id"] as JsonPrimitive).content
                )
            }
        }
    }
    return members
}
