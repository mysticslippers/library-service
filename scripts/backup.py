import argparse
import os
import re
import secrets
import subprocess
import sys
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


def run(command, environment, capture=False):
    result = subprocess.run(command, cwd=ROOT, env=environment, text=True,
                            stdout=subprocess.PIPE if capture else None, check=True)
    return result.stdout if capture else ''


def compose(arguments, restore_project=None, repository_only=False):
    prefix = ['docker', 'compose', '--project-directory', str(ROOT)]
    if restore_project is not None:
        return [*prefix, '-p', restore_project, '-f', str(ROOT / 'scripts/backup/compose.restore.yml')]
    if repository_only:
        return [*prefix, '-p', 'library-backup-repository-' + arguments.project,
                '-f', str(ROOT / 'scripts/backup/compose.repository.yml')]
    if arguments.env_file:
        prefix.extend(['--env-file', str(Path(arguments.env_file).resolve())])
    return [*prefix, '-p', arguments.project, '-f', str(Path(arguments.compose_file).resolve()),
            '-f', str(ROOT / 'docker-compose.backup.yml')]


def prepare(arguments):
    if not re.fullmatch(r'latest|[a-fA-F0-9]{8,64}', arguments.snapshot):
        raise ValueError('Use latest or a hexadecimal snapshot ID')
    if not re.fullmatch(r'[a-z0-9][a-z0-9_-]*', arguments.project):
        raise ValueError('Use a valid Compose project name')
    repository = Path(arguments.repository).resolve()
    password = Path(arguments.password_file).resolve()
    if arguments.command == 'init':
        repository.mkdir(mode=0o700, parents=True, exist_ok=True)
        existing = any(repository.iterdir())
        if existing and not (repository / 'config').is_file():
            raise ValueError('Repository is nonempty and has no Restic config; inspect it before initialization')
        if existing and not password.is_file():
            raise ValueError('Existing encrypted repository requires its original recovery password')
        password.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        if not password.exists():
            descriptor = os.open(password, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            with os.fdopen(descriptor, 'w', encoding='utf-8') as output:
                output.write(secrets.token_urlsafe(48))
    if not password.is_file() or not password.read_bytes().strip():
        raise ValueError('Backup password is missing or empty; initialize a new repository or supply its recovery key')
    if not repository.is_dir():
        raise ValueError('Backup repository directory is missing')
    environment = os.environ.copy()
    environment.update(BACKUP_DIRECTORY=str(repository), BACKUP_PASSWORD_FILE=str(password),
                       BACKUP_SOURCE_ID=arguments.project,
                       BACKUP_UID=str(os.getuid() if hasattr(os, 'getuid') else 0),
                       BACKUP_GID=str(os.getgid() if hasattr(os, 'getgid') else 0))
    return environment, repository


def restore_check(arguments, environment):
    project = 'library-restore-' + uuid.uuid4().hex
    environment.update(RESTORE_DB_PASSWORD=secrets.token_urlsafe(32),
                       RESTORE_S3_PASSWORD=secrets.token_urlsafe(32))
    command = compose(arguments, restore_project=project)
    try:
        run([*command, 'up', '-d', '--wait', '--wait-timeout', '120', 'restore-db', 'restore-s3'], environment)
        run([*command, 'run', '--rm', '--no-deps', 'verifier', 'restore-check',
             '--snapshot', arguments.snapshot], environment)
    finally:
        run([*command, 'down', '--volumes', '--remove-orphans'], environment)


def main():
    parser = argparse.ArgumentParser(description='Encrypted PostgreSQL/S3 backup and isolated restore verification')
    parser.add_argument('--compose-file', default=str(ROOT / 'docker-compose.prod.yml'))
    parser.add_argument('--env-file')
    parser.add_argument('--project', default='library-service-prod')
    parser.add_argument('--repository', default=str(ROOT / 'backups'))
    parser.add_argument('--password-file', default=str(ROOT / 'secrets/backup.password'))
    parser.add_argument('--snapshot', default='latest')
    parser.add_argument('command', choices=['init', 'backup', 'check', 'snapshots', 'restore-check'])
    arguments = parser.parse_args()
    environment, repository = prepare(arguments)
    if arguments.command == 'restore-check':
        restore_check(arguments, environment)
        return
    repository_only = arguments.command != 'backup'
    command = compose(arguments, repository_only=repository_only)
    if arguments.command == 'backup':
        active = run([*command, 'ps', '--status', 'running', '-q', 'backend'], environment, capture=True)
        if active.strip():
            raise ValueError('Stop all backend writers before backing up PostgreSQL and S3 together')
    operation = arguments.command
    if operation == 'init':
        run([*command, 'build', 'repository'], environment)
        if (repository / 'config').is_file():
            operation = 'snapshots'
            print('Existing repository and password preserved.', flush=True)
    service = 'repository' if repository_only else 'backup'
    run([*command, 'run', '--rm', '--no-deps', service, operation], environment)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        message = str(error) if isinstance(error, ValueError) else type(error).__name__
        print('Backup command failed: ' + message, file=sys.stderr)
        sys.exit(1)
