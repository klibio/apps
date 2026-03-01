# instructions for github actions

* create matrix builds starting from the project use JRE up to the most recent Java LTS release
    current Java LTS releases are 8, 11, 17, 21, 25
* store jar or executable build results inside run for artifact download
* configure upload to SONATYPE maven repo if applicable with SONATYPE bearer secret
* use timestamps for artifacts uploads
    ```yaml
      set-timestamp:
        runs-on: ubuntu-latest
        outputs:
          steps: ${{ steps.timestamp.outputs }}
        steps:
          - name: Set Timestamp
            id: timestamp
            run: echo "TIMESTAMP=$(date +"%Y%m%d%H%M%S")" >> $GITHUB_ENV
    ```