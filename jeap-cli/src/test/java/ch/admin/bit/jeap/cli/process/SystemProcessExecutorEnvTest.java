package ch.admin.bit.jeap.cli.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemProcessExecutorEnvTest {

    @Test
    void testEnvironmentVariablesArePassed(@TempDir Path tempDir) throws IOException, InterruptedException {
        SystemProcessExecutor executor = new SystemProcessExecutor();
        // Use a simple command to print an environment variable
        // On Linux/macOS we can use 'sh -c echo $TEST_VAR'
        
        // We can't easily set an environment variable in the current process in Java reliably (System.setProperty is NOT System.getenv)
        // But we can check if SOME existing environment variable is passed.
        // Or we can assume that if we add it to the builder, it's there.
        
        // Let's try to see if 'PATH' is there, which should always be there.
        ProcessExecutionResult result = executor.executeAndCapture(List.of("sh", "-c", "echo $PATH"), tempDir);
        
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.combinedOutput()).contains(System.getenv("PATH"));
    }

    @Test
    void ensureMavenEnvironmentKeepsExistingMavenOpts() {
        SystemProcessExecutor executor = new SystemProcessExecutor();
        Map<String, String> environment = new HashMap<>();
        environment.put("MAVEN_OPTS", "-Xmx512m");
        environment.put("http_proxy", "http://proxy.example.org:8080");

        executor.ensureMavenEnvironment(environment);

        assertThat(environment.get("MAVEN_OPTS")).isEqualTo("-Xmx512m");
    }

    @Test
    void ensureMavenEnvironmentPrefersJeapMavenOpts() {
        SystemProcessExecutor executor = new SystemProcessExecutor();
        Map<String, String> environment = new HashMap<>();
        environment.put("JEAP_MAVEN_OPTS", "-Dcustom=true");
        environment.put("MAVEN_OPTS", "-Xmx512m");
        environment.put("http_proxy", "http://proxy.example.org:8080");

        executor.ensureMavenEnvironment(environment);

        assertThat(environment.get("MAVEN_OPTS")).isEqualTo("-Dcustom=true");
    }

    @Test
    void ensureMavenEnvironmentFallsBackToProxyEnvironmentVariables() {
        SystemProcessExecutor executor = new SystemProcessExecutor();
        Map<String, String> environment = new HashMap<>();
        environment.put("http_proxy", "http://user:secret@proxy.example.org:8080");
        environment.put("https_proxy", "https://secure-proxy.example.org:8443");
        environment.put("no_proxy", "localhost,127.0.0.1,.example.org,service.internal:8081");

        executor.ensureMavenEnvironment(environment);

        assertThat(environment.get("MAVEN_OPTS")).isEqualTo(String.join(" ",
                "-Dhttp.proxyHost=proxy.example.org",
                "-Dhttp.proxyPort=8080",
                "-Dhttps.proxyHost=secure-proxy.example.org",
                "-Dhttps.proxyPort=8443",
                "-Dhttp.nonProxyHosts=localhost|127.0.0.1|*.example.org|service.internal",
                "-Dhttps.nonProxyHosts=localhost|127.0.0.1|*.example.org|service.internal"));
    }

    @Test
    void ensureMavenEnvironmentUsesDefaultProxyPorts() {
        SystemProcessExecutor executor = new SystemProcessExecutor();
        Map<String, String> environment = new HashMap<>();
        environment.put("HTTP_PROXY", "proxy.example.org");
        environment.put("HTTPS_PROXY", "secure-proxy.example.org");

        executor.ensureMavenEnvironment(environment);

        assertThat(environment.get("MAVEN_OPTS")).isEqualTo(String.join(" ",
                "-Dhttp.proxyHost=proxy.example.org",
                "-Dhttp.proxyPort=80",
                "-Dhttps.proxyHost=secure-proxy.example.org",
                "-Dhttps.proxyPort=443"));
    }

}
