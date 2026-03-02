# development

## debugging SONATYPE snapshot deployments

MIND that this requires either a configured environment variable SONATYPE_BEARER
or a `~/.bnd/settings.json` with
```json
{
  "SONATYPE_BEARER": "sonatype-bearer-token-here",
}
```

copy `DBG_sonatype.*` to `cnf/ext` folder will provide additional `DBG_*` repos for investigating.

When you already made release to Sonatype `./gradlew release` you can use the created `release.mvn` file.

```java
-plugin.93.sonatype_snapshots:\
    aQute.bnd.repository.maven.provider.MavenBndRepository; \
    	tag         = DBG; \
        snapshotUrl = ${sonatype_snapshots}; \
        index       = ${workspace}/cnf/release.mvn; \
        name        = 'DBG bnd sonatype snapshots'
```