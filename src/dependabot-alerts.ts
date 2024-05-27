import { octokit } from './octokit'
import { hentRepoer } from './common/hentRepoer'
import { sendSlackMessage } from './common/slackPosting'

const repoer = hentRepoer()
let fantAlert = false

for (const repo of repoer) {
    console.log('Henter for repo ' + repo)
    const dependabotAlerts = await octokit.request('GET /repos/{owner}/{repo}/dependabot/alerts?severity=high', {
        owner: 'navikt',
        repo: repo,
        state: 'open',
    })

    if (dependabotAlerts.data.length > 0) {
        fantAlert = true
        // eslint-disable-next-line
        const blocks = [] as any[]
        blocks.push({
            type: 'section',
            text: {
                type: 'mrkdwn',
                text: `:error: *<https://www.github.com/navikt/${repo}|${repo}>* Har totalt <https://www.github.com/navikt/${repo}/security/dependabot|${dependabotAlerts.data.length} kritiske dependabot alerts>.`,
            },
        })
        dependabotAlerts.data.map((alert) => {
            blocks.push({
                type: 'section',
                text: {
                    type: 'mrkdwn',
                    text: `*<${alert.html_url}|${alert.security_advisory.summary}>*`,
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
