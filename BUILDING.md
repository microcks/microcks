# Building guide

**Want to contribute and build stuff? Great!** ✨ We try to make it easy, and all contributions, even the smaller ones, are more than welcome. This includes bug reports, fixes, documentation, examples...

First, you may need to read our [global contribution guide](https://github.com/microcks/.github/blob/master/CONTRIBUTING.md) and then to read this page.

## Reporting an issue

This project uses GitHub issues to manage the issues. Open an issue directly in GitHub.

If you believe you found a bug, and it's likely possible, please indicate a way to reproduce it, what you are seeing and what you would expect to see.
Don't forget to indicate your Java, Maven and/or Docker version.

## Setup

### Git hooks

The repository ships its client-side hooks in the versioned `.githooks` directory (a `pre-commit` hook running
`mvn spotless:check` and a `commit-msg` hook enforcing Conventional Commits and sign-off).

Running a build from the repository root (`mvn validate`, `mvn clean install`, ...) wires them automatically by setting
the **repository-local** Git config:

```
$ git config --local core.hooksPath .githooks
```

Notes:

- Only this repository's config is touched. Your global/system Git configuration and your other projects are never
  modified.
- The path is relative, so Git resolves it from the top level of the current working tree. This makes it work
  identically in the main clone and in any [git worktree](https://git-scm.com/docs/git-worktree), where `.git` is a
  file and has no `hooks` directory of its own.
- If you previously built Microcks, stale copies may still live in `.git/hooks`; they are now ignored and can be
  deleted.
- Pass `-Dgit.hooks.skip=true` to skip this step, or run `git commit --no-verify` to bypass the hooks for a commit.

## Build

### Build the whole project

You need to have [Apache Maven](https://maven.apache.org) (version >= 3.5) up and running as well as a valid Java Development Kit (version >= 25) install to build the project.

```
$ git clone https://github.com/microcks/microcks.git
[...]
$ cd microcks
$ mvn clean install
[...] 
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for Microcks 1.12.0-SNAPSHOT:
[INFO] 
[INFO] Microcks ........................................... SUCCESS [  0.408 s]
[INFO] Microcks Model ..................................... SUCCESS [  1.771 s]
[INFO] Microcks Util ...................................... SUCCESS [  4.590 s]
[INFO] Microcks EL ........................................ SUCCESS [  1.018 s]
[INFO] Microcks App ....................................... SUCCESS [ 40.540 s]
[INFO] Microcks Async Minion .............................. SUCCESS [  8.425 s]
[INFO] Microcks Distros ................................... SUCCESS [  0.034 s]
[INFO] Microcks Uber App .................................. SUCCESS [  0.850 s]
[INFO] Microcks Uber Async Minion ......................... SUCCESS [  3.818 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:01 min
[INFO] Finished at: 2025-04-07T18:07:39+02:00
[INFO] ------------------------------------------------------------------------
```

### Build and run webapp jar

For now, there's still a problem with Frontend integration tests configuration so you should disable them using the following flag:

```
$ cd webapp
$ mvn -Pprod package
[...]
$ java -jar target/microcks-x.y.z-SNAPSHOT.jar
```

### Build and run webapp Docker image

```
$ cd webapp
$ mvn -Pprod clean package && docker build -f src/main/docker/Dockerfile -t microcks/microcks-uber:nightly .
[...]
$ cd ../install/docker-compose
$ docker-compose -f docker-compose.yml up -d
```
After spinning up the containers, you will now have access to Keycloak for account management, and microcks webapp to setup mocking, etc.

You can login to keycloak on `http://localhost:18080/` with username and password `admin`.
You can login to microcks webapp with the username `admin` and password `microcks123`.

## Before you contribute

To contribute, use GitHub Pull Requests, from your **own** fork.
