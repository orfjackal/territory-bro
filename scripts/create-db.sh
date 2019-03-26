#!/bin/bash
set -eu

echo -n "New account username: "
read username
if [[ ! $username =~ ^[a-z0-9]+$ ]]; then
    echo "ERROR: Username must be lowercase alphanumeric"
    exit 1
fi

echo -n "New account password: "
read -s password
echo

if [[ "$password" == "" ]]; then
    echo "ERROR: Empty password"
    exit 1
fi
if [[ "$password" == "$username" ]]; then
    echo "ERROR: Same username and password"
    exit 1
fi

echo -n "Database hostname: "
read dbhost

echo "
CREATE USER $username WITH PASSWORD '$password';

-- because of RDS, see http://stackoverflow.com/a/34898033/62130
GRANT $username TO master;

CREATE DATABASE $username
    OWNER $username
    ENCODING 'UTF-8'
    LC_COLLATE 'fi_FI.UTF-8'
    LC_CTYPE 'fi_FI.UTF-8'
    TEMPLATE template0;

\connect $username;

CREATE SCHEMA $username AUTHORIZATION $username;

CREATE EXTENSION postgis;
" | psql -h $dbhost -U master territorybro

cat resources/migrations/*.up.sql | PGPASSWORD=$password psql -h $dbhost -U $username $username

# Bash 4 would have a shortcut, but macOS has Bash 3
# https://stackoverflow.com/questions/11392189/how-to-convert-a-string-from-uppercase-to-lowercase-in-bash
username_upper=$(echo "$username" | tr '[:lower:]' '[:upper:]')
echo "
Compose config:

        TENANT__${username_upper}__DATABASE_URL: jdbc:postgresql://${dbhost}/${username}?user=${username}&password=${password}
        TENANT__${username_upper}__DATABASE_HOST: ${dbhost}
        TENANT__${username_upper}__DATABASE_USERNAME: ${username}
        TENANT__${username_upper}__DATABASE_PASSWORD: ${password}
        TENANT__${username_upper}__ADMINS: 
"
