#!/bin/sh
#
# In order to properly export DATABASE_URL into your shell's env, source this
# script, as opposed to executing it directly: `. ./scripts/setup.sh`.

echo "Setting DATABASE_URL..."
read -p "db username? " db_username
read -s -p "db password? " db_pwd
export DATABASE_URL=postgres://$db_username:$db_pwd@localhost/gingrsnapdb
echo

echo "Setting AWS credentials..."
read -s -p "AWS access key? " aws_access_key
export AWS_ACCESS_KEY=$aws_access_key
echo
read -s -p "AWS secret key? " aws_secret_key
export AWS_SECRET_KEY=$aws_secret_key
echo
export S3_BUCKET="gingrsnap.dev"

echo "Starting postgres instance..."
pg_ctl -D /usr/local/var/postgres -l /usr/local/var/postgres/server.log start

echo "Setup complete!"
