# PAS Backfill

The `pas-backfill` commands submit a backfill job YAML file to the PAS and read the generated report. Both commands
require a PAS access token. Pass it with `--access-token` or pipe it to stdin. Piping is recommended because it avoids
putting the token into the shell history.

> **Local development note:** The CLI runs inside a Docker container, so `localhost` refers to the container, not your
> host machine. To reach a service running locally on Linux, use the Docker bridge gateway IP `172.17.0.1` instead:
>
> ```bash
> echo "$PAS_ACCESS_TOKEN" | ./jeap pas-backfill send \
>   --file=backfill-job.yaml \
>   --job-id=88dbb65f-9634-4685-bc86-17b72d715d3e \
>   --url=http://172.17.0.1:8080/process-archive-service
> ```
>
> On macOS/Windows you can use `host.docker.internal` instead.

## Submit a Backfill Job

```bash
echo "$PAS_ACCESS_TOKEN" | ./jeap pas-backfill send \
  --file=backfill-job.yaml \
  --job-id=88dbb65f-9634-4685-bc86-17b72d715d3e \
  --url=https://pas.example.com/process-archive-service
```

Arguments:

| Argument         | Required | Description                                                                 |
|------------------|----------|-----------------------------------------------------------------------------|
| `--file`         | yes      | Path to the input backfill job YAML file, for example `backfill-job.yaml`.   |
| `--url`          | yes      | Base URL of the Process Archive Service including its servlet context path, for example `https://pas.example.com/process-archive-service`. |
| `--job-id`       | no       | Unique job UUID. If omitted, the CLI generates a random UUID.                |
| `--access-token` | no       | PAS access token. If omitted, the token is read from stdin.                  |

The command sends the input file as `application/yaml` to the PAS job endpoint. See
[Job YAML format](#job-yaml-format) below for the YAML structure, endpoint behavior, and possible
responses. On success, it prints the created job id status message to stdout.

## Read a Backfill Report

```bash
echo "$PAS_ACCESS_TOKEN" | ./jeap pas-backfill report \
  --job-id=88dbb65f-9634-4685-bc86-17b72d715d3e \
  --url=https://pas.example.com/process-archive-service \
  --output=backfill-report.yaml
```

Arguments:

| Argument         | Required | Description                                                   |
|------------------|----------|---------------------------------------------------------------|
| `--job-id`       | yes      | UUID of the backfill job to read the report for.              |
| `--url`          | yes      | Base URL of the Process Archive Service including its servlet context path, for example `https://pas.example.com/process-archive-service`. |
| `--output`       | no       | Output file path. If omitted, the report is written to stdout. |
| `--access-token` | no       | PAS access token. If omitted, the token is read from stdin.    |

The command reads the report as YAML. Use `--output backfill-report.yaml` to write it to a file, or omit `--output` to
print the report to stdout.

## Job YAML Format

The `pas-backfill send` command submits a YAML file to the PAS job endpoint.

```http
PUT /api/jobs/{jobId}
Content-Type: application/yaml
```

`jobId` is a UUID chosen by the caller. Reusing the same `jobId` with identical content is idempotent and returns
`200 OK`. Reusing the same `jobId` with different content returns `409 Conflict`.

Required role: `backfilljob:write`.

### Unversioned Data Reader Endpoint

Use this shape when the configured data reader endpoint does not contain `{version}`, for example:
`https://source-service.example/api/archive-data/{id}`.

```yaml
message: JmeDecreeDocumentCreatedEvent
topic: jme-process-archive-decreedocumentcreated
archiveDataReferences:
  - id: DOC-2024-001
  - id: DOC-2024-002
```

### Versioned Data Reader Endpoint

Use this shape when the configured data reader endpoint contains `{version}`, for example:
`https://source-service.example/api/archive-data/{id}?version={version}`. In this case every reference must include
`version`.

```yaml
message: JmeDecreeDocumentCreatedEvent
topic: jme-process-archive-decreedocumentcreated
archiveDataReferences:
  - id: DOC-2024-001
    version: 1
  - id: DOC-2024-002
    version: 1
```

### Fields

| Field | Required | Description |
| --- | --- | --- |
| `message` | yes | Name of the configured message/archive type. |
| `topic` | yes | Topic configured for the message/archive type. |
| `archiveDataReferences` | yes | Non-empty list of archive data references to process. |
| `archiveDataReferences[].id` | yes | Business reference id understood by the source service. |
| `archiveDataReferences[].version` | no | Business reference version understood by the source service. Required only when the configured data reader endpoint contains `{version}`. |

### Responses

| Status | Meaning |
| --- | --- |
| `200 OK` | Job accepted, or the same job was already submitted with identical content. |
| `400 Bad Request` | Invalid YAML/request content, unknown archive configuration, topic mismatch, or unsupported non-remote archive configuration. |
| `403 Forbidden` | Caller does not have `backfilljob:write`. |
| `409 Conflict` | A job with the same `jobId` already exists with different content. |
