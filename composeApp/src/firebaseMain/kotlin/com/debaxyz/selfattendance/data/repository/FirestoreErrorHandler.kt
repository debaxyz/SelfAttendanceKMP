package com.debaxyz.selfattendance.data.repository

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val currentUser = try { Firebase.auth.currentUser } catch (_: Exception) { null }

    val providerInfo = JsonArray(
        currentUser?.providerData?.map { p ->
            buildJsonObject {
                put("providerId", p.providerId)
                put("email", p.email)
            }
        } ?: emptyList()
    )

    val authInfo: JsonObject = buildJsonObject {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("providerInfo", providerInfo)
    }

    val errorInfo = buildJsonObject {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfo)
    }

    val jsonString = errorInfo.toString()
    println("FirestoreError: Firestore Error: $jsonString")
    return jsonString
}
