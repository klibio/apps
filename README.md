# io.klib.apps

```bash
# build
export SONATYPE_BEARER=
export GPG_KEYNAME=
export GPG_PASSPHRASE=
```

-H 'Authorization: Bearer '$SONATYPE_BEARER \
```bash
URL=https://central.sonatype.com/repository/maven-snapshots/
FILE=io.zip
curl -vv \
-H 'accept: text/plain' \
-H 'Authorization: Basic M2JXTkVlejU6VFcvdlA3MGd4c0szQnV2emdNQUtFSWJlT3NrMjUveWxnR3FIYko3b0pEMkI=' \
-H 'Content-Type: multipart/form-data' \
-F upload=@$FILE \
$URL


-F name=manual \
-F publishingType=USER_MANGED \
```
