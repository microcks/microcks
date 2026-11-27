# Security Policy

## Reporting a Vulnerability

Please do not report suspected vulnerabilities through public GitHub issues, discussions, pull requests or other public channels.

Report a vulnerability affecting any Microcks component or website privately by emailing the Microcks Security Team at [security@microcks.io](mailto:security@microcks.io).

Include as much of the following information as possible:

- The affected component, repository and version,
- A description of the vulnerability and its potential impact,
- Detailed steps to reproduce the issue or a proof of concept,
- Any known mitigations or suggested remediation,
- Whether the vulnerability has already been disclosed or shared elsewhere.

The Security Team will acknowledge receipt within five business days. We may contact you for additional information while we validate the report, determine its severity and affected versions, and develop a remediation. Please keep the report confidential until a coordinated disclosure date has been agreed upon.

## Coordinated Disclosure

Microcks targets coordinated public disclosure within 90 calendar days of receiving a vulnerability report. When a fix or effective mitigation is available earlier, the Security Team may coordinate an earlier disclosure with the reporter.

The timeline may be extended when remediation is unusually complex, upstream or downstream coordination is required, or an earlier disclosure would create additional risk for users. The Security Team will notify the reporter of material timeline changes and agree on a revised disclosure date.

When appropriate, the Security Team will publish a GitHub Security Advisory that identifies affected and fixed versions, available mitigations, severity, credits and a CVE identifier. Disclosure occurs only after the fix or mitigation and the advisory are ready, unless active exploitation or another exceptional circumstance requires a different response.

## Supported Versions

Microcks releases follow the [Semantic Versioning](https://semver.org/) specification. Security fixes are applied to the current development branch and may be backported to actively maintained release branches based on severity, impact and feasibility. Each published security advisory identifies the affected and fixed versions and any available mitigations.

## Security Team

The Security Team is made up of a subset of the project [Maintainers](https://github.com/microcks/.github/blob/main/GOVERNANCE.md#maintainers-code-owners-contributors-and-adopters) and [Code Owners](https://github.com/microcks/.github/blob/main/GOVERNANCE.md#maintainers-code-owners-contributors-and-adopters) who are willing and able to respond to vulnerability reports.

The Security Team is responsible for acknowledging and triaging reports, coordinating remediation, identifying a Fix Lead when needed, communicating with reporters, preparing advisories and coordinating disclosure. Information about an undisclosed vulnerability is shared only with people who need it to investigate, remediate or coordinate the disclosure.

## Credits

Sections of this document have been borrowed and inspired from the [OpenEBS](https://github.com/openebs/community/blob/72506ee3b885bd06324b82a650fcd3a61e93eef0/SECURITY.md) project.
