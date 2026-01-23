#!/usr/bin/env node

/**
 * AI Content Detection Script for Pull Requests
 * 
 * This script analyzes PR diffs to detect potentially AI-generated content.
 * It uses GitHub Copilot CLI to perform the analysis and can:
 * - Post a warning comment on the PR
 * - Add a label to flag the PR
 * - Optionally fail the CI check
 * 
 * @see https://github.com/microcks/microcks/issues/XXX
 */

import { config } from 'dotenv';
import { Octokit } from '@octokit/rest';
import { execSync } from 'child_process';

// Load .env file if present (for local testing)
config();

/**
 * Configuration class for AI content detection
 */
class AIDetectionConfig {
    constructor() {
        // Required configuration
        this.ghToken = process.env.GH_TOKEN;
        this.prNumber = parseInt(process.env.PR_NUMBER, 10);
        this.repo = process.env.GITHUB_REPOSITORY;

        // Optional configuration with defaults
        this.threshold = parseInt(process.env.CONFIDENCE_THRESHOLD || '80', 10);
        this.labelName = process.env.PR_LABEL || 'ai-generated';
        this.skipUsers = (process.env.SKIP_USERS || '')
            .split(',')
            .map((u) => u.trim())
            .filter(Boolean);
        this.failOnDetection =
            process.env.FAIL_ON_DETECTION === 'true' ||
            process.env.FAIL_ON_DETECTION === '1';
        this.dryRun = process.env.DRY_RUN === 'true' || process.env.DRY_RUN === '1';
        this.customPrompt = process.env.CUSTOM_PROMPT || null;
        this.diffMaxChars = parseInt(process.env.DIFF_MAX_CHARS || '20000', 10);
    }

    /**
     * Validates required configuration
     * @throws {Error} If required configuration is missing or invalid
     */
    validate() {
        if (!this.ghToken) {
            throw new Error('GH_TOKEN is required');
        }

        if (!this.prNumber || isNaN(this.prNumber)) {
            throw new Error('PR_NUMBER is required and must be a number');
        }

        if (!this.repo) {
            throw new Error('GITHUB_REPOSITORY is required');
        }

        if (this.threshold < 0 || this.threshold > 100) {
            throw new Error('CONFIDENCE_THRESHOLD must be between 0 and 100');
        }

        if (this.diffMaxChars < 1000) {
            throw new Error('DIFF_MAX_CHARS must be at least 1000');
        }
    }

    /**
     * Prints current configuration (for debugging)
     */
    printConfig() {
        console.log('Configuration:');
        console.log(`  - Repository: ${this.repo}`);
        console.log(`  - PR Number: ${this.prNumber}`);
        console.log(`  - Threshold: ${this.threshold}%`);
        console.log(`  - Label: ${this.labelName || '(none)'}`);
        console.log(`  - Skip Users: ${this.skipUsers.length > 0 ? this.skipUsers.join(', ') : '(none)'}`);
        console.log(`  - Fail on Detection: ${this.failOnDetection}`);
        console.log(`  - Dry Run: ${this.dryRun}`);
        console.log(`  - Max Diff Chars: ${this.diffMaxChars}`);
    }
}

/**
 * Fetches PR author and checks if they should be skipped
 * @param {Octokit} octokit - GitHub API client
 * @param {AIDetectionConfig} config - Configuration object
 * @returns {Promise<{author: string, title: string, body: string}|null>} PR info or null if should skip
 */
async function fetchPRInfo(octokit, config) {
    const [owner, repo] = config.repo.split('/');

    console.log(`\nFetching PR #${config.prNumber} from ${config.repo}...`);

    const { data: pr } = await octokit.rest.pulls.get({
        owner,
        repo,
        pull_number: config.prNumber,
    });

    const author = pr.user.login;
    console.log(`PR author: ${author}`);
    console.log(`PR title: ${pr.title}`);

    if (config.skipUsers.includes(author)) {
        console.log(`Author '${author}' is in skip list. Skipping analysis.`);
        return null;
    }

    return {
        author,
        title: pr.title,
        body: pr.body || '',
    };
}

/**
 * Fetches the diff for the PR
 * @param {AIDetectionConfig} config - Configuration object
 * @returns {string} PR diff content
 */
function fetchPRDiff(config) {
    console.log(`\nFetching diff for PR #${config.prNumber}...`);

    try {
        const command = `gh pr diff ${config.prNumber} --repo ${config.repo}`;
        const diff = execSync(command, {
            encoding: 'utf-8',
            maxBuffer: 50 * 1024 * 1024, // 50MB buffer
            env: { ...process.env, GH_TOKEN: config.ghToken },
        });

        // Truncate if too large
        if (diff.length > config.diffMaxChars) {
            console.log(
                `Diff truncated from ${diff.length} to ${config.diffMaxChars} characters`,
            );
            return diff.substring(0, config.diffMaxChars);
        }

        console.log(`Diff fetched successfully (${diff.length} characters)`);
        return diff;
    } catch (error) {
        console.error('Error fetching PR diff:', error.message);
        throw new Error('Failed to fetch PR diff.');
    }
}

/**
 * Builds the default analysis prompt
 * @param {string} diff - Git diff content
 * @param {Object} prInfo - PR information (title, body)
 * @returns {string} Formatted prompt
 */
function buildDefaultPrompt(diff, prInfo) {
    return `You are a code auditor specialized in detecting AI-generated content.
Analyze the following pull request diff and description.

PR Title: ${prInfo.title}
PR Description: ${prInfo.body || '(no description)'}

Rules for analysis:
1. Ignore imports, license headers, and config/generated files.
2. Look for common AI-generated patterns:
   - Overly verbose comments explaining obvious code
   - Consistent formatting that differs from project style
   - Generic variable naming patterns
   - Explanatory comments that seem auto-generated
   - Code that seems templated or boilerplate-heavy
3. Consider that experienced contributors may also write clean, well-documented code.
4. Be conservative - flag only when confidence is high.
5. Start your response EXACTLY with 'Confidence Score: X%' where X is 0-100.
6. Provide brief reasoning for your assessment.

Diff to analyze:
${diff}`;
}

/**
 * Escapes a string for safe use as a shell argument
 * @param {string} arg - Argument to escape
 * @returns {string} Escaped argument
 */
function escapeShellArg(arg) {
    // Replace single quotes with '\'' and wrap in single quotes
    return `'${arg.replace(/'/g, "'\\''")}'`;
}

/**
 * Runs Copilot CLI analysis on the diff
 * @param {string} diff - Git diff content
 * @param {Object} prInfo - PR information
 * @param {AIDetectionConfig} config - Configuration object
 * @returns {string} Copilot analysis output
 */
function runCopilotAnalysis(diff, prInfo, config) {
    console.log('\nRunning Copilot analysis...');

    // Build prompt
    const prompt = config.customPrompt || buildDefaultPrompt(diff, prInfo);

    try {
        // Execute copilot CLI in programmatic mode
        const escapedPrompt = escapeShellArg(prompt);
        const command = `copilot -p ${escapedPrompt}`;

        const output = execSync(command, {
            encoding: 'utf-8',
            maxBuffer: 10 * 1024 * 1024, // 10MB buffer
            env: { ...process.env, GH_TOKEN: config.ghToken },
        });

        console.log('--- Copilot Analysis Output ---');
        console.log(output);
        console.log('-------------------------------');

        return output;
    } catch (error) {
        console.error('Error running Copilot analysis:', error.message);
        throw new Error('Copilot analysis failed.');
    }
}

/**
 * Parses confidence score from Copilot output
 * @param {string} output - Copilot analysis output
 * @returns {number} Confidence score (0-100)
 */
function parseConfidenceScore(output) {
    // Match "Confidence Score: XX%" (case insensitive)
    const regex = /Confidence Score:\s*(\d+)%?/i;
    const match = output.match(regex);

    if (match && match[1]) {
        const score = parseInt(match[1], 10);
        console.log(`Parsed confidence score: ${score}%`);
        return score;
    }

    console.warn(
        'Could not parse confidence score from output. Defaulting to 0.',
    );
    return 0;
}

/**
 * Posts a comment on the PR with analysis results
 * @param {Octokit} octokit - GitHub API client
 * @param {AIDetectionConfig} config - Configuration object
 * @param {number} score - Confidence score
 * @param {string} analysis - Full analysis text
 */
async function postPRComment(octokit, config, score, analysis) {
    if (config.dryRun) {
        console.log('[DRY RUN] Would post PR comment (skipping)');
        return;
    }

    const [owner, repo] = config.repo.split('/');

    const body = `## 🤖 AI Content Detection Notice

**Confidence Score:** ${score}%

This PR has been flagged for potential AI-generated content. While AI assistance in coding is not prohibited, we encourage contributors to:

1. Ensure they understand and can explain all contributed code
2. Test the code thoroughly before submitting
3. Follow the project's contribution guidelines

<details>
<summary>📊 Analysis Details</summary>

${analysis}
</details>

---
*This is an automated analysis. Maintainers will review the PR accordingly.*`;

    try {
        await octokit.rest.issues.createComment({
            owner,
            repo,
            issue_number: config.prNumber,
            body,
        });
        console.log('Posted notice comment to PR');
    } catch (error) {
        console.error('Error posting PR comment:', error.message);
        throw new Error('Failed to post PR comment.');
    }
}

/**
 * Adds a label to the PR
 * @param {Octokit} octokit - GitHub API client
 * @param {AIDetectionConfig} config - Configuration object
 */
async function addPRLabel(octokit, config) {
    if (!config.labelName) {
        console.log('No label configured, skipping label addition');
        return;
    }

    if (config.dryRun) {
        console.log(`[DRY RUN] Would add label: ${config.labelName} (skipping)`);
        return;
    }

    const [owner, repo] = config.repo.split('/');

    try {
        // First, ensure the label exists (create if it doesn't)
        try {
            await octokit.rest.issues.getLabel({
                owner,
                repo,
                name: config.labelName,
            });
        } catch (labelError) {
            if (labelError.status === 404) {
                console.log(`Label '${config.labelName}' does not exist. Creating it...`);
                await octokit.rest.issues.createLabel({
                    owner,
                    repo,
                    name: config.labelName,
                    color: 'f9d0c4', // Light red/pink color
                    description: 'This PR contains potentially AI-generated content',
                });
                console.log(`Created label: ${config.labelName}`);
            }
        }

        // Add the label to the PR
        await octokit.rest.issues.addLabels({
            owner,
            repo,
            issue_number: config.prNumber,
            labels: [config.labelName],
        });
        console.log(`Added label: ${config.labelName}`);
    } catch (error) {
        console.error('Failed to add label:', error.message);
        // Don't throw - label is optional functionality
    }
}

/**
 * Main execution function
 */
async function main() {
    console.log('╔══════════════════════════════════════════════════════════════╗');
    console.log('║           AI Content Detection for Pull Requests             ║');
    console.log('║                    Microcks Project                          ║');
    console.log('╚══════════════════════════════════════════════════════════════╝');

    try {
        // 1. Load and validate configuration
        const cfg = new AIDetectionConfig();
        cfg.validate();
        cfg.printConfig();

        if (cfg.dryRun) {
            console.log('\n⚠️  DRY RUN MODE ENABLED');
            console.log('Analysis will run but no comments/labels/failures will be applied');
        }

        // 2. Initialize Octokit
        const octokit = new Octokit({ auth: cfg.ghToken });

        // 3. Check if PR author should be skipped
        const prInfo = await fetchPRInfo(octokit, cfg);
        if (!prInfo) {
            console.log('\n✅ Skipping analysis due to skip list match');
            process.exit(0);
        }

        // 4. Fetch PR diff
        const diff = fetchPRDiff(cfg);
        if (!diff || diff.trim().length === 0) {
            console.log('\n✅ No diff content found or diff is empty. Exiting.');
            process.exit(0);
        }

        // 5. Run Copilot analysis
        const analysis = runCopilotAnalysis(diff, prInfo, cfg);

        // 6. Parse confidence score
        const score = parseConfidenceScore(analysis);

        // 7. Take action based on score
        console.log('\n--- Results ---');
        if (score >= cfg.threshold) {
            console.log(
                `⚠️  Score (${score}%) meets or exceeds threshold (${cfg.threshold}%)`,
            );

            // Post comment
            await postPRComment(octokit, cfg, score, analysis);

            // Add label
            await addPRLabel(octokit, cfg);

            // Fail if configured (and not dry run)
            if (cfg.failOnDetection && !cfg.dryRun) {
                console.error(
                    '\n❌ Failing workflow due to AI content detection above threshold',
                );
                process.exit(1);
            } else if (cfg.dryRun) {
                console.log('[DRY RUN] Would fail workflow (skipping)');
            }
        } else {
            console.log(
                `✅ Score (${score}%) is below threshold (${cfg.threshold}%). No action taken.`,
            );
        }

        console.log('\n═══════════════════════════════════════════════════════════════');
        console.log('           AI Content Detection Complete');
        console.log('═══════════════════════════════════════════════════════════════\n');
    } catch (error) {
        console.error('\n❌ ERROR:', error.message);
        if (error.stack) {
            console.error('Stack trace:', error.stack);
        }
        process.exit(1);
    }
}

// Run main function
main();
