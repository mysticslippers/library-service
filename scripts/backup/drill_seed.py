import os
import sys
import uuid

import psycopg2

sys.path.insert(0, '/opt')
from backup_tool import s3_client


VERSION = uuid.UUID('467bf0ac-b3f7-4a09-9255-091673257a13')
KEY = f'materials/1/covers/{VERSION}.jpg'


def main():
    client = s3_client()
    bucket = os.environ['S3_COVERS_BUCKET']
    if len(sys.argv) > 1 and sys.argv[1] == 'remove-cover':
        client.delete_object(Bucket=bucket, Key=KEY)
        return
    with psycopg2.connect() as database:
        with database.cursor() as cursor:
            cursor.execute("""
                INSERT INTO users(email, phone, password_hash, first_name, last_name, status)
                VALUES ('backup-drill@example.invalid', '+79990000001',
                        'synthetic-password-hash-marker', 'Restore', 'Drill', 'ACTIVE');
                INSERT INTO libraries(code, name) VALUES ('DRILL', 'Backup drill');
                INSERT INTO materials(title, cover_object_key, cover_version)
                VALUES ('Книга для проверки восстановления', %s, %s);
                INSERT INTO fines(user_id, reason, amount) VALUES (1, 'OTHER', 17.50);
                SELECT nextval('users_id_seq');
            """, (KEY, str(VERSION)))
    client.create_bucket(Bucket=bucket)
    client.put_object(Bucket=bucket, Key=KEY, Body=b'\xff\xd8\xffbackup-drill-image',
                      ContentType='image/jpeg', CacheControl='public, max-age=31536000, immutable')
    client.put_object(Bucket=bucket, Key='nested/with spaces/обложка.bin', Body=b'opaque-key-object',
                      ContentType='application/octet-stream', Metadata={'purpose': 'restore-drill'})
    print('Synthetic database and S3 fixtures created.', flush=True)


if __name__ == '__main__':
    main()
