# Microcks AI Contribution Policy

Contributors may use Generative AI tools (such as Large Language Models, GitHub Copilot, Cursor, Claude Code, etc.) when contributing to Microcks. As with any development tool, you are responsible for the quality of the result and for understanding what you submit.

## Guiding Principle

Microcks is a community-driven organization that values expertise, clear communication, and personal responsibility. To maintain trust in the AI era, we follow a key principle:

**What you do with Generative AI reflects on you.**

Successful ongoing contribution to this community is contingent on building trust with the members of the organization through ongoing collaboration. Whether or not Generative AI tools were involved, you are fully accountable for the correctness, security, and clarity of your contributions. Human review is required for all code, including code generated or assisted by Generative AI tools. 

## Understanding and Responsibility

* **You must understand your contribution.** Do not submit code, documentation, or other content that you do not fully understand. You should be able to explain to reviewers what your contribution does, how it works, and how you verified it. You should be able to answer reviewers' questions yourself, without recourse to AI.
* **Review all AI-generated content.** Before submitting, verify that the contribution is correct, tested, and follows the project's contributing guidelines.
* **You own what you submit.** As the PR author, you are responsible for all changes, regardless of the tools used to create them. 

## Acceptable Use

We recognize that Generative AI can be a helpful tool. You may use it to:
* Explain parts of the codebase you don’t understand.
* Auto-complete routines or boilerplate code (such as error handling, test scaffolding, function signatures).
* Reformat or refactor existing content.
* Brainstorm ideas for implementation cases, but write the actual code yourself.
* Draft test cases which you subsequently review, revise, and simplify before submission.

These uses are acceptable provided you are involved in the entire process, fully understand the content, and take personal responsibility for it.

## What NOT to Do (Unacceptable Use)

It is **not acceptable** to use Generative AI tools in the following ways:

* **Do not instruct AI tools to directly respond to comments.** You must not use AI tools to auto-reply to maintainer review comments on Pull Requests or Issues. Reviewers want to engage with you, not receive generic AI-generated responses. If you use AI assistance to formulate responses, ensure they genuinely reflect your understanding and that you can defend your reasoning.
* **Do not use AI tools in commit trailers.** AI tools MUST NOT be added to `Signed-off-by`, `Co-authored-by`, `Assisted-by`, or similar tags in commit messages. Only humans can legally certify the [Developer Certificate of Origin (DCO)](https://developercertificate.org/). The `Signed-off-by` tag certifies that you wrote the code or otherwise have the right to submit it. An AI tool is not a legal entity and cannot make this certification.
* **Do not submit large, unfocused AI-generated PRs.** AI tools can generate content faster than reviewers can read it. Keep PRs focused. Maintainers MAY close pull requests that are too large to review effectively.
* **Avoid verbose AI-generated content.** Trim down unnecessary verbosity in PR descriptions, commit messages, and code comments. Be clear, concise, and specific. 
* **Do not use AI for `good first issue` tasks.** Issues labeled as `good first issue` are intended for new contributors to learn the codebase and gain experience. Using AI tools to complete these issues is against their purpose.
* **Do not plagiarize.** Do not submit work produced via AI tooling that copies from external sources without correct attribution or licensing.

## Quality Standards

* **Meet the same standards.** AI-assisted contributions are reviewed to the same standard as any other contribution. Code must be correct, maintainable, well-tested, and consistent with project conventions.
* **Ensure licensing compliance.** AI-generated content must not introduce material under licenses incompatible with the project's Apache 2.0 License.
* **Test your changes.** All code contributions must be tested according to the project's standards, whether AI-assisted or not.

## AI-Assisted Reviews and Proposals

* **AI reviews don't replace human review.** Automated checks and AI-generated review comments can provide useful signals, but they do not satisfy the project's review requirements. Maintainers make the merge decisions.
* **Do not request AI-assisted reviews on PRs.** Do not invoke AI bots or tools to post review comments on project PRs. If AI-assisted review is desired, maintainers will request it.
* **Proposals require deep understanding.** If you use AI tools to help draft architectural proposals, you must have a thorough understanding of the problem space, proposed solution, and alternatives. The rationale matters most.

## Transparency & Attribution

We expect contributors to declare when Generative AI was used to prepare a submission. 

* **Explicit Disclosure:** You must explicitly disclose AI usage in your Pull Request description (e.g., checking the box in the PR template). 
* **Developer Certificate of Origin:** If you submit AI-assisted contributions, you are personally certifying via the DCO that you have the right to contribute the content under the project's license.

If you’re unsure about the licensing of code you created using Generative AI tools, **don’t submit it**.

## Consequences of Non-Compliance

Pull Requests that violate this policy may be closed. Specific cases include:
* PRs where the contributor cannot explain or defend the changes during review.
* PRs with generic, inauthentic responses to reviewer feedback that demonstrate a lack of understanding.
* Large, unfocused PRs that appear to be bulk AI-generated content.
