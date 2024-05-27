import { octokit } from './octokit'
import { Endpoints } from '@octokit/types';
import { hentRepoer } from './common/hentRepoer'
import { sendSlackMessage } from './common/slackPosting'

const repoer = hentRepoer()
let fantAlert = false

for (const repo of repoer) {
    console.log('Henter for repo ' + repo)
    type DependabotAlertsResponseData = Endpoints['GET /repos/{owner}/{repo}/dependabot/alerts']['response']['data'];
    const response = await octokit.request('GET /repos/{owner}/{repo}/dependabot/alerts', {
        owner: 'navikt',
        repo: repo,
        state: 'open',
    });

    const dependabotAlerts: DependabotAlertsResponseData = response.data;


    if (dependabotAlerts.length > 0) {
        fantAlert = true
        const highAndcriticals = dependabotAlerts
            .filter(alert =>  alert.security_vulnerability.severity == 'critical' || alert.security_vulnerability.severity == 'high')
        const lows = dependabotAlerts
            .filter(alert => alert.security_vulnerability.severity != 'critical' && alert.security_vulnerability.severity != 'high')

        const hasSevere = highAndcriticals.length > 0
        const hasLow = lows.length > 0
        // eslint-disable-next-line
        const blocks = [] as any[]
        blocks.push({
            type: 'section',
            text: {
                type: 'mrkdwn',
                text: `${hasSevere ? ':error:' : ':warning:' } *<https://www.github.com/navikt/${repo}|${repo}>* Har totalt ${hasSevere ? highAndcriticals.length + ' high/critical ' : ''}${hasLow ? lows.length + 'andre ' : ''} <https://www.github.com/navikt/${repo}/security/dependabot|dependabot alerts>${hasSevere ? ':' : '.'}`,
            },
        })

        highAndcriticals.map((alert) => {
            blocks.push({
                type: 'section',
                text: {
                    type: 'mrkdwn',
                    text: `*<${alert.html_url}|${alert.security_advisory.severity.toUpperCase()}: ${alert.security_advisory.summary} }>*`,
                },
            })
        })

        await sendSlackMessage('SLACK_WEBHOOK', { blocks })
        console.log(`Sendte til slack for repo '${repo}'`)
    }
}

if (!fantAlert) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const blocks = [] as any[]
    blocks.push({
        type: 'section',
        text: {
            type: 'mrkdwn',
            text: `:godstolen: *Ingen dependabot alerts i noen av repoene våre* :tada: `,
        },
    })

    await sendSlackMessage('SLACK_WEBHOOK', { blocks })
}
