import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from contextlib import contextmanager
from pathlib import Path

import boto3
import psycopg2
from botocore.config import Config
from psycopg2 import sql


WORK = Path('/work')
SNAPSHOT = WORK / 'snapshot'
PHASE = 'initialization'


def run(command):
    return subprocess.run(command, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


def restic(*arguments, read_only=False):
    prefix = ['restic', '--no-lock'] if read_only else ['restic']
    return run([*prefix, *arguments])


@contextmanager
def connection():
    database = psycopg2.connect(connect_timeout=10)
    try:
        database.set_session(isolation_level='REPEATABLE READ', readonly=True)
        with database:
            with database.cursor() as cursor:
                cursor.execute("SET TIME ZONE 'UTC'")
            yield database
    finally:
        database.close()


def database_state(database):
    with database.cursor() as cursor:
        cursor.execute("""
            SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') ORDER BY c.relname
        """)
        tables = [row[0] for row in cursor.fetchall()]
        cursor.execute("""
            SELECT sequencename, last_value, increment_by, min_value, max_value, start_value, cycle
            FROM pg_sequences WHERE schemaname = 'public' ORDER BY sequencename
        """)
        sequences = [list(row) for row in cursor.fetchall()]

    fingerprints = {}
    for table in tables:
        digest = hashlib.sha256()
        count = 0
        with database.cursor(name='backup_rows') as cursor:
            cursor.itersize = 1000
            cursor.execute(sql.SQL('SELECT to_jsonb(t)::text FROM public.{} t '
                                   'ORDER BY to_jsonb(t)::text COLLATE "C"').format(sql.Identifier(table)))
            for (row,) in cursor:
                content = row.encode('utf-8')
                digest.update(len(content).to_bytes(8, 'big'))
                digest.update(content)
                count += 1
        fingerprints[table] = {'rows': count, 'sha256': digest.hexdigest()}
    return {'tables': fingerprints, 'sequences': sequences}


def s3_client():
    return boto3.client('s3', endpoint_url=os.environ['S3_ENDPOINT'],
                        region_name=os.environ['S3_REGION'],
                        aws_access_key_id=os.environ['S3_ACCESS_KEY'],
                        aws_secret_access_key=os.environ['S3_SECRET_KEY'],
                        config=Config(connect_timeout=10, read_timeout=60,
                                      retries={'max_attempts': 3, 'mode': 'standard'},
                                      s3={'addressing_style': 'path'}))


def inventory(client, bucket):
    objects = {}
    for page in client.get_paginator('list_objects_v2').paginate(Bucket=bucket):
        for item in page.get('Contents', []):
            objects[item['Key']] = {'size': item['Size'], 'etag': item['ETag']}
    return objects


def object_filename(key):
    return hashlib.sha256(key.encode('utf-8')).hexdigest()


def copy_and_hash(source, target=None):
    digest = hashlib.sha256()
    count = 0
    while content := source.read(1024 * 1024):
        digest.update(content)
        count += len(content)
        if target is not None:
            target.write(content)
    return count, digest.hexdigest()


def verify_references(database, objects):
    with database.cursor() as cursor:
        cursor.execute('SELECT cover_object_key FROM public.materials WHERE cover_object_key IS NOT NULL')
        if any(row[0] not in objects for row in cursor):
            raise ValueError('A referenced cover is missing')


def backup():
    global PHASE
    PHASE = 'database snapshot'
    SNAPSHOT.mkdir(mode=0o700)
    (SNAPSHOT / 'objects').mkdir(mode=0o700)
    with connection() as database:
        with database.cursor() as cursor:
            cursor.execute('SELECT pg_export_snapshot()')
            snapshot_id = cursor.fetchone()[0]
        run(['pg_dump', '--format=custom', '--no-owner', '--no-acl',
             '--snapshot=' + snapshot_id, '--file=' + str(SNAPSHOT / 'database.dump')])
        state = database_state(database)
        if 'flyway_schema_history' not in state['tables']:
            raise ValueError('Flyway schema is required')
        PHASE = 'S3 snapshot'
        client = s3_client()
        bucket = os.environ['S3_COVERS_BUCKET']
        before = inventory(client, bucket)
        verify_references(database, before)
        objects = []
        for key, expected in sorted(before.items()):
            response = client.get_object(Bucket=bucket, Key=key)
            if response['ETag'] != expected['etag']:
                raise ValueError('An object changed during backup')
            with response['Body'] as source, (SNAPSHOT / 'objects' / object_filename(key)).open('xb') as target:
                size, digest = copy_and_hash(source, target)
            if size != expected['size']:
                raise ValueError('Incomplete S3 download')
            metadata = {field: response[field] for field in
                        ('ContentType', 'CacheControl', 'ContentEncoding', 'ContentDisposition', 'Metadata')
                        if field in response}
            objects.append({'key': key, 'size': size, 'sha256': digest, 'metadata': metadata})
        if inventory(client, bucket) != before:
            raise ValueError('S3 changed during backup')
    manifest = {'format': 1, 'source': os.environ['BACKUP_SOURCE_ID'],
                'created_at': datetime.now(timezone.utc).isoformat(), 'database': state, 'objects': objects}
    (SNAPSHOT / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False), encoding='utf-8')
    PHASE = 'encrypted repository write'
    output = restic('backup', '--json', '--host', 'library-service', '--tag', 'library-service',
                    '--tag', 'source:' + os.environ['BACKUP_SOURCE_ID'], str(SNAPSHOT))
    summaries = [json.loads(line) for line in output.splitlines()]
    summary = next(item for item in summaries if item.get('message_type') == 'summary')
    print('Backup created: ' + summary['snapshot_id'], flush=True)


def restore_check(snapshot):
    global PHASE
    if os.environ['PGHOST'] != 'restore-db' or os.environ['S3_ENDPOINT'] != 'http://restore-s3:9000':
        raise ValueError('Restore check requires isolated targets')
    PHASE = 'repository integrity check'
    restic('check', '--read-data', read_only=True)
    PHASE = 'snapshot selection'
    selector = [] if snapshot == 'latest' else [snapshot]
    snapshots = json.loads(restic('snapshots', '--json', '--host', 'library-service',
                                 '--tag', 'source:' + os.environ['BACKUP_SOURCE_ID'],
                                 '--path', str(SNAPSHOT), '--latest', '1', *selector, read_only=True))
    if len(snapshots) != 1:
        raise ValueError('Expected one matching snapshot')
    snapshot_id = snapshots[0]['id']
    PHASE = 'snapshot restore'
    destination = WORK / 'restore'
    restic('restore', snapshot_id, '--target', str(destination), '--verify', read_only=True)
    restored = destination / 'work' / 'snapshot'
    manifest = json.loads((restored / 'manifest.json').read_text(encoding='utf-8'))
    if manifest['format'] != 1 or manifest['source'] != os.environ['BACKUP_SOURCE_ID']:
        raise ValueError('Unsupported snapshot manifest')
    PHASE = 'empty target validation'
    with connection() as database:
        if database_state(database)['tables']:
            raise ValueError('Restore database must be empty')
    client = s3_client()
    if client.list_buckets()['Buckets']:
        raise ValueError('Restore S3 must be empty')
    PHASE = 'database restore'
    run(['pg_restore', '--exit-on-error', '--single-transaction', '--no-owner', '--no-acl',
         '--dbname=' + os.environ['PGDATABASE'], str(restored / 'database.dump')])
    PHASE = 'database verification'
    with connection() as database:
        if database_state(database) != manifest['database']:
            raise ValueError('Restored database differs from the snapshot')
    PHASE = 'S3 restore and verification'
    bucket = os.environ['S3_COVERS_BUCKET']
    client.create_bucket(Bucket=bucket)
    for item in manifest['objects']:
        filename = restored / 'objects' / object_filename(item['key'])
        if filename.is_symlink():
            raise ValueError('Object file must not be a symbolic link')
        with filename.open('rb') as content:
            if copy_and_hash(content) != (item['size'], item['sha256']):
                raise ValueError('Object checksum mismatch')
            content.seek(0)
            client.put_object(Bucket=bucket, Key=item['key'], Body=content, **item['metadata'])
        response = client.get_object(Bucket=bucket, Key=item['key'])
        with response['Body'] as content:
            if copy_and_hash(content) != (item['size'], item['sha256']):
                raise ValueError('Restored object checksum mismatch')
        if any(response.get(field) != value for field, value in item['metadata'].items()):
            raise ValueError('Restored object metadata mismatch')
    actual = inventory(client, bucket)
    if set(actual) != {item['key'] for item in manifest['objects']}:
        raise ValueError('Restored S3 inventory mismatch')
    with connection() as database:
        verify_references(database, actual)
    print(f'Restore verified: {snapshot_id}; tables={len(manifest["database"]["tables"])}; '
          f'objects={len(actual)}', flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['init', 'backup', 'check', 'snapshots', 'restore-check'])
    parser.add_argument('--snapshot', default='latest')
    arguments = parser.parse_args()
    if not re.fullmatch(r'latest|[a-fA-F0-9]{8,64}', arguments.snapshot):
        raise ValueError('Invalid snapshot ID')
    os.umask(0o077)
    if arguments.command == 'init':
        restic('init')
        print('Encrypted backup repository initialized.', flush=True)
    elif arguments.command == 'backup':
        backup()
    elif arguments.command == 'restore-check':
        restore_check(arguments.snapshot)
    elif arguments.command == 'check':
        restic('check', '--read-data')
        print('Backup repository integrity verified.', flush=True)
    else:
        snapshots = json.loads(restic('snapshots', '--json', '--host', 'library-service',
                                     '--tag', 'source:' + os.environ['BACKUP_SOURCE_ID']))
        for snapshot in snapshots:
            print(snapshot['id'] + ' ' + snapshot['time'], flush=True)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'Backup operation failed during {PHASE}; error_type={type(error).__name__}', file=sys.stderr)
        sys.exit(1)
