#!/bin/bash
set -e

echo "Creating application user and database..."

mongosh admin -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --eval "
  db = db.getSiblingDB('$APP_DB_NAME');
  
  if (!db.getUser('$APP_DB_USER')) {
    db.createUser({
      user: '$APP_DB_USER',
      pwd: '$APP_DB_PASS',
      roles: [{ role: 'readWrite', db: '$APP_DB_NAME' }]
    });
    print('User $APP_DB_USER created successfully.');
  } else {
    print('User $APP_DB_USER already exists.');
  }
"