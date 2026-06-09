package ch.admin.bit.jeap.cli.process;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link ProcessExecutor} that executes commands using the system's ProcessBuilder.
 * <p>
 * This implementation inherits I/O from the parent process, allowing command output to be displayed
 * to the user in real-time.
 * </p>
 */
@Slf4j
@Component
public class SystemProcessExecutor implements ProcessExecutor {
    private static final String CERT_REPO_TOKEN = "CERTIFICATES_REPO_GIT_TOKEN";
    private static final String JEAP_MAVEN_OPTS = "JEAP_MAVEN_OPTS";
    private static final String JEAP_MAVEN_SETTINGS = "JEAP_MAVEN_SETTINGS";
    private static final String JEAP_MAVEN_REPO_LOCAL = "JEAP_MAVEN_REPO_LOCAL";
    private static final String MAVEN_OPTS = "MAVEN_OPTS";

    @Override
    public int execute(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        return executeAndCapture(command, workingDirectory).exitCode();
    }

    @Override
    public ProcessExecutionResult executeAndCapture(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        List<String> effectiveCommand = adjustCommand(command);
        ProcessBuilder processBuilder = createProcessBuilder(effectiveCommand, workingDirectory);
        ensureCertificateRepoToken(processBuilder.environment(), workingDirectory);
        ensureMavenEnvironment(processBuilder.environment());
        logMavenExecutionDetailsIfApplicable(effectiveCommand, processBuilder.environment());
        return runProcess(processBuilder);
    }

    private ProcessBuilder createProcessBuilder(List<String> command, Path workingDirectory) {
        return new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);
    }

    private ProcessExecutionResult runProcess(ProcessBuilder processBuilder) throws IOException, InterruptedException {
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                output.append(line).append(System.lineSeparator());
            }
        }

        return new ProcessExecutionResult(process.waitFor(), output.toString());
    }

    private void ensureCertificateRepoToken(Map<String, String> environment, Path workingDirectory) {
        if (hasText(environment.get(CERT_REPO_TOKEN))) {
            return;
        }
        String resolvedToken = resolveTokenFromEnvironment(environment);
        if (!hasText(resolvedToken)) {
            resolvedToken = resolveTokenViaGhCli(workingDirectory, environment);
        }
        if (hasText(resolvedToken)) {
            environment.put(CERT_REPO_TOKEN, resolvedToken);
        } else {
            logWarn("Could not resolve CERTIFICATES_REPO_GIT_TOKEN from env or gh auth token.");
        }
    }

    private List<String> adjustCommand(List<String> command) {
        if (command == null || command.isEmpty()) {
            return command;
        }
        if (!isMavenCommand(command.getFirst())) {
            return command;
        }
        String settingsPath = System.getenv(JEAP_MAVEN_SETTINGS);
        String repoLocalPath = System.getenv(JEAP_MAVEN_REPO_LOCAL);
        if (!hasText(settingsPath) && !hasText(repoLocalPath)) {
            return command;
        }
        List<String> adjusted = new ArrayList<>(command.size() + 4);
        adjusted.add(command.getFirst());
        if (hasText(settingsPath) && !commandContainsSettings(command)) {
            adjusted.add("--settings");
            adjusted.add(settingsPath);
        }
        if (hasText(repoLocalPath) && !commandContainsRepoLocal(command)) {
            adjusted.add("-Dmaven.repo.local=" + repoLocalPath);
        }
        adjusted.addAll(command.subList(1, command.size()));
        return adjusted;
    }

    void ensureMavenEnvironment(Map<String, String> environment) {
        String canonicalMavenOpts = environment.get(JEAP_MAVEN_OPTS);
        if (hasText(canonicalMavenOpts)) {
            environment.put(MAVEN_OPTS, canonicalMavenOpts);
            return;
        }
        if (hasText(environment.get(MAVEN_OPTS))) {
            return;
        }
        String proxyMavenOpts = buildMavenOptsFromProxyEnvironment(environment);
        if (hasText(proxyMavenOpts)) {
            environment.put(MAVEN_OPTS, proxyMavenOpts);
            logInfo("Fallback for http proxy in mavenopts: " + proxyMavenOpts);
        }
    }

    private String buildMavenOptsFromProxyEnvironment(Map<String, String> environment) {
        List<String> opts = new ArrayList<>();
        addProxyOptions(opts, "http", firstEnvironmentValue(environment, "http_proxy", "HTTP_PROXY"), 80);
        addProxyOptions(opts, "https", firstEnvironmentValue(environment, "https_proxy", "HTTPS_PROXY"), 443);

        String noProxy = firstEnvironmentValue(environment, "no_proxy", "NO_PROXY");
        if (!opts.isEmpty() && hasText(noProxy)) {
            String nonProxyHosts = toMavenNonProxyHosts(noProxy);
            if (hasText(nonProxyHosts)) {
                opts.add("-Dhttp.nonProxyHosts=" + nonProxyHosts);
                opts.add("-Dhttps.nonProxyHosts=" + nonProxyHosts);
            }
        }
        return String.join(" ", opts);
    }

    private void addProxyOptions(List<String> opts, String protocol, String proxyUrl, int defaultPort) {
        ProxyConfiguration proxyConfiguration = parseProxyConfiguration(protocol, proxyUrl, defaultPort);
        if (proxyConfiguration == null) {
            return;
        }
        opts.add("-D" + protocol + ".proxyHost=" + proxyConfiguration.host());
        opts.add("-D" + protocol + ".proxyPort=" + proxyConfiguration.port());
    }

    private ProxyConfiguration parseProxyConfiguration(String protocol, String proxyUrl, int defaultPort) {
        if (!hasText(proxyUrl)) {
            return null;
        }
        String value = proxyUrl.trim();
        String valueWithScheme = value.contains("://") ? value : protocol + "://" + value;
        try {
            URI uri = new URI(valueWithScheme);
            String host = uri.getHost();
            if (!hasText(host)) {
                logWarn("Could not parse " + protocol + "_proxy for Maven proxy fallback: missing host");
                return null;
            }
            int port = uri.getPort() == -1 ? defaultPort : uri.getPort();
            return new ProxyConfiguration(host, port);
        } catch (URISyntaxException e) {
            logWarn("Could not parse " + protocol + "_proxy for Maven proxy fallback: " + e.getReason());
            return null;
        }
    }

    private String firstEnvironmentValue(Map<String, String> environment, String... keys) {
        for (String key : keys) {
            String value = environment.get(key);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String toMavenNonProxyHosts(String noProxy) {
        List<String> nonProxyHosts = new ArrayList<>();
        for (String entry : noProxy.split(",")) {
            String normalized = normalizeNoProxyEntry(entry);
            if (hasText(normalized)) {
                nonProxyHosts.add(normalized);
            }
        }
        return String.join("|", nonProxyHosts);
    }

    private String normalizeNoProxyEntry(String entry) {
        if (!hasText(entry)) {
            return null;
        }
        String normalized = entry.trim();
        int schemeSeparator = normalized.indexOf("://");
        if (schemeSeparator >= 0) {
            normalized = normalized.substring(schemeSeparator + 3);
        }
        int pathSeparator = normalized.indexOf('/');
        if (pathSeparator >= 0) {
            normalized = normalized.substring(0, pathSeparator);
        }
        if (normalized.startsWith("[")) {
            int closingBracket = normalized.indexOf(']');
            if (closingBracket >= 0) {
                normalized = normalized.substring(0, closingBracket + 1);
            }
        } else {
            int colon = normalized.lastIndexOf(':');
            if (colon >= 0 && normalized.indexOf(':') == colon) {
                normalized = normalized.substring(0, colon);
            }
        }
        if (normalized.startsWith(".")) {
            normalized = "*" + normalized;
        }
        return normalized;
    }

    private boolean isMavenCommand(String executable) {
        return executable != null && (executable.endsWith("mvn") || executable.endsWith("mvnw") || executable.contains("/mvnw"));
    }

    private boolean commandContainsSettings(List<String> command) {
        for (String arg : command) {
            if ("--settings".equals(arg) || (arg != null && arg.startsWith("--settings=")) || "-s".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private boolean commandContainsRepoLocal(List<String> command) {
        for (String arg : command) {
            if (arg != null && arg.startsWith("-Dmaven.repo.local=")) {
                return true;
            }
        }
        return false;
    }

    private void logMavenExecutionDetailsIfApplicable(List<String> command, Map<String, String> environment) {
        // Only log details on failure if needed, or if we had a verbose flag.
        // For now, we remove the excessive logging as requested.
    }

    private String resolveSettingsPath(List<String> command, Map<String, String> environment) {
        for (int i = 0; i < command.size(); i++) {
            String arg = command.get(i);
            if ("--settings".equals(arg) || "-s".equals(arg)) {
                if (i + 1 < command.size()) {
                    return command.get(i + 1);
                }
            } else if (arg != null && arg.startsWith("--settings=")) {
                return arg.substring("--settings=".length());
            }
        }
        String mavenConfig = environment.get("MAVEN_CONFIG");
        if (hasText(mavenConfig)) {
            Matcher matcher = Pattern.compile("(?:^|\\s)(?:--settings|-s)\\s+([^\\s]+)").matcher(mavenConfig);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String resolveLocalRepository(List<String> command, Map<String, String> environment, String settingsPath) {
        String fromCommand = findRepoLocalInArgs(command);
        if (hasText(fromCommand)) {
            return fromCommand;
        }
        String fromMavenOpts = findRepoLocalInString(environment.get(MAVEN_OPTS));
        if (hasText(fromMavenOpts)) {
            return fromMavenOpts;
        }
        String fromSettings = readLocalRepositoryFromSettings(settingsPath);
        if (hasText(fromSettings)) {
            return fromSettings;
        }
        String mavenUserHome = environment.get("MAVEN_USER_HOME");
        if (hasText(mavenUserHome)) {
            return mavenUserHome + "/repository";
        }
        String home = environment.get("HOME");
        if (hasText(home)) {
            return home + "/.m2/repository";
        }
        return "<unknown>";
    }

    private String findRepoLocalInArgs(List<String> args) {
        for (String arg : args) {
            if (arg != null && arg.startsWith("-Dmaven.repo.local=")) {
                return arg.substring("-Dmaven.repo.local=".length());
            }
        }
        return null;
    }

    private String findRepoLocalInString(String value) {
        if (!hasText(value)) {
            return null;
        }
        Matcher matcher = Pattern.compile("-Dmaven\\.repo\\.local=([^\\s]+)").matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String readLocalRepositoryFromSettings(String settingsPath) {
        if (!hasText(settingsPath)) {
            return null;
        }
        Path path = Path.of(settingsPath);
        if (!path.isAbsolute()) {
            return null;
        }
        if (!path.toFile().exists()) {
            return null;
        }
        try {
            String xml = java.nio.file.Files.readString(path, StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("<localRepository>\\s*([^<]+)\\s*</localRepository>").matcher(xml);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (IOException ignored) {
            // keep fallback behavior
        }
        return null;
    }


    private String resolveTokenFromEnvironment(Map<String, String> environment) {
        if (hasText(environment.get("GH_TOKEN"))) {
            return environment.get("GH_TOKEN");
        }
        if (hasText(environment.get("GITHUB_TOKEN"))) {
            return environment.get("GITHUB_TOKEN");
        }
        return null;
    }

    private String resolveTokenViaGhCli(Path workingDirectory, Map<String, String> parentEnvironment) {
        try {
            ProcessBuilder ghProcessBuilder = new ProcessBuilder("gh", "auth", "token")
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true);
            ghProcessBuilder.environment().putAll(parentEnvironment);

            Process ghProcess = ghProcessBuilder.start();
            String outputLine;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(ghProcess.getInputStream(), StandardCharsets.UTF_8))) {
                outputLine = reader.readLine();
            }
            int exitCode = ghProcess.waitFor();
            if (exitCode == 0 && hasText(outputLine)) {
                return outputLine.trim();
            }
            logWarn("gh auth token failed (exitCode=%d)".formatted(exitCode));
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logWarn("gh auth token failed: %s".formatted(ignored.getClass().getSimpleName()));
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void logInfo(String message) {
        log.info(message);
    }

    private void logWarn(String message) {
        log.warn(message);
    }

    private record ProxyConfiguration(String host, int port) {
    }
}
