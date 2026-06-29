import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val baseUrl= "https://life360/api/v3/"

//every function in this file throws an exception if missing a circle member (404) or unauthorized (403) or returns the movement if it's all good


suspend fun getAccessToken(client: HttpClient): String? {
    info("Executing post request against ${baseUrl}oauth2/token.json")
    val result = client.post(baseUrl + "oauth2/token.json") {
        contentType(ContentType.Application.Json)
        val content = buildJsonObject {
            put("grant_type","password")           //"grant_type": "password",
            put("username",Config.LIFE_USERNAME)   //"username": "example@email.com",
            put("password",Config.LIFE_PASSWORD)   //"password": "string"
        }
        setBody(content.toString())
    }
    if(result.status.isSuccess()){ //either 200 or 403
        val body = parseToJsonElement(result.bodyAsText()) as JsonObject
        return ( body["access_token"] as JsonPrimitive).content
    } else {
        error("Authentication failed with status code ${result.status.value}")
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
suspend fun getForceUpdatePosition(client: HttpClient,circleId:String,authToken:String,name:String, id:String):Movement {
    val requestId:String
    client.post(baseUrl + "circles/$circleId/members/$id/request"){
        bearerAuth(authToken)
        contentType(ContentType.Application.Json)
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

    }
    client.get(baseUrl+"circles/members/request/$requestId"){
        bearerAuth(authToken)
    }.run{
        if(!status.isSuccess()){ //can return either 404 or 200
            throw ExpiredAccessToken()
        }
        val body = parseToJsonElement(bodyAsText()) as JsonObject
        val location= body["location"] as JsonObject
        val y = (location["latitude"] as JsonPrimitive).content.toDouble()
        val x = (location["longitude"] as JsonPrimitive).content.toDouble()
        val startTime= (location["startTimestamp"] as JsonPrimitive).content.toLong()
        val endTime= (location["endTimestamp"] as JsonPrimitive).content.let{ if(it.isEmpty()) null else it.toLong()}
        return Movement(
            tag=name,
            start= startTime,
            end=endTime,
            coordinates = Coordinates(x,y)
        )
    }
}


@Throws(ExpiredAccessToken::class,RuntimeException::class) // failed configuration
suspend fun getCircleId(client: HttpClient,authToken:String): String{
    val circles = client.get(baseUrl + "circles"){
    bearerAuth(authToken)
}.run {
        if (!status.isSuccess()) {
            throw ExpiredAccessToken()
        } else {
            val body = parseToJsonElement(bodyAsText()) as JsonArray
            body.map { je ->
                (je as JsonObject).run {
                    Pair(
                        (get("id") as JsonPrimitive).content,
                        (get("name") as JsonPrimitive).content
                    )
                }
            }
        }
    }
    /*[
{
"id": "b42ece6c-47b8-4061-a672-d55fd8211ce3",
"name": "Smith Family",
"color": "string",
"type": "basic",
"createdAt": "1493252439",
"memberCount": "2",
"unreadMessages": "3",
"unreadNotifications": "0",
"features": { }
}
]*/

    circles.find{ it.second.trim() == Config.CIRCLE_NAME }?.first.let{
        if(it==null) {
            error("Invalid circle name defined in .env: ${Config.CIRCLE_NAME}")
            throw RuntimeException()
        } else return it
    }
}

@Throws(ExpiredAccessToken::class)
suspend fun getCircleMembers(client: HttpClient,authToken:String,circleId:String): List<Member>{

    val members = client.get(baseUrl + "circles/$circleId"){
        bearerAuth(authToken)
    }.run {
        if(!status.isSuccess()){
            throw ExpiredAccessToken()
        } else {
            val body = (parseToJsonElement(bodyAsText()) as JsonObject)["members"] as JsonArray
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
