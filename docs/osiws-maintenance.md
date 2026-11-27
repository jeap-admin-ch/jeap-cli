# OpenSearch Index Writer Maintenance Jobs

The `osiws` commands submit maintenance jobs to the OpenSearch index writer service and export their reports. An
upsert job writes the current data of the given ids to the index again, a delete job removes the given ids from the
index. The index writer runs both in the background; maintenance must be enabled there
(`jeap.opensearch.indexwriter.maintenance.enabled=true`).

| Command        | Required role      | Endpoint                                                       |
|----------------|--------------------|----------------------------------------------------------------|
| `osiws upsert` | `upsertjob#create` | `PUT /api/upsert-jobs/{jobId}`                                 |
| `osiws delete` | `deletejob#create` | `PUT /api/delete-jobs/{jobId}`                                 |
| `osiws report` | `jobreport#read`   | `GET /api/upsert-jobs/{jobId}`, `GET /api/delete-jobs/{jobId}` |

Pass the access token with `--access-token` or pipe it to stdin. Piping is recommended because it keeps the token out
of shell history and process arguments:

```bash
echo "$OSIWS_ACCESS_TOKEN" | jeap osiws upsert \
  --file=upsert-job.yaml \
  --csv=ids.csv \
  --url=https://index-writer.example.com
```

Each successful submission prints its job UUID to stdout. Without `--job-id`, the CLI generates one. Before sending
the request, the CLI writes the UUID to stderr, so it is known even if the request fails or times out:

```text
Submitting upsert job 88dbb65f-9634-4685-bc86-17b72d715d3e with 2 references to https://index-writer.example.com.
Upsert job 88dbb65f-9634-4685-bc86-17b72d715d3e accepted with 2 references: schema major 1 (explicit) in jme_decree_document_v1-000001, job-state open.
```

If the outcome is uncertain, export the report or submit the same input again with `--job-id=<uuid>`: the index writer
returns the stored job for identical input under the same UUID and answers `409` if the content differs. For a
deliberate rerun, submit a new job without `--job-id`.

Index writer URLs must use HTTPS. For a local index writer on a loopback address only, explicitly opt in to HTTP with
`--allow-insecure-http=true`. HTTP connections time out after 10 seconds and responses after 60 seconds.

## Upsert Jobs

The YAML file names the `UPSERT` operation of the index writer's message configuration (`messages.json`) to use:

```yaml
message:
  messageName: JmeDecreeDocumentCreatedEvent
  topicName: jme-decree-document-created
operation:
  indexType: DecreeDocument
  indexMajorVersion: 1
```

`operation.indexMajorVersion` selects the schema major of the job. If it is omitted, the index writer selects the
highest configured major; the CLI never chooses a major. `operation.indexOperation` may be omitted, the CLI sends
`UPSERT`.

The ids are read from a CSV file with `--csv`. The `version` column is optional business metadata: the index writer
stores and reports it, it never selects a schema major. A blank version is omitted, the CSV may also have only the
`id` column.

```csv
id,version
12345,17
67890,
```

Instead of a CSV file, the YAML file may list the ids in `originReferences`. The CLI sends:

```yaml
message:
  messageName: JmeDecreeDocumentCreatedEvent
  topicName: jme-decree-document-created
operation:
  indexType: DecreeDocument
  indexOperation: UPSERT
  indexMajorVersion: 1
originReferences:
  - id: "12345"
    version: "17"
  - id: "67890"
```

## Delete Jobs

```bash
echo "$OSIWS_ACCESS_TOKEN" | jeap osiws delete \
  --file=delete-job.yaml \
  --csv=ids.csv \
  --url=https://index-writer.example.com
```

```yaml
indexType: DecreeDocument
indexMajorVersion: 1
```

With `indexMajorVersion`, the ids are deleted from that schema major only. Without it, they are deleted from every
major configured for the index type when the job is accepted. Ids that are already absent count as deleted.

Delete jobs have ids only: the CSV file has the single column `id`, and a `version` is rejected. The CLI sends:

```yaml
indexType: DecreeDocument
indexMajorVersion: 1
originReferences:
  - id: "12345"
  - id: "67890"
```

## Export Reports

```bash
echo "$OSIWS_ACCESS_TOKEN" | jeap osiws report \
  --job-type=upsert \
  --job-id=88dbb65f-9634-4685-bc86-17b72d715d3e \
  --url=https://index-writer.example.com > report.yaml
```

`--job-type` is `upsert` or `delete`. Use `--output=report.yaml` instead of redirecting stdout to write the file
directly.

The index writer returns a report in pages. The CLI reads every page and writes one YAML document: the first page with
the references of all pages and without the `page` block. `--page-size` sets the number of references per request; by
default, the index writer's `default-page-size` (100) applies, at most its `max-page-size` (1000) is allowed. If a page
cannot be read or the pages do not fit together, the CLI writes nothing and exits with a non-zero status.

While a job is still running, the pages are read one after the other, so later references can show a newer state than
the summary at the top. Export the report again once `job-state` is `completed` for a final result.

## Input and Validation

The CLI validates the whole input before contacting the index writer:

- Files are UTF-8 and may start with a BOM. CSV files must use the exact header, support RFC-compatible quoting, and
  may contain blank lines and lines starting with `#`, which are ignored. The order of the ids is kept.
- Ids and versions are text: leading zeros are kept, surrounding whitespace is removed. In YAML, unquoted integers such
  as `12345` or `007` are accepted; values that YAML would not keep exactly as written, such as `1.50` or `true`, must
  be quoted.
- Each id may appear only once per job, also with different versions: both entries would write the same document.
- Ids and versions have at most 512 characters and no control characters. `indexMajorVersion` is an unquoted positive
  integer.
- `originReferences` come either from the YAML file or from `--csv`, not both. Unknown fields are rejected, so are
  rollback controls such as `allowRollback`: maintenance jobs never compare versions.

The limits configured in the index writer (`max-references`, `max-request-bytes`) are checked by the index writer.

## Responses and Errors

| HTTP status | Meaning                                                                                                     |
|-------------|-------------------------------------------------------------------------------------------------------------|
| `400`       | The index writer rejected the input, for example an unknown schema major or an exceeded limit.              |
| `401`       | The access token is invalid or expired.                                                                     |
| `403`       | The access token lacks the required role.                                                                   |
| `404`       | Unknown job (check `--job-id` and `--job-type`), or no job API: wrong URL or maintenance not enabled.       |
| `409`       | The job UUID exists with different content.                                                                 |
| `422`       | `NOT_ALLOWED` (for example a rollover policy) or `TARGET_INVALID`: nothing was stored.                      |
| `503`       | The job storage is temporarily unavailable. Retry later with the same `--job-id`.                           |

The CLI shows the error code and message of the index writer and does not retry, nor does it switch to another schema
major. Diagnostics are written to stderr; stdout contains only the job UUID or the report, so it can be piped safely.
Access tokens are never written into request bodies, reports or errors.
