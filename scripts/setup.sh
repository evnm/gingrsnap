#!/bin/sh
#
# In order to properly export DATABASE_URL into your shell's env, source this
# script, as opposed to executing it directly: `. ./scripts/setup.sh`.

echo "Setting DATABASE_URL..."
read -p "db username? " db_username
read -s -p "db password? " db_pwd
export DATABASE_URL=postgres://$db_username:$db_pwd@localhost/postgres
echo

echo "Setting Google Apps credentials..."
read -p "Google Apps username? " google_username
export GOOGLE_USERNAME=$google_username
read -s -p "Google Apps password? " google_password
export GOOGLE_PASSWORD=$google_password

echo "Setting AWS credentials..."
read -s -p "AWS access key? " aws_access_key
export AWS_ACCESS_KEY=$aws_access_key
echo
read -s -p "AWS secret key? " aws_secret_key
export AWS_SECRET_KEY=$aws_secret_key
echo
export S3_BUCKET="gingrsnap.dev"

echo "Setup complete!"
