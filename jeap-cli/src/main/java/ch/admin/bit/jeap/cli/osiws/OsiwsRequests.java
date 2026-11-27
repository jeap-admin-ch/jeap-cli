package ch.admin.bit.jeap.cli.osiws;

/**
 * One origin reference of a job. The version is optional business metadata of an upsert reference; the index writer
 * only stores and reports it, it never selects a schema major.
 */
record OriginReference(String id, String version) {
}

/**
 * @param yaml           the canonical request body
 * @param referenceCount the number of origin references in the request
 */
record PreparedOsiwsRequest(String yaml, int referenceCount) {
}
