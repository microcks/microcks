# Microcks Governance

This document defines governance policies for the Microcks project.

## Principles
The Microcks project community adheres to the following principles:

- **Open**: The Microcks community strives to be open, accessible and welcoming to everyone. Anyone may contribute, and contributions are available to all users according to open source values and licenses.
- **Transparent** and **accessible**: Any changes to the Microcks source code and collaborations on the project are publicly accessible (GitHub code, issues, PRs, and discussions).
- **Merit**: Ideas and contributions are accepted according to their technical merit and alignment with project objectives, scope, and design principles.
- **Vendor-neutral**: Microcks is designed and maintained to be fully aligned with the [principles](https://contribute.cncf.io/maintainers/community/vendor-neutrality/) of the Cloud Native Computing Foundation (CNCF).

Join us 👉 https://microcks.io/community/

## Maintainers, Code Owners, Contributors and Adopters
The Microcks project has four roles. All project members operate in one (or more) of these roles:

| Level | Role | Responsibilities |
| :---  | :--- | :--- |
| 1 | **Maintainer** | Vote, Develop roadmap and contribution guidelines; Review, Approve/Reject, Merge, and Manage repositories. Maintainers are elected or removed by the current maintainers. A Maintainer has authority over the entire Microcks project: the organization and every project, sub-project and repo within the organization.|
| 2     | **Code Owner**| Have special expertise in a particular domain within the Microcks project. The domain may be a sub-project, repo or other responsibility as defined by the Maintainers. The maintainers grant a code owner (alias Domain Maintainers) a set of authorities and responsibilities for the domain. Code owners are expected to join maintainer and community meetings when required. A code owner has no responsibilities for the entire project, organization or projects outside their domain. Code owners role, refer to [GitHub CODEOWNERS](https://docs.github.com/fr/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners) capabilities.|
| 3     | **Contributor** | Contribute code, test and document the project. A contributor’s authority applies to one or more sub-projects. Microcks is a very welcoming community and is eager to onboard and help anyone from the open source community to contribute to the project. |
| 4     | **Adopter** | Use the Microcks project, with or without contributing to the project. Adopters are encouraged to raise issues, provide feedback and participate in discussions on sub-projects within a public forum and community. |

- Maintainer and Code Owners list: https://github.com/microcks/.github/blob/main/MAINTAINERS.md
- Contributors list: (DevStats) [new contributors over the last 6 months](https://microcks.devstats.cncf.io/d/52/new-contributors-table?orgId=1&from=now-6M&to=now) / (GitHub) [contributors on Microcks main repo](https://github.com/microcks/microcks/graphs/contributors).
- Adopters (public) list: https://github.com/microcks/.github/blob/main/ADOPTERS.md
> 📢 If you're using Microcks in your organization, please add your company name to this [list](https://github.com/microcks/.github/blob/main/ADOPTERS.md) 🙏 It really helps the project to gain momentum and credibility. It's a small contribution back to the project with a significant impact.

## Contributor ladder
To become a maintainer, you need to get involved with the Microcks project on GitHub and demonstrate commitment and qualities:

   * Participation: For three months or more. Examples include participation in discussions, contributions and code or documentation reviews.
   * Collaboration: Demonstrate the ability to work with others, take on new ideas and help others succeed.
   * Availability (ideally full-time): Be available on Slack, Discord, GitHub, and email so you can help move the project forward in a timely way.
   * Respect: Alignment with Microcks and CNCF code of conduct and guiding principles.
     
### Voting in and voting out maintainers

1. Maintainers make a public announcement during community meetings,
2. During this meeting, a maintainer nominates to add a new maintainer or remove an existing maintainer,
3. The nominator will open a PR to the [centralized](https://github.com/microcks/.github/blob/main/MAINTAINERS.md) Maintainer and Code Owners list,
4. Maintainers vote via GitHub PR comments, with a 2 week deadline. Anyone in the community is welcome to comment. Community comments will be considered but not counted toward the vote,
5. After two weeks, any maintainer who abstains from voting will not be counted towards the vote,
6. Decision is approved with a super-majority: 66% 2/3) or more of maintainers who have voted within two weeks,
7. If 66% (2/3) of all maintainers have approved within two weeks, the voting is closed early.

For maintainers voted in, permissions are immediately added. For maintainers, voted-out permissions are immediately removed.

### Becoming a Code Owner 
A Code Owner (alias Domain Maintainers) is appointed by the maintainers to recognize a contributor with expertise and authority in a specific domain. Code Owners are appointed to have elevated privileges, authority and specific responsibilities. The code owner role is part of the Microcks contributor ladder and is the primary path from contributor to maintainer. The roles and responsibilities of code owners are scoped. A person can have one or more code owner responsibilities.

Code owners are enabled to act independently. They do not have responsibilities or voting rights over the entire project or organization. They are expected to participate with the community, but they are not expected to participate in maintainer meetings unless requested.

### Remaining a Maintainer or Code Owner

If a maintainer or code owner can no longer fulfill their commitments, they should consult with the maintainers and either take a sabbatical or step down from their role. All maintainers share the responsibility of ensuring the group operates with consistent dedication. If a maintainer or code owner fails to meet their commitments, they may be voted out by the maintainers and transitioned to emeritus status.

## Adding or Removing Sub Projects
Microcks maintainers have the authority to add or remove sub-projects or repositories as needed. We follow a careful approach when making these changes: any new sub-project must serve a long-term purpose that is clearly distinct from existing ones, while sub-projects slated for removal must be shown to have either outlived their usefulness, become deprecated or unmaintainable.

When a sub-project is removed, it will be archived as-is within the Microcks-archive organization, along with its associated repositories, ensuring transparency and historical reference.

## Conflict Resolutions
Typically, it is assumed that disputes will be resolved amicably by those involved. However, if the situation becomes more serious, conflicts will be resolved through a voting process. A supermajority of votes from project maintainers is required to make a decision, and the project lead has the final say in the ruling.

## Community Meetings
[Microcks](https://microcks.io/) hosts two monthly community meetings tailored for different time zones:

- **APAC-friendly Meeting:** Second Thursday of each month  
  - Time: 9–10 a.m. CET / 1–2 p.m. Bengaluru
- **America-friendly Meeting:** Fourth Thursday of each month  
  - Time: 6–7 p.m. CET / 1–2 p.m. EST / 9–10 a.m. PST

Here’s how to join and participate: https://github.com/microcks/community/blob/main/JOIN-OUR-MEETINGS.md

The maintainers will also have closed meetings to discuss security reports or Code of Conduct violations. Any maintainer in charge should schedule such meetings upon receiving a security issue or CoC report. All current Maintainers must be invited to such closed meetings, except for any maintainer accused of a CoC violation.

## Governance Changes
Changes to governance policy and any supporting documents must be agreed upon and approved by 66% (2/3) of the maintainers either by vote or by review and approval of a PR on the document.

This Project Governance is a living document. As the Microcks community and project continue to evolve, maintainers are **committed** to improving and openly sharing our governance model, ensuring transparency and collaboration every step of the way.

## Code of Conduct
Microcks follow the [Code of Conduct](CODE_OF_CONDUCT.md), which is aligned with the [CNCF Code of Conduct](https://github.com/cncf/foundation/blob/master/code-of-conduct.md).

## Credits
Thanks to [Dawn Foster](https://github.com/geekygirldawn) for the inspiring talk and valuable insights at KubeCon Europe 2022: "Good Governance Practices for CNCF Projects":
[Info](https://contribute.cncf.io/resources/videos/2022/good-governance-practices/), [Recording](https://youtu.be/x0tgEpIER1M?si=0EMgdfA1j5kxpXlW) and [slide deck](https://static.sched.com/hosted_files/kccnceu2022/7c/Good_Governance_CNCF_Projects.pdf) 👀

Sections of this document have been borrowed and inspired from the [CoreDNS](https://github.com/coredns/coredns/blob/master/GOVERNANCE.md), [Kyverno](https://github.com/kyverno/kyverno/blob/main/GOVERNANCE.md), [OpenEBS](https://github.com/openebs/community/blob/72506ee3b885bd06324b82a650fcd3a61e93eef0/GOVERNANCE.md) and [fluxcd](https://github.com/fluxcd/community/blob/main/GOVERNANCE.md) projects.
