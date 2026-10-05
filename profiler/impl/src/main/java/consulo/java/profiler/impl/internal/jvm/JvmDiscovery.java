/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.profiler.impl.internal.jvm;

import consulo.logging.Logger;
import consulo.platform.Platform;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the local HotSpot JVMs of the current user through their {@code hsperfdata_<user>} files, the way {@code jps}
 * does, without the JDK's attach module.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JvmDiscovery {
    private static final Logger LOG = Logger.getInstance(JvmDiscovery.class);

    private static final String PERF_DATA_PREFIX = "hsperfdata_";

    private static final Set<String> OPTIONS_WITH_VALUE = Set.of(
        "-cp", "-classpath", "--class-path", "-p", "--module-path", "--upgrade-module-path", "--add-modules",
        "--add-opens", "--add-exports", "--add-reads", "--patch-module", "--limit-modules", "--enable-native-access"
    );

    private JvmDiscovery() {
    }

    /**
     * @return the pids of the attachable JVMs, without the IDE's own
     */
    public static Set<Integer> listJvmPids() {
        long self = ProcessHandle.current().pid();
        Set<Integer> result = new HashSet<>();
        for (Path directory : perfDataDirectories()) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                for (Path file : files) {
                    int pid = parsePid(file.getFileName().toString());
                    if (pid > 0 && pid != self && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                        result.add(pid);
                    }
                }
            }
            catch (IOException e) {
                LOG.warn("Can't list " + directory, e);
            }
        }
        return result;
    }

    private static Set<Path> perfDataDirectories() {
        String directoryName = PERF_DATA_PREFIX + System.getProperty("user.name");
        Set<Path> directories = new LinkedHashSet<>();
        directories.add(Path.of(System.getProperty("java.io.tmpdir"), directoryName));
        if (!Platform.current().os().isWindows()) {
            directories.add(Path.of("/tmp", directoryName));
        }
        return directories;
    }

    private static int parsePid(String fileName) {
        try {
            return Integer.parseInt(fileName);
        }
        catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @return the main class, module or jar of a JVM command line, or the fallback when it has none
     */
    public static String mainOf(List<String> arguments, String fallback) {
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if ("-jar".equals(argument) && i + 1 < arguments.size()) {
                return Path.of(arguments.get(i + 1)).getFileName().toString();
            }
            if (("-m".equals(argument) || "--module".equals(argument)) && i + 1 < arguments.size()) {
                return arguments.get(i + 1);
            }
            if (OPTIONS_WITH_VALUE.contains(argument)) {
                i++;
                continue;
            }
            if (!argument.startsWith("-")) {
                return argument;
            }
        }
        return fallback;
    }
}
