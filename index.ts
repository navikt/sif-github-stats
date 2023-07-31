import * as core from '@actions/core';
import axios from 'axios';
import { Gauge, register, Pushgateway } from 'prom-client';

async function run() {
  try {
    const githubToken = process.env['GITHUB_TOKEN'] || '';
    const githubApiUrl = process.env['GITHUB_API_URL'] || '';
    const pushGatewayAddress = process.env['PUSH_GATEWAY_ADDRESS'] || '';

    const githubTeams = ['k9saksbehandling', 'omsorgspenger', 'dusseldorf', 'pleiepenger'];
    const repositories: Set<any> = new Set();

    const jobTimer = new Gauge({
      name: 'sif_github_stats_last_job_timer',
      help: 'Time it took to run last job'
    });
    jobTimer.startTimer();

    // Fetch repositories for teams
    for (const team of githubTeams) {
      const repos = await axios.get(`${githubApiUrl}/orgs/navikt/teams/${team}/repos`, {
        headers: { Authorization: `Bearer ${githubToken}` },
        params: { per_page: '100' }
      });
      repos.data.forEach((repo: any) => repositories.add(repo));
    }

    core.info(`Found ${repositories.size} repositories for team(s) ${githubTeams.join(', ')}`);

    // Filter out archived repos & repos that doesn't belong to the team
    const teamRepositories = Array.from(repositories).filter(
      (it: any) => (it.permissions.push || it.permissions.admin || it.permissions.maintain) && !it.archived
    ).map((it: any) => it.name);

    core.info(`Filtered out ${repositories.size - teamRepositories.length} repositories`);

    const openPullRequests: { [key: string]: number } = {};

    // Fetch open pull requests from repositories
    for (const repository of teamRepositories) {
      try {
        const pulls = await axios.get(`${githubApiUrl}/repos/navikt/${repository}/pulls`, {
          headers: { Authorization: `Bearer ${githubToken}` },
          params: { per_page: '100', state: 'open' }
        });
        openPullRequests[repository] = pulls.data.length;
      } catch (e) {
        core.error(`Error fetching open pull requests for repository: ${repository}, Msg: [${e.message}]`);
      }
    }

    const openPRsGauge = new Gauge({
      name: 'sif_github_stats_open_prs',
      help: 'Open Github PRs',
      labelNames: ['repository']
    });

    // Iterate and set metrics for repositories
    for (const [repository, count] of Object.entries(openPullRequests)) {
      core.info(`Setting metrics for Repository: ${repository} with value Open Pull Requests: ${count}`);
      openPRsGauge.labels(repository).set(count);
    }

    jobTimer.setDuration();

    // Push the metrics to the PushGateway
    const pushGateway = new Pushgateway(pushGatewayAddress);
    pushGateway.pushAdd({ jobName: 'sif-github-stats' }, (err) => {
      if (err) {
        core.error(`Error pushing metrics: ${err.message}`);
      } else {
        core.info('Pushing metrics successful');
      }
    });

    core.info('Finished job successfully, exiting');
  } catch (error) {
    core.setFailed(error.message);
  }
}

run();
