#!/bin/bash
set -e

if [ ! -f /app/application-dev.yml.template ]; then
    cp /app/application-dev.yml /app/application-dev.yml.template
fi

# 用环境变量渲染模板到配置文件
envsubst < /app/application-dev.yml.template > /app/application-dev.yml

exec java -Xmx2g -jar app.jar --spring.config.location=/app/application-dev.yml
