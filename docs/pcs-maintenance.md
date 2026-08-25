# PCS Maintenance Jobs

The `pcs` commands submit asynchronous maintenance jobs to a Process Context Service (PCS) and retrieve their YAML
reports. Submission requires `processcontextjob:write`; report retrieval requires `processcontextjob:read`.

Pass the PCS OAuth access token with `--access-token` or pipe it to stdin. Piping is recommended because it keeps the
token out of shell history and process arguments:

```bash
echo "$PCS_ACCESS_TOKEN" | ./jeap pcs reevaluate-relations \
  --file=reevaluation-job.yaml \
  --processes-csv=processes.csv \
  --url=https://pcs.example.com/process-context
```

Each submission prints its job UUID to stdout. When `--job-id` is omitted, the CLI generates a UUID. Store that UUID
to retrieve the report. Supply a stable explicit UUID when a failed or uncertain HTTP request may need to be retried;
PCS treats an identical request under the same UUID as an idempotent retry.

Diagnostics are written to stderr. Reports are written only to stdout or the requested output file, so stdout can be
piped safely.

PCS URLs must use HTTPS. For a local PCS on a loopback address only, explicitly opt in to HTTP:

```bash
echo "$PCS_ACCESS_TOKEN" | ./jeap pcs report \
  --job-type=backfill \
  --job-id=88dbb65f-9634-4685-bc86-17b72d715d3e \
  --url=http://localhost:8080/process-context \
  --allow-insecure-http=true
```

The opt-in never permits HTTP for a remote host. HTTP connections time out after 10 seconds and responses after 60
seconds, so an unavailable or stalled PCS does not block the CLI indefinitely.

## Reevaluate Relations

```bash
echo "$PCS_ACCESS_TOKEN" | ./jeap pcs reevaluate-relations \
  --file=reevaluation-job.yaml \
  --url=https://pcs.example.com/process-context \
  --job-id=88dbb65f-9634-4685-bc86-17b72d715d3e
```

Complete canonical YAML (field names follow PCS 27.0.2 exactly):

```yaml
process-template-name: assessmentProcess
processes:
  - origin-process-id: assessment-4711
  - origin-process-id: assessment-4712
```

For large lists, omit `processes` from YAML and use `--processes-csv`:

```csv
originProcessId
assessment-4711
assessment-4712
```

The request is sent with `PUT /api/reevaluation-jobs/{jobId}`.

## Backfill Process Data

```bash
echo "$PCS_ACCESS_TOKEN" | ./jeap pcs backfill \
  --file=backfill-job.yaml \
  --process-data-csv=process-data.csv \
  --url=https://pcs.example.com/process-context
```

Complete YAML:

```yaml
process-template-name: assessmentProcess
entries:
  - origin-process-id: assessment-4711
    process-data:
      - key: assessmentArtefactId
        value: art-456
        role: FinalVersion
      - key: assessmentId
        value: a-123
  - origin-process-id: assessment-4712
    process-data:
      - key: assessmentId
        value: a-789
```

For CSV input, YAML contains only `process-template-name`:

```csv
originProcessId,key,value,role
assessment-4711,assessmentId,a-123,
assessment-4711,assessmentArtefactId,art-456,FinalVersion
assessment-4712,assessmentId,a-789,
```

CSV rows are grouped by `originProcessId`. A blank role becomes an omitted YAML role. The request is sent with
`PUT /api/backfill-jobs/{jobId}`.

## Republish Relations

Supply either `--file` or `--relations-csv`; no metadata YAML is needed for CSV mode:

```bash
echo "$PCS_ACCESS_TOKEN" | ./jeap pcs notify-relations \
  --relations-csv=relations.csv \
  --url=https://pcs.example.com/process-context
```

Complete YAML:

```yaml
relationIds:
  - 019c8c72-6fd1-7f25-a9a1-3b3d51fbb321
  - 019c8c72-7b42-7a04-9443-bf8ec98ce871
```

CSV:

```csv
relationId
019c8c72-6fd1-7f25-a9a1-3b3d51fbb321
019c8c72-7b42-7a04-9443-bf8ec98ce871
```

The request is sent with `PUT /api/relation-publication-jobs/{jobId}`. Relation UUID selection remains an operator
responsibility; the CLI does not query the PCS database or select relations.

## Retrieve Reports

Select the endpoint directly with `--job-type`:

```bash
echo "$PCS_ACCESS_TOKEN" | ./jeap pcs report \
  --job-type=backfill \
  --job-id=88dbb65f-9634-4685-bc86-17b72d715d3e \
  --url=https://pcs.example.com/process-context \
  --output=backfill-report.yaml
```

Accepted types and endpoints:

| Job type | Endpoint |
| --- | --- |
| `reevaluation` | `GET /api/reevaluation-jobs/{jobId}` |
| `backfill` | `GET /api/backfill-jobs/{jobId}` |
| `relation-publication` | `GET /api/relation-publication-jobs/{jobId}` |

Without `--output`, the response is emitted unchanged to stdout. Reports describe PCS task processing. Successful
relation republication confirms listener handoff, not downstream consumption.

## CSV and Validation

CSV files are UTF-8 and may start with a BOM. The parser supports RFC-compatible quoting, including commas, embedded
newlines and CRLF records, and ignores blank lines and lines beginning with `#`. Headers must match exactly. Required
fields must not be blank. Duplicate normalized IDs or process-data values are rejected. Complete YAML and the
corresponding CSV option are mutually exclusive.

The CLI validates all inputs before contacting PCS and emits deterministic canonical YAML. The fixed limits match PCS
27.0.2:

| Limit | Value |
| --- | ---: |
| Tasks per job | 10000 |
| UTF-8 bytes per string field | 2000 |
| Process-data values per task | 100 |
| Process-data values per job | 10000 |
| Canonical request bytes | 10485760 (10 MiB) |

## Responses and Errors

Requests and reports use `application/yaml`. HTTP `400` reports PCS validation details, `403` identifies the required
semantic role, `404` identifies the missing job, and `409` explains that a job UUID already exists with different
content. Invalid local input and connectivity failures also return a non-zero exit status. If submission with an
automatically generated UUID fails, the error on stderr includes that UUID for a safe retry; stdout contains only the
UUID of successful submissions. Access tokens are never written into canonical requests, reports, errors, or verbose
launcher output.
