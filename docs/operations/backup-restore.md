# PostgreSQL and cover backups

The backup contains one PostgreSQL database, all current objects in the private
cover bucket, and an encrypted verification manifest. It includes Flyway history,
audit, authentication records, outbox and consumer deduplication state. Credentials
from environment files and Docker secrets are not copied. Keep deployment
configuration and recovery credentials in a separate password/secret manager.

Restic encrypts the repository, including snapshot metadata. The password file
must be stored independently of the repository: losing it loses access to the
copies. See the [Restic repository format](https://restic.readthedocs.io/en/stable/100_references.html).

## Initialize

Run commands from the project root with Docker Compose v2 and Python 3.10 or newer:

```shell
python scripts/backup.py init
```

This builds the tools image, initializes `backups/`, and creates
`secrets/backup.password` without displaying the password. Both locations are
ignored by Git. Repeating initialization preserves an existing repository and
password. An existing repository without its original password is rejected.

On Linux the tool runs as the calling host user. New host directories use mode
0700 and the password uses 0600. On Windows protect the repository and password
directories with the account's NTFS permissions. Plaintext dump/restore files
exist only in private directories on temporary Docker volumes; Compose removes
those volumes when each one-off tool container is removed. The tools have no
Docker socket or host data-volume mounts.

For a separate backup disk or a repository copied from another machine, supply
the paths explicitly. Use the same source project ID when selecting its snapshots:

```shell
python scripts/backup.py --repository /srv/library-backups --password-file /secure/library-backup.password init
```

`init`, `check` and `snapshots` run without network access or production credentials.
The default Compose project/source ID is `library-service-prod`; use `--project`
if the application was deployed with a different Compose project name.

## Take a consistent backup

Stop every writer sharing the database/bucket, including backend replicas, IDE
instances, background workers and migrations. The wrapper rejects a running
`backend` service in the selected Compose project; it cannot detect writers in
other projects or on other machines.

```shell
docker compose --env-file .env.production -f docker-compose.prod.yml stop backend
python scripts/backup.py --env-file .env.production backup
docker compose --env-file .env.production -f docker-compose.prod.yml start backend
```

Restart the backend after either success or failure. The script does not stop or
start application services itself. This first deployment procedure requires a
maintenance window; it does not claim an online atomic snapshot across PostgreSQL
and S3.

The tool exports a repeatable-read database snapshot and passes it to
[`pg_dump`](https://www.postgresql.org/docs/16/app-pgdump.html). The dump,
deterministic hashes/counts of every public table, sequence state and cover
references use that database snapshot. Each S3 object is downloaded with its
metadata and SHA-256. The bucket listing must be unchanged after copying, and
every database cover reference must exist in it. Objects are stored under hashes
of their keys, so an S3 key is never used as a local filesystem path.

Only after these steps succeed is the snapshot published to Restic. A missing
referenced cover or incomplete object download fails the operation. The command
prints the completed snapshot ID, without contact data, password hashes or object
contents. Failed snapshots are not published.

```shell
python scripts/backup.py snapshots
python scripts/backup.py check
```

`check` runs `restic check --read-data`, which reads and verifies the repository
contents, not only its indexes. See [Restic integrity checks](https://restic.readthedocs.io/en/stable/045_working_with_repos.html#checking-integrity-and-consistency).
Copy the encrypted repository to independent storage after a successful snapshot.
Keep the password outside that copy. A repository on the database host alone does
not protect against loss of that host.

## Verify recovery

On a recovery machine, check out the project and build the tools image if needed:

```shell
docker build -t library-service-backup:local scripts/backup
python scripts/backup.py --repository /srv/library-backups --password-file /secure/library-backup.password restore-check
```

To verify a particular copy, put `--snapshot <hexadecimal-ID>` before `restore-check`.
The default is the latest snapshot for the selected source project.

The command creates a unique `library-restore-...` Compose project with a fresh
PostgreSQL 16 and MinIO. They have an internal network, no published ports and no
connection to production networks. The original repository is mounted read-only;
Restic uses `--no-lock`, so do not run repository pruning concurrently with a drill.

The verifier:

1. Checks every encrypted repository pack and restores the selected snapshot with
   Restic's file verification.
2. Requires empty PostgreSQL/S3 targets and restores the archive with
   [`pg_restore --single-transaction --exit-on-error`](https://www.postgresql.org/docs/16/app-pgrestore.html).
3. Compares all public-table row counts and content hashes, including Flyway and
   audit history, and compares sequence values/settings.
4. Uploads the objects, reads them back, compares bytes and metadata, and checks
   the complete inventory and every database cover reference.

It reports success only after all comparisons pass. The temporary containers,
their data volumes and decrypted staging are removed on exit. This is a recovery
drill, not an in-place production restore or a deployment cutover command.

## Production recovery and remaining setup

Restore to new, empty infrastructure during an outage; keep the old deployment
stopped until the recovery point and checks have been reviewed. Provision database
roles, S3 permissions, environment files, JWT/monitoring secrets and TLS separately.
The database archive intentionally excludes source role ownership and grants.

Kafka, Redis and monitoring data are outside this backup. Do not connect a restored
database immediately to an old Kafka backlog or restart email/SMS workers. Choose
the broker/consumer-offset recovery point first: replay can refer to changes made
after the database snapshot. With a fresh broker, published-but-unconsumed outbox
events need a reviewed replay plan using their original event IDs; restored
`processed_events` prevents redoing committed consumer work. Redis catalog caches
should be empty. Decide separately whether restored pending notifications should
be delivered again before enabling delivery jobs.

Before production, configure an independent/off-site destination, restricted backup
accounts, a maintenance schedule, retention and monitoring of backup failures.
The repository currently has no automatic `forget`/`prune` operation and no host
scheduler installed. Choose retention after capacity/RPO requirements are known;
test a retained historical snapshot before deleting older copies. Repeat the drill
after migration/tool changes and periodically on copies taken from the real server.

## Reproduce the synthetic drill

```shell
python scripts/verify-backup.py
```

This requires Maven and a JDK in addition to Python/Docker. It applies the actual
project migrations through the project's Flyway dependency, seeds synthetic
PostgreSQL/S3 data, checks writer/missing-cover/wrong-password refusals, verifies
that initialization preserves the key, and performs a real isolated restore.
It also asserts that recovery and a refused backup leave the encrypted repository
unchanged. Test data and keys stay under ignored `tmp/`; production services are
not accessed. The fixture PostgreSQL port is random and bound to localhost; only
the migration runner on the host uses it.

`.github/workflows/backup.yml` runs this drill when backup tooling, migration or
dependency files change. It uses generated test credentials and uploads no backup
repository, password file or restored data as CI artifacts.
