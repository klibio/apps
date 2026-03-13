# io.klib.apps

OSGi application bundles for [klib.io](https://klib.io)

* providing OSGi application services and utilities
* `io.klib.app.p2.mirror` — Eclipse p2 repository mirroring service
* built with [bnd](https://bnd.bndtools.org) and [Gradle](https://gradle.org)
* running on `Java 21`
* CI/CD pipeline with automated builds and releases
* published to [Maven Central](https://central.sonatype.com) via Sonatype
* documentation via GitHub Pages
    * provide download of [latest build artifacts](downloads)

## Source repository

[github.com/klibio/apps](https://github.com/klibio/apps)

## Quick Links

- [Downloads](downloads) — latest OSGi bundles from the last successful build on `main`

## Build

```bash
# build
./gradlew clean build

# OSGi tests (default timeout: 5 min)
./gradlew testOSGi -PtestTaskTimeoutMinutes=10

# release
export SONATYPE_BEARER=
./gradlew clean release
```

<sup>last edit: {{ 'now' | date: "%Y%m%d-%H%M%S" }}</sup><br/>
{% assign source_revision = site.source_revision | default: site.github.build_revision %}
{% assign repository_nwo = site.github.repository_nwo | default: site.repository | default: 'klibio/apps' %}
{% if source_revision and source_revision != '' %}
<sup>source-revision: <a href="https://github.com/{{ repository_nwo }}/commit/{{ source_revision }}">{{ source_revision | slice: 0, 12 }}</a></sup>
{% else %}
<sup>source-revision:n/a</sup>
{% endif %}
