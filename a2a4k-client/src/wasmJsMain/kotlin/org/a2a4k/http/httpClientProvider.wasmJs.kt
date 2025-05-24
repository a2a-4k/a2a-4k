package org.a2a4k.http

import io.ktor.client.*
import io.ktor.client.engine.js.*
import kotlinx.serialization.json.Json

actual fun createHttpClient(
    json: Json,
    config: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(Js) {
    // User-defined additional config
    config()
}
