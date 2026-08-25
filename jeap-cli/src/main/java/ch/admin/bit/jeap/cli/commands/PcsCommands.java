package ch.admin.bit.jeap.cli.commands;

import ch.admin.bit.jeap.cli.pcs.PcsException;
import ch.admin.bit.jeap.cli.pcs.PcsJobType;
import ch.admin.bit.jeap.cli.pcs.PcsMaintenanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Component
public class PcsCommands {

    private static final String ERROR_MAPPER = "pcsExitStatusExceptionMapper";

    private final PcsMaintenanceService service;
    private final InputStream inputStream;

    @Autowired
    public PcsCommands(PcsMaintenanceService service) {
        this(service, System.in);
    }

    PcsCommands(PcsMaintenanceService service, InputStream inputStream) {
        this.service = service;
        this.inputStream = inputStream;
    }

    @Command(name = {"pcs", "reevaluate-relations"}, group = "PCS Maintenance",
            description = "Submit a relation reevaluation job (requires processcontextjob:write)",
            exitStatusExceptionMapper = ERROR_MAPPER)
    public void reevaluateRelations(
            @Option(longName = "file", required = true,
                    description = "YAML request or metadata file") String file,
            @Option(longName = "processes-csv",
                    description = "CSV file with originProcessId rows") String processesCsv,
            @Option(longName = "job-id",
                    description = "Job UUID for safe retry (default: generated)") String jobId,
            @Option(longName = "url", required = true,
                    description = "PCS base URL") String url,
            @Option(longName = "access-token",
                    description = "PCS access token (default: stdin)") String accessToken,
            @Option(longName = "allow-insecure-http", defaultValue = "false",
                    description = "Allow HTTP only for a loopback PCS URL") boolean allowInsecureHttp,
            CommandContext context) {
        validateInput(context, Set.of(
                "file", "processes-csv", "job-id", "url", "access-token", "allow-insecure-http"));
        ResolvedJobId resolvedJobId = resolveJobId(jobId);
        submit(resolvedJobId, () -> service.submitReevaluation(
                requiredPath(file, "--file"), optionalPath(processesCsv), resolvedJobId.value(), url,
                resolveAccessToken(accessToken), allowInsecureHttp));
        printJobId(context, resolvedJobId.value());
    }

    @Command(name = {"pcs", "backfill"}, group = "PCS Maintenance",
            description = "Submit a process-data backfill job (requires processcontextjob:write)",
            exitStatusExceptionMapper = ERROR_MAPPER)
    public void backfill(
            @Option(longName = "file", required = true,
                    description = "YAML request or metadata file") String file,
            @Option(longName = "process-data-csv",
                    description = "CSV file with originProcessId,key,value,role rows") String processDataCsv,
            @Option(longName = "job-id",
                    description = "Job UUID for safe retry (default: generated)") String jobId,
            @Option(longName = "url", required = true,
                    description = "PCS base URL") String url,
            @Option(longName = "access-token",
                    description = "PCS access token (default: stdin)") String accessToken,
            @Option(longName = "allow-insecure-http", defaultValue = "false",
                    description = "Allow HTTP only for a loopback PCS URL") boolean allowInsecureHttp,
            CommandContext context) {
        validateInput(context, Set.of(
                "file", "process-data-csv", "job-id", "url", "access-token", "allow-insecure-http"));
        ResolvedJobId resolvedJobId = resolveJobId(jobId);
        submit(resolvedJobId, () -> service.submitBackfill(
                requiredPath(file, "--file"), optionalPath(processDataCsv), resolvedJobId.value(), url,
                resolveAccessToken(accessToken), allowInsecureHttp));
        printJobId(context, resolvedJobId.value());
    }

    @Command(name = {"pcs", "notify-relations"}, group = "PCS Maintenance",
            description = "Submit a relation republication job (requires processcontextjob:write)",
            exitStatusExceptionMapper = ERROR_MAPPER)
    public void notifyRelations(
            @Option(longName = "file",
                    description = "Complete YAML request file") String file,
            @Option(longName = "relations-csv",
                    description = "CSV file with relationId rows") String relationsCsv,
            @Option(longName = "job-id",
                    description = "Job UUID for safe retry (default: generated)") String jobId,
            @Option(longName = "url", required = true,
                    description = "PCS base URL") String url,
            @Option(longName = "access-token",
                    description = "PCS access token (default: stdin)") String accessToken,
            @Option(longName = "allow-insecure-http", defaultValue = "false",
                    description = "Allow HTTP only for a loopback PCS URL") boolean allowInsecureHttp,
            CommandContext context) {
        validateInput(context, Set.of(
                "file", "relations-csv", "job-id", "url", "access-token", "allow-insecure-http"));
        ResolvedJobId resolvedJobId = resolveJobId(jobId);
        submit(resolvedJobId, () -> service.submitRelationPublication(
                optionalPath(file), optionalPath(relationsCsv), resolvedJobId.value(), url,
                resolveAccessToken(accessToken), allowInsecureHttp));
        printJobId(context, resolvedJobId.value());
    }

    @Command(name = {"pcs", "report"}, group = "PCS Maintenance",
            description = "Retrieve a PCS maintenance report (requires processcontextjob:read)",
            exitStatusExceptionMapper = ERROR_MAPPER)
    public void report(
            @Option(longName = "job-type", required = true,
                    description = "reevaluation, backfill or relation-publication") String jobType,
            @Option(longName = "job-id", required = true,
                    description = "Job UUID") String jobId,
            @Option(longName = "url", required = true,
                    description = "PCS base URL") String url,
            @Option(longName = "output",
                    description = "Report output file (default: stdout)") String output,
            @Option(longName = "access-token",
                    description = "PCS access token (default: stdin)") String accessToken,
            @Option(longName = "allow-insecure-http", defaultValue = "false",
                    description = "Allow HTTP only for a loopback PCS URL") boolean allowInsecureHttp,
            CommandContext context) {
        validateInput(context, Set.of(
                "job-type", "job-id", "url", "output", "access-token", "allow-insecure-http"));
        String report = service.report(PcsJobType.fromCliValue(jobType), resolveRequiredJobId(jobId), url,
                optionalPath(output), resolveAccessToken(accessToken), allowInsecureHttp);
        if (report != null) {
            context.outputWriter().print(report);
            context.outputWriter().flush();
        }
    }

    private ResolvedJobId resolveJobId(String jobId) {
        return jobId == null || jobId.isBlank()
                ? new ResolvedJobId(UUID.randomUUID(), true)
                : new ResolvedJobId(parseJobId(jobId), false);
    }

    private UUID resolveRequiredJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new PcsException("Missing job ID.");
        }
        return parseJobId(jobId);
    }

    private UUID parseJobId(String jobId) {
        String normalized = jobId.trim();
        try {
            UUID uuid = UUID.fromString(normalized);
            if (!uuid.toString().equalsIgnoreCase(normalized)) {
                throw new IllegalArgumentException();
            }
            return uuid;
        } catch (IllegalArgumentException e) {
            throw new PcsException("Invalid job ID '" + jobId + "'. Must be a UUID.");
        }
    }

    private Path requiredPath(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new PcsException("Missing required " + option + " path.");
        }
        return path(value);
    }

    private Path optionalPath(String value) {
        return value == null || value.isBlank() ? null : path(value);
    }

    private Path path(String value) {
        try {
            return Path.of(value);
        } catch (InvalidPathException e) {
            throw new PcsException("Invalid file path '" + value + "': " + e.getMessage(), e);
        }
    }

    private String resolveAccessToken(String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken.trim();
        }
        try {
            String stdinToken = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (stdinToken.isBlank()) {
                throw new PcsException("Missing access token. Provide --access-token or pipe the token to stdin.");
            }
            return stdinToken;
        } catch (IOException e) {
            throw new PcsException("Could not read access token from stdin: " + e.getMessage(), e);
        }
    }

    private void printJobId(CommandContext context, UUID jobId) {
        context.outputWriter().println(jobId);
        context.outputWriter().flush();
    }

    private void submit(ResolvedJobId jobId, Submission submission) {
        try {
            submission.run();
        } catch (PcsException e) {
            if (!jobId.generated()) {
                throw e;
            }
            throw new PcsException("Submission failed for generated job ID " + jobId.value() + ": "
                    + e.getMessage(), e);
        }
    }

    private void validateInput(CommandContext context, Set<String> allowedOptions) {
        if (!context.parsedInput().arguments().isEmpty()) {
            throw new PcsException("Unexpected positional arguments: " + context.parsedInput().arguments().stream()
                    .map(argument -> argument.value())
                    .toList());
        }
        context.parsedInput().options().stream()
                .filter(option -> option.longName() == null || !allowedOptions.contains(option.longName()))
                .findFirst()
                .ifPresent(option -> {
                    String name = option.longName() == null ? String.valueOf(option.shortName()) : option.longName();
                    throw new PcsException("Unknown option '--" + name + "'. Use --help to list valid options.");
                });
    }

    private record ResolvedJobId(UUID value, boolean generated) {
    }

    @FunctionalInterface
    private interface Submission {
        void run();
    }
}
