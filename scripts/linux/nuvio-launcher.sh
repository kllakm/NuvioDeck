#!/bin/sh
# Launcher used inside the Flatpak bundle
if [ -x /app/opt/NuvioDeck/bin/NuvioDeck ]; then
    exec /app/opt/NuvioDeck/bin/NuvioDeck "$@"
fi
exec /app/opt/Nuvio/bin/Nuvio "$@"
