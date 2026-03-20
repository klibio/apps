#!/usr/bin/env bash
set -ev

# this is importing the private key on the CI machine
if [[ -n "${GPG_PRIVATE_KEY}" && -n "${GPG_PASSPHRASE}" ]]; then
    echo -e "#\n# GPG - importing private key on local machine\n#\n"
    echo "${GPG_PRIVATE_KEY}" | \
    gpg --batch \
        --yes \
        --pinentry-mode loopback \
        --passphrase "${GPG_PASSPHRASE}" \
        --import

    echo -e "#\n# list secret keys\n#\n"
    gpg --list-secret-keys --keyid-format LONG
    export GPG_TTY=$(tty)
fi

# verify that the GPG agent is working by signing and verifying a test message
# and configure MAVEN_SIGNING_ARGS to use the GPG key and passphrase for signing, or skip signing if not configured
if [[ -n "${GPG_KEY_ID}" && -n "${GPG_PASSPHRASE}" ]]; then
    echo -e "#\n# GPG signing to configure and activate GPG agent\n#\n"
    echo "test" | \
    gpg --batch \
        --yes \
        --local-user "${GPG_KEY_ID}" \
        --pinentry-mode loopback \
        --passphrase "${GPG_PASSPHRASE}" \
        --clearsign | \
    gpg --verify
    echo -e "#\n# GPG signing to configure and activate GPG agent\n#\n"
else 
    echo -e "#\n# GPG signing environment variables not configured, SKIPPING GPG signing\n#\n"
fi

# build
echo -e "#\n# gradle and maven versions\n#\n"
./gradlew --no-daemon --version

echo -e "#\n# build bnd and bndtools\n#\n"
./gradlew \
    --no-daemon \
    clean release "$@"

