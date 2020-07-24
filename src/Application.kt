package com.example

import io.ktor.application.*
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.json
import io.ktor.server.netty.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.list
import kotlinx.serialization.json.*
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object Users : IntIdTable() {
    val email = varchar("email", 100).index()
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)
    var email by Users.email

    fun toJSONResponse(): UserResponse {
        return UserResponse(id.value, email)
    }
}

fun setupDB() {
    Database.connect("jdbc:mysql://127.0.0.1/users_api_db?serverTimezone=UTC", driver = "com.mysql.cj.jdbc.Driver", user = "root", password = "")
    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(Users)
    }
}

val json = Json(JsonConfiguration.Stable)

fun main(args: Array<String>): Unit {
    setupDB()
    EngineMain.main(args)
}

@Serializable
data class UserResponse(val id: Int, val email: String)

@Serializable
data class UserRequest(val email: String)


fun Application.module() {
    install(ContentNegotiation) { json() }
    routing {
        get("/users") {
            val users = transaction { User.all().toList() }
            val jsonList = json.stringify(UserResponse.serializer().list, users.map { it.toJSONResponse() })
            call.respondText(jsonList, ContentType.Application.Json)
        }
        post("/users") {
            val userRequest = call.receive<UserRequest>()
            val user = transaction { User.new { email = userRequest.email } }
            val jsonUser = json.stringify(UserResponse.serializer(), user.toJSONResponse())
            call.respondText(jsonUser, ContentType.Application.Json, HttpStatusCode.Created)
        }
    }
}