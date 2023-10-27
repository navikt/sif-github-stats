import './common/configInit'
import * as dayjs from 'dayjs'

import { octokit } from './octokit'
import { hentRepoer } from './common/hentRepoer'
import { sendSlackMessage } from './common/slackPosting'

const repoer = hentRepoer()
const antallDager = 30
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const blocks = [] as any[]

for (const repo of repoer) {
    console.log(`Sjekker repo '${repo}'`)
    const pulls = (
        await octokit.request('GET /repos/{owner}/{repo}/pulls', {
            owner: 'navikt',
            repo: repo,
        })
    ).data.filter((pull) => !pull.draft && pull.user?.login !== 'dependabot[bot]')
    const gamle = pulls.filter((pull) => dayjs().diff(dayjs(pull.created_at), 'day') > antallDager)
    if (gamle.length > 0) {
        blocks.push({
            type: 'section',
            text: {
                type: 'mrkdwn',
                text: `

:warning: * <https://www.github.com/navikt/${repo}|${repo}>* 
Har totalt <https://www.github.com/navikt/${repo}/pulls|${pulls.length} pull requests>. ${gamle.length} av dem er eldre enn ${antallDager} dager. Vi bør merge eller lukke disse`,
            },
        })

        gamle.map((pull) => {
            blocks.push({
                type: 'context',
                elements: [
                    {
                        type: 'image',
                        image_url: pull.user?.avatar_url,
                        alt_text: pull.user?.login
                    },
                    {
                        type: 'plain_text',
                        text: `${pull.user?.login} <${pull.html_url}|${pull.title}>`,
                    }
                ]
            })
        })
    }
}

//splitt i blokker på 50 og post til slack
const chunkSize = 50
const chunkedBlocks = blocks.reduce((resultArray, item, index) => {
    const chunkIndex = Math.floor(index / chunkSize)

    if (!resultArray[chunkIndex]) {
        resultArray[chunkIndex] = [] // start a new chunk
    }

    resultArray[chunkIndex].push(item)

    return resultArray
}, [])

let sent = false
// post chunked blocks to slack
for (const blocks of chunkedBlocks) {
    const res = await sendSlackMessage('SLACK_WEBHOOK', { blocks })
    console.log(`Sendte til slack: ${res}'`)
    sent = true
}

if (!sent) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const blocks = [] as any[]
    blocks.push({
        type: 'section',
        text: {
            type: 'mrkdwn',
            text: `:godstolen: *Ingen gamle pullrequests i noen av repoene våre* :tada: `,
        },
    })

    await sendSlackMessage('SLACK_WEBHOOK', { blocks })
}
