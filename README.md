# io.klib.apps

[![Continuous Integration](https://github.com/klibio/apps/actions/workflows/ci.yml/badge.svg)](https://github.com/klibio/apps/actions/workflows/ci.yml)

```bash
# build
./gradlew clean build

# OSGi tests can be bounded globally via Gradle property (default: 5 min)
./gradlew testOSGi -PtestTaskTimeoutMinutes=10

# release
export SONATYPE_BEARER=
export GPG_KEY_ID=
export GPG_PASSPHRASE=

./gradlew clean release

```

