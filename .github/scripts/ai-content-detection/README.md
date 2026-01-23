# AI Content Detection for Pull Requests

This workflow automatically analyzes pull request content to detect potentially AI-generated code and documentation. It helps maintainers focus their review efforts and ensures transparency in the contribution process.

## Overview

The AI content detection workflow:
1. Triggers on pull requests from external forks or can be run manually
2. Fetches the PR diff and description
3. Uses GitHub Copilot CLI to analyze the content
4. Posts results as a PR comment if AI-generated content is detected
5. Optionally adds a label to flag the PR
6. Can fail the CI check based on configuration

## Configuration

### Workflow Inputs (Manual Trigger)

| Input | Description | Default |
|-------|-------------|---------|
| `pr_number` | Pull Request number to analyze | Required |
| `confidence_threshold` | Confidence threshold percentage (0-100) | `80` |
| `pr_label` | Label to add if AI detected | `ai-generated` |
| `skip_users` | Comma-separated list of users to skip | `dependabot[bot],dependabot` |
| `fail_on_detection` | Fail workflow if AI content detected | `false` |
| `dry_run` | Test mode: analyze without actions | `true` |
| `custom_prompt` | Custom analysis prompt | (default prompt) |
| `diff_max_chars` | Maximum diff size to analyze | `20000` |

### Environment Variables

| Variable | Description |
|----------|-------------|
| `GH_TOKEN` | GitHub token for API access |
| `PR_NUMBER` | Pull request number |
| `GITHUB_REPOSITORY` | Repository in `owner/repo` format |

## Usage

### Automatic Trigger

The workflow automatically runs on:
- Pull requests from forked repositories
- Pull request events: `opened`, `synchronize`, `reopened`

### Manual Trigger

1. Go to **Actions** > **AI Content Detection**
2. Click **Run workflow**
3. Enter the PR number and configure options
4. Click **Run workflow**

### Local Testing

1. Clone the repository
2. Navigate to `.github/scripts/ai-content-detection`
3. Create a `.env` file with required variables:
   ```
   GH_TOKEN=your_github_token
   PR_NUMBER=123
   GITHUB_REPOSITORY=microcks/microcks
   CONFIDENCE_THRESHOLD=80
   DRY_RUN=true
   ```
4. Run `npm install && node analyze.js`

## How It Works

### Detection Criteria

The analysis looks for common AI-generated patterns:
- Overly verbose comments explaining obvious code
- Consistent formatting that differs from project style
- Generic variable naming patterns
- Explanatory comments that seem auto-generated
- Code that seems templated or boilerplate-heavy

### Confidence Score

The analysis returns a confidence score (0-100%):
- **0-50%**: Likely human-written
- **50-80%**: Potentially AI-assisted
- **80-100%**: Likely AI-generated

### Actions Taken

When the score exceeds the threshold:
1. **Comment**: A notice is posted on the PR with the analysis details
2. **Label**: An `ai-generated` label is added to the PR
3. **Fail** (optional): The workflow can fail to prevent auto-merge

## Important Notes

- AI assistance in coding is **not prohibited** in Microcks
- This tool helps ensure contributors understand their submitted code
- The detection is heuristic-based and may have false positives
- Maintainers should use their judgment for final decisions
- Contributors are encouraged to explain their code when flagged

## Contributing

To improve the AI detection:
1. Adjust the prompt in `analyze.js` for better accuracy
2. Tune the confidence threshold based on experience
3. Add patterns specific to Microcks codebase

## References

- [Original implementation (OpenTelemetry)](https://github.com/open-telemetry/opentelemetry.io/pull/8631)
- [GitHub Copilot CLI](https://docs.github.com/en/copilot/using-github-copilot/using-github-copilot-in-the-cli)
