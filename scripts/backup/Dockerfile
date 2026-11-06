FROM restic/restic:0.19.1 AS restic
FROM postgres:16-alpine

RUN apk add --no-cache python3 py3-boto3 py3-psycopg2 \
    && mkdir /work && chmod 1777 /work
COPY --from=restic /usr/bin/restic /usr/local/bin/restic
COPY backup_tool.py /opt/backup_tool.py

ENV RESTIC_REPOSITORY=/repository \
    RESTIC_PASSWORD_FILE=/run/secrets/backup_password \
    RESTIC_CACHE_DIR=/work/cache \
    PYTHONDONTWRITEBYTECODE=1

ENTRYPOINT ["python3", "/opt/backup_tool.py"]
