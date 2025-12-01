# Dev Environment Setup

## Install and start MongoDB

    cd ./ci-cd/local/mongo
    docker-compose up

## Local connection to MongoDB

    mongosh --username dev_user --password dev_password --authenticationDatabase receipt_db
    mongosh --username admin --password admin_password --authenticationDatabase admin

## Start app server in dev mode (with auto-reload on source changes)

    sbt ~reStart