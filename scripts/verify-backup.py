import hashlib
import os
import secrets
import shutil
import subprocess
import sys
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


def run(command, environment, expected=0, capture=False):
    result = subprocess.run(command, cwd=ROOT, env=environment, text=True,
                            stdout=subprocess.PIPE if capture else None)
    if expected == 0 and result.returncode != 0:
        raise RuntimeError('Restore drill command failed')
    if expected != 0 and result.returncode == 0:
        raise RuntimeError('Unsafe backup operation unexpectedly succeeded')
    return result.stdout if capture else ''


def fingerprints(folder):
    return {str(path.relative_to(folder)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in folder.rglob('*') if path.is_file()}


def main():
    project = 'library-backup-drill-' + uuid.uuid4().hex
    scratch = ROOT / 'tmp' / project
    scratch.mkdir(parents=True, mode=0o700)
    repository = scratch / 'repository'
    password = scratch / 'password'
    environment = os.environ.copy()
    environment.update(POSTGRES_DB='drill', POSTGRES_USER='drill_admin',
                       POSTGRES_PASSWORD=secrets.token_urlsafe(32), S3_ACCESS_KEY='drill-admin',
                       S3_SECRET_KEY=secrets.token_urlsafe(32), S3_COVERS_BUCKET='drill-covers',
                       BACKUP_SOURCE_ID=project, BACKUP_DIRECTORY=str(repository),
                       BACKUP_PASSWORD_FILE=str(password))
    source = ['docker', 'compose', '--project-directory', str(ROOT), '-p', project,
              '-f', str(ROOT / 'scripts/backup/compose.drill.yml')]
    backup = [sys.executable, str(ROOT / 'scripts/backup.py'), '--compose-file',
              str(ROOT / 'scripts/backup/compose.drill.yml'), '--project', project,
              '--repository', str(repository), '--password-file', str(password)]
    tools = [*source, '-f', str(ROOT / 'docker-compose.backup.yml')]
    fixture = [*tools, 'run', '--rm', '--no-deps', '-v', str(ROOT / 'scripts/backup') + ':/fixtures:ro',
               '--entrypoint', 'python3', 'backup', '/fixtures/drill_seed.py']
    try:
        run([*backup, 'init'], environment)
        password_before = password.read_bytes()
        run([*backup, 'init'], environment)
        if password.read_bytes() != password_before:
            raise RuntimeError('Initialization replaced the existing recovery password')
        run([*source, 'up', '-d', '--wait', '--wait-timeout', '120', 'library-db', 'minio'], environment)
        port = run([*source, 'port', 'library-db', '5432'], environment, capture=True).strip().rsplit(':', 1)[1]
        environment['DRILL_JDBC_URL'] = 'jdbc:postgresql://127.0.0.1:' + port + '/drill'
        classpath = scratch / 'classpath.txt'
        maven = shutil.which('mvn') or shutil.which('mvn.cmd')
        java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
        if not maven or not java:
            raise RuntimeError('Maven and a JDK are required for the real Flyway drill')
        run([maven, '-B', '-ntp', '-f', str(ROOT / 'backend/pom.xml'), 'dependency:build-classpath',
             '-Dmdep.outputFile=' + str(classpath)], environment)
        run([java, '--class-path', classpath.read_text(encoding='utf-8').strip(),
             str(ROOT / 'scripts/backup/InitializeDrill.java'),
             str(ROOT / 'backend/src/main/resources/database/migration')], environment)
        run(fixture, environment)
        run([*source, '--profile', 'writer-guard', 'up', '-d', 'backend'], environment)
        run([*backup, 'backup'], environment, expected=1)
        run([*source, 'stop', 'backend'], environment)
        run([*backup, 'backup'], environment)
        before = fingerprints(repository)
        run([*backup, 'restore-check'], environment)
        if fingerprints(repository) != before:
            raise RuntimeError('Restore verification modified the encrypted repository')
        run([*fixture, 'remove-cover'], environment)
        run([*backup, 'backup'], environment, expected=1)
        if fingerprints(repository) != before:
            raise RuntimeError('Failed backup modified the encrypted repository')
        wrong_password = scratch / 'wrong-password'
        wrong_password.write_text(secrets.token_urlsafe(32), encoding='utf-8')
        wrong = backup.copy()
        wrong[wrong.index('--password-file') + 1] = str(wrong_password)
        run([*wrong, 'check'], environment, expected=1)
        print('Backup drill passed: encrypted snapshot, real restore, writer guard, missing-cover refusal, '
              'wrong-password refusal and initialization preservation.', flush=True)
    finally:
        run([*source, '--profile', 'writer-guard', 'down', '--volumes', '--remove-orphans'], environment)


if __name__ == '__main__':
    main()
