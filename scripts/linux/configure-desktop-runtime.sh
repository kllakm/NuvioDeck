#!/usr/bin/env bash

set -euo pipefail

if [[ -n "${LOCAL_PROPERTIES_BASE64:-}" ]]; then
    printf '%s' "${LOCAL_PROPERTIES_BASE64}" | base64 --decode > local.properties
else
    : > local.properties
fi

sed -i -E '/^[[:space:]]*(sdk\.dir|SENTRY_(DSN|DESKTOP_DSN|AUTH_TOKEN)|NUVIO_RELEASE_(STORE_FILE|STORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD)|NUVIO_MACOS_(SIGNING_IDENTITY|NOTARY_APPLE_ID|NOTARY_TEAM_ID|NOTARY_PASSWORD|NOTARY_KEYCHAIN_PROFILE|NOTARY_KEYCHAIN_PATH))[[:space:]]*=/d' local.properties
