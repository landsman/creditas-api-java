# Development

`make build` checks formatting, compiles with Error Prone and NullAway, runs the tests, checks the declared
dependencies and packages the jars. `make` lists the targets. A JDK 21 or newer is enough; `mise.toml` pins the one
used here.

## Releasing

Only a tag releases: merging to `main` publishes nothing. `make release` asks for the tag, suggesting the next
patch, refuses one that exists or a checkout that is not a clean `origin/main`, tags, waits for
[release.yml](../.github/workflows/release.yml) to publish both modules to Maven Central and writes the release page,
a pre-release while it is 0.x or an `-rc`. **Central is immutable**: a version, once published, can never be
replaced or deleted, and the next fix is the next tag. An `-rc` tag (`v0.1.0-rc1`) tries the pipeline without taking
a real version number.

### Maven Central, set up once

The `cz.insuit` namespace, the Portal user token and the signing key are shared with the other `cz.insuit` libraries;
how each was made is in
[fio-api-java's development page](https://github.com/landsman/fio-api-java/blob/main/.docs/development.md#maven-central-set-up-once).
This repository needs only the four secrets, typed in from the same 1Password items:

```sh
gh secret set MAVEN_CENTRAL_USERNAME -R landsman/creditas-api-java
gh secret set MAVEN_CENTRAL_PASSWORD -R landsman/creditas-api-java
gh secret set SIGNING_KEY -R landsman/creditas-api-java          # the armored private key
gh secret set SIGNING_KEY_PASSWORD -R landsman/creditas-api-java # its passphrase
```

Without the signing key the build still publishes to `~/.m2` (`make publish-local`), unsigned; Central refuses an
unsigned upload, so a release without the secrets fails at the Portal's validation, before anything is public.
