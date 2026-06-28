# 🚀 Microcks AsyncAPI 3.0 Request/Reply Guide

🗓 *Last updated: June 28, 2026* | 📝 [Improve this page](https://github.com/AishaRiaz-Dev/microcks/edit/main/path/to/your/file.md)

---

## Overview

This guide shows you how to use the **[AsyncAPI 3.0](https://www.asyncapi.com/specifications)
Request/Reply pattern** with Microcks. While traditional event-driven architectures rely on "fire-and-forget" publish/subscribe mechanisms, many enterprise workflows require a conversational pattern where a request message expects a synchronous or asynchronous reply on a dedicated destination.

**Microcks** supports AsyncAPI 3.0 Request/Reply as a core application behavior. This means Microcks can automatically spin up mocks that listen on a request channel, evaluate incoming payloads, and dynamically route compliance-validated replies back to your consuming applications.

Let’s start! 🚀

## 1. Set up the Async feature infrastructure

Before Microcks can process asynchronous request/reply streams, ensure the core async features are enabled within your deployment configuration.

If you have used the [Operator-based installation](https://microcks.io/documentation/guides/installation/operator/)
 of Microcks, verify or add the following properties to your `MicrocksInstall` custom resource definition:

```yaml
apiVersion: microcks.github.io/v1alpha1
kind: MicrocksInstall
metadata:
  name: microcks
spec:
  [...]
  features:
    async:
      enabled: true
      defaultFrequency: 3
```


The `async` feature must be set to **`true`**. If you have used the **Helm Chart-based installation** of Microcks, ensure your corresponding `Values.yaml` file includes this configuration fragment:

```yaml
[...]
features:
  async:
    enabled: true
    defaultFrequency: 3
```

Once infrastructure configuration is verified, Microcks will evaluate Request/Reply operations natively when processing AsyncAPI 3.0 files.

## 2. Use Request/Reply patterns in AsyncAPI 3.0

AsyncAPI 3.0 decouples channels from operations, allowing explicit definitions of communication behaviors. Under the `operations` block, Microcks processes the `reply` attribute to determine how a mock response should be routed.

Based on the project reference file `webapp/src/test/resources/io/github/microcks/util/asyncapi/user-signedup-asyncapi-3.0-reply.yaml`, Microcks supports two distinct execution patterns: **Static Channel Replies** and **Dynamic Header-Based Routing**.

### Pattern A: Static Channel Replies

In this approach, the reply destination is hardcoded directly inside the specification contract. Microcks always publishes mock responses to this explicit channel.

```yaml
asyncapi: '3.0.0'
info:
  title: User SignedUp API
  version: 1.0.0
channels:
  user/signedup:
    address: user/signedup
  user/signedup/reply:
    address: user/signedup/reply
operations:
  userSignedUpRequest:
    action: receive
    channel:
      $ref: '#/channels/user/signedup'
    reply:
      channel:
        $ref: '#/channels/user/signedup/reply'
      messages:
        - $ref: '#/components/messages/UserSignedUpReply'
```

### Pattern B: Dynamic Header-Based Routing (replyTo)

For scalable or multitenant event networks, you can configure Microcks to look for the reply address dynamically at runtime inside the incoming message headers using a runtime expression.

```yaml
asyncapi: '3.0.0'
info:
  title: User SignedUp API (Dynamic)
  version: 1.0.0
channels:
  user/signedup:
    address: user/signedup
operations:
  userSignedUpDynamic:
    action: receive
    channel:
      $ref: '#/channels/user/signedup'
    reply:
      address:
        location: '$message.header#/replyTo'
      messages:
        - $ref: '#/components/messages/UserSignedUpReply'
```

## Adding Examples for Mocking Data

Because Microcks uses **example-driven mocking engine mechanics**, you must attach concrete examples to both your request message schemas and reply message schemas inside the `components` section:

```yaml
components:
  messages:
    UserSignedUpRequest:
      payload:
        type: object
        properties:
          userId:
            type: string
          email:
            type: string
      examples:
        - name: Aisha
          payload:
            userId: 'USR-992'
            email: laurent@microcks.io
        - name: RandomUser
          payload:
            userId: 'USR-{{randomNumeric(3)}}'
            email: '{{randomEmail()}}'
    UserSignedUpReply:
      payload:
        type: object
        properties:
          status:
            type: string
          confirmedAt:
            type: string
      examples:
        - name: AishaReply
          payload:
            status: 'SUCCESS'
            confirmedAt: '{{backInTime(0d)}}'
```
​💡 Note: The `{{randomEmail()}}` and `{{backInTime(0d)}}` notations are powerful built-in Templating functions that allow Microcks to evaluate and distribute dynamic mock text payloads on the fly! 😉

## 3. Validate your mocks

To verify that Microcks is successfully simulating the request/reply lifecycle, you can run a quick simulation client. In a real-world scenario, your application publishes a message to the request channel, and Microcks instantly answers back on the designated reply destination.

You can use the local utility scripts from our tooling repository to monitor this interaction:

```bash
git clone [https://github.com/microcks/api-tooling.git](https://github.com/microcks/api-tooling.git)
cd api-tooling/async-clients/mock-validation-client
npm install

# Start a consumer listening exclusively on the designated reply channel destination
node reply-listener.js user/signedup/reply
```

When an application publishes a request payload matching the `Aisha` example definition, Microcks catches the event, interprets the target destination from the operation details, and outputs the following evaluated reply message mock:

```text
Connecting to channel: user/signedup/reply...
Received Reply Mock from Microcks:
{
  "status": "SUCCESS",
  "confirmedAt": "2026-06-28T18:01:56Z"
}
```

​🎉 **Fantastic**! Microcks successfully validated the incoming structure, matched it to an example block, evaluated the dynamic time templating expressions, and emitted the target reply on the proper channel hook.


## 4. Run AsyncAPI contract tests

The final step is leveraging the automated testing capability of Microcks to assert that your real development or QA environments are processing request/reply configurations cleanly without architectural drift.

Imagine validating an active microservice deployed inside a QA environment. Use a mock utility to pump simulated runtime requests through your pipeline:

```bash
node producer.js --channel user/signedup --payload '{"userId":"USR-101","email":"john@doe.com"}'
```

Keep this process alive. To run an active compliance sweep inside the Microcks web console, navigate to the dashboard, click **New Test**, and populate the validation form fields:

* **Test Endpoint:** Specify the connection string matching your environment broker topology.
* **Operation:** Select your targeted incoming request handler operation block (e.g., `userSignedUpRequest`).
* **Runner:** Choose **ASYNC API SCHEMA** to enforce explicit validation rules across both channels simultaneously.
* **Timeout:** Retain the standard window or set it up to 10 seconds to await responses comfortably.

Microcks will evaluate the transactions. If your application sends a fully structured payload matching the contract specifications, Microcks presents a successful validation status banner.


### Evaluating Schema Drift and Failures

Let's modify the producer output slightly to simulate bad or broken code. If an engineering update drops required string inputs or alters expected properties (such as replacing `userId` with an invalid parameter name like `user_identifier`):

```bash
# Simulating a breaking schema alteration on the active network
node producer.js --channel user/signedup --payload '{"user_identifier":"USR-101","email":"john@doe.com"}'
```

Rerun the test job inside the Microcks dashboard. The schema validator catches this structural change immediately, reporting a clean failure layout:

```text
Test Failure: Operation userSignedUpRequest
Severity: High
Error Details: Payload content does not validate against schema rules defined in AsyncAPI contract. 
Missing required property: [ userId ]. Additional property [ user_identifier ] is not allowed by specifications.
```

🥳 **Perfect**! When your application stack starts transmitting garbage fields or ignoring contract definitions, Microcks halts testing execution and provides context on exactly where the formatting broke down.

## Wrap-Up

In this guide, we explored how Microcks manages **AsyncAPI 3.0 Request/Reply mechanics** to simulate two-way messaging pathways using both static definitions and header-driven dynamic `replyTo` configurations. Leveraging this validation loop ensures complex message interactions are tracked, caught, and structured correctly across your event networks.

Thanks for reading! Let us know your thoughts or share feedback directly with our team on our [Discord chat](https://microcks.io/discord-invite)  🐙


