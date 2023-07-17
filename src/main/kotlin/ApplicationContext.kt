package no.nav.github_stats

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.prometheus.client.exporter.PushGateway
import kotlinx.serialization.json.Json

class ApplicationContext(
    val githubApiUrl: String,
    val httpClient: HttpClient,
    val pushGateway: PushGateway
) {
    internal class Builder(
        var env: Map<String, String>? = null,
        var githubApiUrl: String? = null,
        var httpClient: HttpClient? = null,
        var pushGateway: PushGateway? = null
    ) {
        fun build(): ApplicationContext {
            val benyttetEnv = env ?: System.getenv()
            val githubApiToken = requireNotNull(benyttetEnv["SIF-STATS-GITHUB-PAT"])
            val githubApiVersion = benyttetEnv["GITHUB_API_VERSION"] ?: "2022-11-28"

            val benyttetGithubApiUrl = githubApiUrl ?: requireNotNull(benyttetEnv["GITHUB_API_URL"])
            val benyttetHttpClient = httpClient ?: HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }
                defaultRequest {
                    header(HttpHeaders.Authorization, "Bearer $githubApiToken")
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                    header("X-GitHub-Api-Version", githubApiVersion)
                    contentType(ContentType.Application.Json)
                }
            }
            val benyttetPushGateway = pushGateway ?: PushGateway(requireNotNull(benyttetEnv["PUSH_GATEWAY_ADDRESS"]))

            return ApplicationContext(
                githubApiUrl = benyttetGithubApiUrl,
                httpClient = benyttetHttpClient,
                pushGateway = benyttetPushGateway
            )
        }
    }
}