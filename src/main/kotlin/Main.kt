package github_stats

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Gauge
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("Main")

fun main(
    applicationContext: ApplicationContext = ApplicationContext.Builder().build()
) {
    val githubTeam = "k9saksbehandling"
    val githubApiUrl = applicationContext.githubApiUrl

    val collectorRegistry = CollectorRegistry()
    val jobTimer = Gauge.build()
        .name("sif_github_stats_last_job_timer")
        .help("Time it took to run last job")
        .register(collectorRegistry)
        .startTimer()

    val httpClient = applicationContext.httpClient

    val teamRepositories: List<OrgRepositories> = runBlocking {
        httpClient.get(githubApiUrl + "orgs/navikt/teams/$githubTeam/repos") {
            parameter("per_page", "100")
        }.body()
    }
    logger.info("Found ${teamRepositories.size} repositories for team $githubTeam")

    val filteredRepos = listOf(
        "aad-iac",
        "dokgen",
        "familie-endringslogg",
        "folketrygdloven-beregningsgrunnlag-regelmodell",
        "vault-iac",
        "ft-kalkulus-verdikjede",
        "fp-nare",
        "fp-prosesstask",
        "ft-frontend-saksbehandling",
        "legacy-avhengigheter",
        "dev-jakarta-transform"
    )

    // Filter out archived repos & repos that doesn't belong to the team
    val filtrertResponse = teamRepositories.filter {
        (it.permissions.push || it.permissions.admin || it.permissions.maintain) &&
                !it.archived && !filteredRepos.contains(it.name)
    }.map { it.name }
    logger.info("Filtered out ${teamRepositories.size - filtrertResponse.size} repositories")

    val openPullRequests: Map<String, Int> = runBlocking {
        filtrertResponse.associateWith { repository ->
            // Fetch all open pull requests from repository and return size
            try {
                httpClient.get(githubApiUrl + "repos/navikt/$repository/pulls") {
                    parameter("per_page", "100")
                    parameter("state", "open")
                }.body<List<PullRequests>>().size
            } catch (e: Exception) {
                logger.error("Error fetching open pull requests for repository: $repository", e.message)
                null
            }
        }.filterNot { it.value == null }.mapValues { it.value!! }
    }
    logger.info("Received ${openPullRequests.size} repositories with open pull requests")

    val gauge = Gauge.build()
        .name("sif_github_stats_open_prs")
        .help("Open Github PRs")
        .register(collectorRegistry)

    try {
        openPullRequests.forEach { (repository, count) ->
            logger.info("Setting metrics for Repository: $repository with value Open Pull Requests: $count")
            gauge
                .labels(repository)
                .set(count.toDouble())
        }
    } finally {
        jobTimer.setDuration()
        logger.info("Pushing metrics")
        applicationContext.pushGateway
            .push(collectorRegistry, "sif-github-stats")
    }

    logger.info("Finished job successfully, exiting")
}

@Serializable
data class PullRequests(
    val updated_at: String
)

@Serializable
data class OrgRepositories(
    val name: String,
    val archived: Boolean,
    val visibility: String,
    val updated_at: String, // "2011-01-26T19:14:43Z"
    val permissions: Permissions
)

@Serializable
data class Permissions(
    val admin: Boolean,
    val maintain: Boolean,
    val push: Boolean,
    val triage: Boolean,
    val pull: Boolean
)