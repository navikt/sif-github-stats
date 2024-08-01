package no.nav.github_stats

import io.ktor.client.*
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

    val teamRepositories = findTeamRepositories(githubTeams, httpClient, githubApiUrl)

    val repositoryInfos: List<RepositoryInfo> = findRepositoryInfo(httpClient, githubApiUrl, teamRepositories)

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

    val totalCriticalGauge = Gauge.build()
        .name("sif_github_stats_dependabot_critical")
        .labelNames("repository")
        .help("Dependabot Critical Alerts")
        .register(collectorRegistry)

    val totalHighGauge = Gauge.build()
        .name("sif_github_stats_dependabot_high")
        .labelNames("repository")
        .help("Dependabot High Alerts")
        .register(collectorRegistry)


    try {
        repositoryInfos.forEach {
            logger.info("Setting metrics for Repository: $it")
            totalPrGauge
                .labels(it.repository)
                .set(it.openPRs.toDouble())

            totalDependabotGauge
                .labels(it.repository)
                .set(it.openDependenciesSum.toDouble())

            totalCriticalGauge
                .labels(it.repository)
                .set(it.criticalAlertsSum.toDouble())

            totalHighGauge
                .labels(it.repository)
                .set(it.highAlertsSum.toDouble())

        }
    } finally {
        jobTimer.setDuration()
        logger.info("Pushing metrics")
        applicationContext.pushGateway
            .push(collectorRegistry, "sif-github-stats")
    }

    logger.info("Finished job successfully, exiting")
}

private fun findTeamRepositories(
    githubTeams: List<String>,
    httpClient: HttpClient,
    githubApiUrl: String
): Set<String> {
    val repositories = runBlocking {
        githubTeams.flatMap { httpClient.get(githubApiUrl + "orgs/navikt/teams/$it/repos") {
                parameter("per_page", "100")
            }.body<List<OrgRepository>>()
        }
    }
    logger.info("Found ${repositories.size} repositories for team(s) ${githubTeams.joinToString { it }} }}")

    // Filter out archived repos & repos that doesn't belong to the team and duplicates
    val teamRepositories = repositories.filter {
        (it.permissions.push || it.permissions.admin || it.permissions.maintain) && !it.archived
    }.map { it.name }.toSet()
    logger.info("Filtered out ${repositories.size - teamRepositories.size} repositories")

    return teamRepositories
}

private fun findRepositoryInfo(
    httpClient: HttpClient,
    githubApiUrl: String,
    teamRepositories: Set<String>
): List<RepositoryInfo> {
    val repositoryInfo: List<RepositoryInfo> = runBlocking {
        teamRepositories.mapNotNull { repository ->
            // Fetch all open pull requests from repository and find total size and dependabots PRs
            try {
                val pullRequests = httpClient.get(githubApiUrl + "repos/navikt/$repository/pulls") {
                    parameter("per_page", "100")
                    parameter("state", "open")
                }.body<List<PullRequest>>()
                RepositoryInfo(
                    repository,
                    pullRequests.size,
                    pullRequests.filter { it.user.login == "dependabot[bot]" })
            } catch (e: Exception) {
                logger.error("Error fetching open pull requests for repository: $repository, Msg: [${e.message}]")
                null
            }
        }
    }

    logger.info("Received ${repositoryInfo.size} repositories with open pull requests")

    runBlocking {
            repositoryInfo.forEach {
                try {
                    it.dependabotAlerts = httpClient.get(githubApiUrl + "repos/navikt/${it.repository}/dependabot/alerts") {
                        parameter("per_page", "100")
                        parameter("state", "open")
                    }.body<List<DependabotAlert>>()
                } catch (e: Exception) {
                    logger.error("Error fetching open dependabot alerts for repository: ${it.repository}, Msg: [${e.message}]")
                }
            }
    }

    return repositoryInfo
}

data class RepositoryInfo(
    val repository: String,
    val openPRs: Int,
    val dependabotPrs: List<PullRequest>,
    var dependabotAlerts: List<DependabotAlert> = emptyList()
) {
    companion object {
        private val dependabotGroupUpdatesRegEx =  "(\\d+)\\s+updates?$".toRegex()
        private val dependabotGroupRegEx =  ".+group.+directory.+updates?$".toRegex()
    }

    val openDependenciesSum by lazy {
        val prTitles = dependabotPrs.map { it.title }.toSet()
        val groupTitles = prTitles.filter { dependabotGroupRegEx.matches(it) }.toSet()
        val groupDependencySums =
            groupTitles.sumOf { dependabotGroupUpdatesRegEx.find(it)?.groups?.lastOrNull()?.value?.toInt() ?: 0 }
        prTitles.minus(groupTitles).size + groupDependencySums
    }
    val criticalAlertsSum by lazy { dependabotAlerts.filter { it.security_vulnerability.severity == "critical" }.size }
    val highAlertsSum by lazy { dependabotAlerts.filter { it.security_vulnerability.severity == "high" }.size }

    override fun toString(): String {
        return "RepositoryInfo(repository='$repository', openPRs=$openPRs, openDependenciesSum=$openDependenciesSum, criticalAlertsSum=$criticalAlertsSum, highAlertsSum=$highAlertsSum)"
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
data class DependabotAlert(
   val security_vulnerability: SecurityVulnerability,
)

@Serializable
data class SecurityVulnerability(
    val severity: String
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
