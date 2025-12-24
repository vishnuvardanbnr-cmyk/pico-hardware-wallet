// GitHub Push Script - Uses Replit's GitHub integration
import { Octokit } from '@octokit/rest';
import { execSync } from 'child_process';

let connectionSettings: any;

async function getAccessToken() {
  if (connectionSettings && connectionSettings.settings.expires_at && new Date(connectionSettings.settings.expires_at).getTime() > Date.now()) {
    return connectionSettings.settings.access_token;
  }
  
  const hostname = process.env.REPLIT_CONNECTORS_HOSTNAME;
  const xReplitToken = process.env.REPL_IDENTITY 
    ? 'repl ' + process.env.REPL_IDENTITY 
    : process.env.WEB_REPL_RENEWAL 
    ? 'depl ' + process.env.WEB_REPL_RENEWAL 
    : null;

  if (!xReplitToken) {
    throw new Error('X_REPLIT_TOKEN not found for repl/depl');
  }

  connectionSettings = await fetch(
    'https://' + hostname + '/api/v2/connection?include_secrets=true&connector_names=github',
    {
      headers: {
        'Accept': 'application/json',
        'X_REPLIT_TOKEN': xReplitToken
      }
    }
  ).then(res => res.json()).then(data => data.items?.[0]);

  const accessToken = connectionSettings?.settings?.access_token || connectionSettings.settings?.oauth?.credentials?.access_token;

  if (!connectionSettings || !accessToken) {
    throw new Error('GitHub not connected');
  }
  return accessToken;
}

async function getUncachableGitHubClient() {
  const accessToken = await getAccessToken();
  return new Octokit({ auth: accessToken });
}

async function main() {
  const repoName = process.argv[2] || 'pico-hardware-wallet';
  
  console.log('Getting GitHub client...');
  const octokit = await getUncachableGitHubClient();
  
  console.log('Fetching authenticated user...');
  const { data: user } = await octokit.rest.users.getAuthenticated();
  console.log(`Authenticated as: ${user.login}`);
  
  let repoExists = false;
  try {
    await octokit.rest.repos.get({ owner: user.login, repo: repoName });
    repoExists = true;
    console.log(`Repository ${user.login}/${repoName} already exists`);
  } catch (error: any) {
    if (error.status === 404) {
      console.log(`Repository not found, creating ${user.login}/${repoName}...`);
      await octokit.rest.repos.createForAuthenticatedUser({
        name: repoName,
        description: 'Multi-chain cryptocurrency hardware wallet application',
        private: false,
      });
      console.log('Repository created successfully');
    } else {
      throw error;
    }
  }
  
  const repoUrl = `https://${await getAccessToken()}@github.com/${user.login}/${repoName}.git`;
  
  try {
    execSync('git remote remove origin', { stdio: 'pipe' });
  } catch (e) {
    // Origin doesn't exist, that's fine
  }
  
  console.log('Adding GitHub remote...');
  execSync(`git remote add origin ${repoUrl}`, { stdio: 'inherit' });
  
  console.log('Pushing to GitHub...');
  execSync('git push -u origin main --force', { stdio: 'inherit' });
  
  console.log(`\nSuccess! Repository available at: https://github.com/${user.login}/${repoName}`);
}

main().catch(err => {
  console.error('Error:', err.message);
  process.exit(1);
});
