#!/bin/sh
#
# In order to properly unset DATABASE_URL in your shell's env, source this
# script, as opposed to executing it directly: `. ./scripts/teardown.sh`.

echo "Stopping postgres instance..."
pg_ctl -D /usr/local/var/postgres stop

echo "Unsetting app-specific env variables..."
unset DATABASE_URL

echo "Teardown complete!"

