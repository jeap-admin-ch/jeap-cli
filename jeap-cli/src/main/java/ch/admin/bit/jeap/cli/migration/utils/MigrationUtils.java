package ch.admin.bit.jeap.cli.migration.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

@UtilityClass
@Slf4j
public class MigrationUtils {

    public static List<File> findFilesWithNames(Path path, String... filenames) throws IOException {
        return findFiles(path, file -> isOneOfWantedFiles(file, filenames));
    }

    public static List<File> findFiles(Path path, Predicate<Path> filter) throws IOException {
        final List<File> foundFiles = new ArrayList<>();
        try (Stream<Path> walkStream = Files.walk(path)) {
            walkStream.filter(p -> p.toFile().isFile())
                    .forEach(f -> {
                        if (filter.test(f)) {
                            foundFiles.add(f.toFile());
                        }
                    });
        }
        return foundFiles;
    }

    public static String removeLinesContaining(String template, String match) {
        return Arrays.stream(template.split("\n"))
                .filter(line -> !line.contains(match))
                .collect(Collectors.joining("\n"));
    }

    public static String readFileToString(String path) {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource(path);
        try {
            return asString(resource);
        } catch (Exception _) {
            try {
                // Backup
                return Files.readString(Path.of(path));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    public static String asString(Resource resource) {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    private static boolean isOneOfWantedFiles(Path file, String... filenames) {
        for (String filename : filenames) {
            if (file.toString().endsWith(filename)) {
                return true;
            }
        }
        return false;
    }
}
