#!/bin/bash
exec /data/jdk/jdk-17.0.15/bin/java \
  -Xms512m -Xmx1536m -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /opt/paper/backend/ruoyi-admin.jar \
  --spring.profiles.active=prod \
  --server.port=6039 \
  --spring.datasource.dynamic.datasource.master.url='jdbc:mysql://127.0.0.1:3307/ai_sc?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=GMT%2B8&autoReconnect=true&rewriteBatchedStatements=true&allowPublicKeyRetrieval=true' \
  --spring.datasource.dynamic.datasource.master.username=root \
  --spring.datasource.dynamic.datasource.master.password='123QWER.' \
  --spring.data.redis.host=127.0.0.1 \
  --spring.data.redis.port=6379 \
  --spring.data.redis.password='scs@pwd' \
  --spring.data.redis.database=3 \
  --sys.upload.path=/opt/paper/upload \
  --justauth.address=https://paper.xunmaw.com
