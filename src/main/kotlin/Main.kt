package no.nav.github_stats

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Gauge
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("Main")

fun main() {
    val applicationContext: ApplicationContext = ApplicationContext.Builder().build()
    val githubTeams = listOf("k9saksbehandling", "omsorgspenger", "dusseldorf")
    val githubApiUrl = applicationContext.githubApiUrl

    val collectorRegistry = CollectorRegistry()
    val jobTimer = Gauge.build()
        .name("sif_github_stats_last_job_timer")
        .help("Time it took to run last job")
        .register(collectorRegistry)
        .startTimer()

    val httpClient = applicationContext.httpClient

    val repositories: MutableSet<OrgRepository> = mutableSetOf()

    runBlocking {
        githubTeams.forEach { team ->
            val repos: List<OrgRepository> = httpClient.get(githubApiUrl + "orgs/navikt/teams/$team/repos") {
                parameter("per_page", "100")
            }.body()
            repositories.addAll(repos)
        }
    }
    logger.info("Found ${repositories.size} repositories for team(s) ${githubTeams.joinToString { it }} }}")

    // Filter out archived repos & repos that doesn't belong to the team
    val teamRepositories = repositories.filter {
        (it.permissions.push || it.permissions.admin || it.permissions.maintain) && !it.archived
    }.map { it.name }
    logger.info("Filtered out ${repositories.size - teamRepositories.size} repositories")
    val repositoryInfos:List<RepositoryInfo> = runBlocking {
        teamRepositories.mapNotNull { repository ->
            // Fetch all open pull requests from repository and return size
            try {
                val pullRequests = httpClient.get(githubApiUrl + "repos/navikt/$repository/pulls") {
                    parameter("per_page", "100")
                    parameter("state", "open")
                }.body<List<PullRequest>>()
                RepositoryInfo(repository, pullRequests.size, pullRequests.filter { it.user.login == "dependabot[bot]" })
            } catch (e: Exception) {
                logger.error("Error fetching open pull requests for repository: $repository, Msg: [${e.message}]")
                null
            }
        }
    }
    logger.info("Received ${repositoryInfos.size} repositories with open pull requests")

    val totalPrGauge = Gauge.build()
        .name("sif_github_stats_open_prs")
        .labelNames("repository")
        .help("Open Github PRs")
        .register(collectorRegistry)

    val totalDependabotGauge = Gauge.build()
        .name("sif_github_stats_dependency_updates")
        .labelNames("repository")
        .help("Dependencies awaiting merge")
        .register(collectorRegistry)


    try {
        repositoryInfos.forEach {
            logger.info("Setting metrics for Repository: ${it.repository} with value Open Pull Requests: ${it.openPRs}")
            totalPrGauge
                .labels(it.repository)
                .set(it.openPRs.toDouble())
            totalDependabotGauge
                .labels(it.repository)
                .set(it.openDependenciesSum().toDouble())
        }
    } finally {
        jobTimer.setDuration()
        logger.info("Pushing metrics")
        applicationContext.pushGateway
            .push(collectorRegistry, "sif-github-stats")
    }

    logger.info("Finished job successfully, exiting")
}

data class RepositoryInfo(
    val repository: String,
    val openPRs: Int,
    val dependabotPrs: List<PullRequest>
) {
    companion object {
        private val dependabotGroupUpdatesRegEx =  "(\\d+)\\s+updates?$".toRegex()
        private val dependabotGroupRegEx =  ".+group.+directory.+updates?$".toRegex()
    }

    fun openDependenciesSum(): Int {
        val prTitles = dependabotPrs.map { it.title }.toSet()
        val groupTitles = prTitles.filter { dependabotGroupRegEx.matches(it) }.toSet()
        val groupDependencySums =
            groupTitles.sumOf { dependabotGroupUpdatesRegEx.find(it)?.groups?.lastOrNull()?.value?.toInt() ?: 0 }

        return prTitles.minus(groupTitles).size + groupDependencySums
    }
}

@Serializable
data class PullRequest(
    val updated_at: String,
    val title: String,
    val user: User
)

@Serializable
data class User(
    val login: String,
    val type: String

)

@Serializable
data class OrgRepository(
    val name: String,
    val archived: Boolean,
    val visibility: String,
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
