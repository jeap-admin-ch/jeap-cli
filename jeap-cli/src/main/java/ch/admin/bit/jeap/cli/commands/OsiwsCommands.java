package ch.admin.bit.jeap.cli.commands;

import ch.admin.bit.jeap.cli.osiws.OsiwsException;
import ch.admin.bit.jeap.cli.osiws.OsiwsJobType;
import ch.admin.bit.jeap.cli.osiws.OsiwsMaintenanceService;
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

/**
 * Maintenance jobs of the OpenSearch index writer service (osiws).
 */
@Component
public class OsiwsCommands {

    private static final String ERROR_MAPPER = "osiwsExitStatusExceptionMapper";
    private static final String GROUP = "OpenSearch Index Writer Maintenance";
    private static final Set<String> SUBMIT_OPTIONS = Set.of(
            "file", "csv", "job-id", "url", "access-token", "allow-insecure-http");

    private final OsiwsMaintenanceService service;
    private final InputStream inputStream;

    @Autowired
    public OsiwsCommands(OsiwsMaintenanceService service) {
        this(service, System.in);
    }

    OsiwsCommands(OsiwsMaintenanceService service, InputStream inputStream) {
        this.service = service;
        this.inputStream = inputStream;
    }

    @Command(name = {"osiws", "upsert"}, group = GROUP,
            description = "Submit an upsert job to the OpenSearch index writer (requires upsertjob#create)",
            exitStatusExceptionMapper = ERROR_MAPPER)
    public void upsert(
            @Option(longName = "file", required = true,
                    description = "YAML job file with message, operation and optionally originReferences") String file,
            @Option(longName = "csv",
                    description = "CSV file with id,version or id rows instead of originReferences") String csv,
            @Option(longName = "job-id",
                    description = "Job UUID for safe retry (default: generated)") String jobId,
            @Option(longName = "url", required = true,
                    description = "Index writer base URL") String url,
            @Option(longName = "access-token",
                    description = "Index writer access token (default: stdin)") String accessToken,
            @Option(longName = "allow-insecure-http", defaultValue = "false",
                    description = "Allow HTTP only for a loopback index writer URL") boolean allowInsecureHttp,
            CommandContext context) {
        submit(OsiwsJobType.UPSERT, file, csv, jobId, url, accessToken, allowInsecureHttp, context);
    }

    @Command(name = {"osiws", "delete"}, group = GROUP,
            description = "Submit a delete job to the OpenSearch index writer (requires deletejob#create)",
            exitStatusExceptionMapper = ERROR_MAPPER)
    public void delete(
            @Option(longName = "file", required = true,
                    description = "YAML job file with indexType, optionally indexMajorVersion and originReferences")
            String file,
            @Option(longName = "csv",
                    description = "CSV file with id rows instead of originReferences") String csv,
            @Option(longName = "job-id",
                    description = "Job UUID for safe retry (default: generated)") String jobId,
            @Option(longName = "url", required = true,
                    description = "Index writer base URL") String url,
            @Option(longName = "access-token",
                    description = "Index writer access token (default: stdin)") String accessToken,
            @Option(longName = "allow-insecure-http", defaultValue = "false",
                    description = "Allow HTTP only for a loopback index writer URL") boolean allowInsecureHttp,
            CommandContext context) {
        submit(OsiwsJobType.DELETE, file, csv, jobId, url, accessToken, allowInsecureHttp, context);
    }

    @Command(name = {"osiws", "report"}, group = GROUP,
            description = "Export the complete report of an index writer job (requires jobreport#read)",
            exitStatusExceptionMapper = ERROR_MAPPER)
    public void report(
            @Option(longName = "job-type", required = true,
                    description = "upsert or delete") String jobType,
            @Option(longName = "job-id", required = true,
                    description = "Job UUID") String jobId,
            @Option(longName = "url", required = true,
                    description = "Index writer base URL") String url,
            @Option(longName = "output",
                    description = "Report output file (default: stdout)") String output,
            @Option(longName = "page-size",
                    description = "References per report request (default: the index writer's default)") String pageSize,
            @Option(longName = "access-token",
                    description = "Index writer access token (default: stdin)") String accessToken,
            @Option(longName = "allow-insecure-http", defaultValue = "false",
                    description = "Allow HTTP only for a loopback index writer URL") boolean allowInsecureHttp,
            CommandContext context) {
        validateInput(context, Set.of(
                "job-type", "job-id", "url", "output", "page-size", "access-token", "allow-insecure-http"));
        OsiwsJobType type = OsiwsJobType.fromCliValue(jobType);
        UUID id = resolveRequiredJobId(jobId);
        Integer limit = parsePageSize(pageSize);
        String report = service.report(type, id, url, limit, optionalPath(output), resolveAccessToken(accessToken),
                allowInsecureHttp);
        if (report != null) {
            context.outputWriter().print(report);
            context.outputWriter().flush();
        }
    }

    private void submit(OsiwsJobType type, String file, String csv, String jobId, String url, String accessToken,
                        boolean allowInsecureHttp, CommandContext context) {
        validateInput(context, SUBMIT_OPTIONS);
        UUID id = jobId == null || jobId.isBlank() ? UUID.randomUUID() : parseJobId(jobId);
        service.submit(type, requiredPath(file), optionalPath(csv), id, url, resolveAccessToken(accessToken),
                allowInsecureHttp);
        context.outputWriter().println(id);
        context.outputWriter().flush();
    }

    private UUID resolveRequiredJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new OsiwsException("Missing job ID.");
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
            throw new OsiwsException("Invalid job ID '" + jobId + "'. Must be a UUID.");
        }
    }

    private Integer parsePageSize(String pageSize) {
        if (pageSize == null || pageSize.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(pageSize.trim());
            if (value >= 1 && value <= OsiwsMaintenanceService.MAX_PAGE_SIZE) {
                return value;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new OsiwsException("Invalid page size '" + pageSize + "'. Must be an integer between 1 and "
                + OsiwsMaintenanceService.MAX_PAGE_SIZE + ".");
    }

    private Path requiredPath(String value) {
        if (value == null || value.isBlank()) {
            throw new OsiwsException("Missing required --file path.");
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
            throw new OsiwsException("Invalid file path '" + value + "': " + e.getMessage(), e);
        }
    }

    private String resolveAccessToken(String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken.trim();
        }
        try {
            String stdinToken = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (stdinToken.isBlank()) {
                throw new OsiwsException("Missing access token. Provide --access-token or pipe the token to stdin.");
            }
            return stdinToken;
        } catch (IOException e) {
            throw new OsiwsException("Could not read access token from stdin: " + e.getMessage(), e);
        }
    }

    private void validateInput(CommandContext context, Set<String> allowedOptions) {
        if (!context.parsedInput().arguments().isEmpty()) {
            throw new OsiwsException("Unexpected positional arguments: " + context.parsedInput().arguments().stream()
                    .map(argument -> argument.value())
                    .toList());
        }
        context.parsedInput().options().stream()
                .filter(option -> option.longName() == null || !allowedOptions.contains(option.longName()))
                .findFirst()
                .ifPresent(option -> {
                    String name = option.longName() == null ? String.valueOf(option.shortName()) : option.longName();
                    throw new OsiwsException("Unknown option '--" + name + "'. Use --help to list valid options.");
                });
    }
}
