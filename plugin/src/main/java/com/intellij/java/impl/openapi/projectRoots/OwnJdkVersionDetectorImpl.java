// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.openapi.projectRoots;

import com.intellij.java.language.projectRoots.OwnJdkVersionDetector;
import consulo.annotation.component.ServiceImpl;
import consulo.logging.Logger;
import consulo.platform.CpuArchitecture;
import consulo.platform.JavaVersion;
import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.util.CapturingProcessUtil;
import consulo.process.util.ProcessOutput;
import consulo.util.lang.StringUtil;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * Reads the JDK where it is - the files through the {@link Path} of its platform, {@code java -version} run there.
 *
 * @author nik
 */
@Singleton
@ServiceImpl
public class OwnJdkVersionDetectorImpl extends OwnJdkVersionDetector {
    private static final Logger LOG = Logger.getInstance(OwnJdkVersionDetectorImpl.class);

    private static final int VERSION_TIMEOUT_MS = 30_000;

    @Nullable
    @Override
    public JdkVersionInfo detectJdkVersionInfo(Platform platform, Path home) {
        // Java 1.7+
        Path releaseFile = home.resolve("release");
        if (Files.isRegularFile(releaseFile)) {
            Properties p = new Properties();
            try (InputStream stream = Files.newInputStream(releaseFile)) {
                p.load(stream);
                String versionString = p.getProperty("JAVA_FULL_VERSION", p.getProperty("JAVA_VERSION"));
                if (versionString != null) {
                    JavaVersion version = JavaVersion.parse(versionString);
                    String arch = StringUtil.unquoteString(p.getProperty("OS_ARCH", ""));
                    boolean x64 = "x86_64".equals(arch) || "amd64".equals(arch);
                    return new JdkVersionInfo(version, x64 ? CpuArchitecture.X86_64 : CpuArchitecture.X86);
                }
            }
            catch (IOException | IllegalArgumentException e) {
                LOG.info(releaseFile.toString(), e);
            }
        }

        // Java 1.2 - 1.8
        Path rtFile = home.resolve("jre/lib/rt.jar");
        if (Files.isRegularFile(rtFile)) {
            try (JarInputStream rtJar = new JarInputStream(Files.newInputStream(rtFile), false)) {
                Manifest manifest = rtJar.getManifest();
                if (manifest != null) {
                    String versionString = manifest.getMainAttributes().getValue("Implementation-Version");
                    if (versionString != null) {
                        JavaVersion version = JavaVersion.parse(versionString);
                        boolean x64 = platform.os().isMac() || Files.isDirectory(rtFile.getParent().resolve("amd64"));
                        return new JdkVersionInfo(version, x64 ? CpuArchitecture.X86_64 : CpuArchitecture.X86);
                    }
                }
            }
            catch (IOException | IllegalArgumentException e) {
                LOG.info(rtFile.toString(), e);
            }
        }

        // last resort
        Path javaExe = home.resolve("bin").resolve(platform.os().isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(javaExe)) {
            try {
                GeneralCommandLine commandLine = new GeneralCommandLine();
                commandLine.withPlatform(platform);
                commandLine.setExePath(javaExe.toString());
                commandLine.addParameter("-version");
                ProcessOutput output = CapturingProcessUtil.execAndGetOutput(commandLine, VERSION_TIMEOUT_MS);

                // the version is printed to the error stream
                List<String> lines = new ArrayList<>(output.getStderrLines());
                lines.addAll(output.getStdoutLines());
                if (!lines.isEmpty()) {
                    JavaVersion base = JavaVersion.parse(lines.get(0));
                    JavaVersion rt = JavaVersion.tryParse(lines.size() > 2 ? lines.get(1) : null);
                    JavaVersion version = rt != null && rt.feature == base.feature && rt.minor == base.minor ? rt : base;
                    boolean x64 = lines.stream().anyMatch(s -> s.contains("64-Bit") || s.contains("x86_64") || s.contains("amd64"));
                    return new JdkVersionInfo(version, x64 ? CpuArchitecture.X86_64 : CpuArchitecture.X86);
                }
            }
            catch (ExecutionException | IllegalArgumentException e) {
                LOG.info(javaExe.toString(), e);
            }
        }

        return null;
    }
}
