/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.openapi.projectRoots.impl;

import com.intellij.java.language.internal.DefaultJavaSdkType;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.projectRoots.OwnJdkVersionDetector;
import com.intellij.java.language.projectRoots.roots.AnnotationOrderRootType;
import com.intellij.java.language.vfs.jrt.JrtFileSystem;
import consulo.annotation.component.ExtensionImpl;
import consulo.container.plugin.PluginManager;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.base.DocumentationOrderRootType;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.java.language.impl.icon.JavaPsiImplIconGroup;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.platform.PlatformOperatingSystem;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.localize.ProjectLocalize;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author Eugene Zhuravlev
 * @since Sep 17, 2004
 */
@ExtensionImpl
public class DefaultJavaSdkTypeImpl extends DefaultJavaSdkType {
    private static final Logger LOG = Logger.getInstance(DefaultJavaSdkTypeImpl.class);
    // do not use javaw.exe for Windows because of issues with encoding
    private static final String VM_EXE_NAME = "java";
    private final Pattern myVersionStringPattern = Pattern.compile("^(.*)java version \"([1234567890_.]*)\"(.*)$");
    private static final String JAVA_VERSION_PREFIX = "java version ";
    private static final String OPENJDK_VERSION_PREFIX = "openjdk version ";
    public static final Key<Boolean> KEY = Key.create("JavaSdk");

    private static final Set<String> ourRootTypeIds = Set.of(
        BinariesOrderRootType.ID,
        SourcesOrderRootType.ID,
        DocumentationOrderRootType.ID,
        AnnotationOrderRootType.ID
    );

    public DefaultJavaSdkTypeImpl() {
        super("JDK", ProjectLocalize.sdkJavaName(), JavaPsiImplIconGroup.java());
    }

    @Override
    @Nullable
    public String getDefaultDocumentationUrl(Sdk sdk) {
        JavaSdkVersion version = JavaSdkTypeUtil.getVersion(sdk);
        if (version == JavaSdkVersion.JDK_1_5) {
            return "http://docs.oracle.com/javase/1.5.0/docs/api/";
        }
        if (version == JavaSdkVersion.JDK_1_6) {
            return "http://docs.oracle.com/javase/6/docs/api/";
        }
        if (version == JavaSdkVersion.JDK_1_7) {
            return "http://docs.oracle.com/javase/7/docs/api/";
        }
        if (version == JavaSdkVersion.JDK_1_8) {
            return "http://download.java.net/jdk8/docs/api/";
        }
        if (version == JavaSdkVersion.JDK_1_9) {
            return "http://download.java.net/jdk9/docs/api/";
        }
        if (version == JavaSdkVersion.JDK_10) {
            return "https://docs.oracle.com/javase/10/docs/api/";
        }
        if (version == JavaSdkVersion.JDK_11) {
            return "https://docs.oracle.com/en/java/javase/11/docs/api/";
        }
        if (version == JavaSdkVersion.JDK_12) {
            return "https://docs.oracle.com/en/java/javase/12/docs/api/";
        }
        return null;
    }

    @Override
    public String getBinPath(Sdk sdk) {
        return sdk.getHomeNioPath().resolve("bin").toString();
    }

    @Override
    public String getToolsPath(Sdk sdk) {
        String versionString = sdk.getVersionString();
        boolean isJdk1_x = versionString != null && (versionString.contains("1.0") || versionString.contains("1.1"));
        return sdk.getHomeNioPath().resolve("lib").resolve(isJdk1_x ? "classes.zip" : "tools.jar").toString();
    }

    @Override
    public void setupCommandLine(GeneralCommandLine commandLine, Sdk sdk) {
        commandLine.withPlatform(sdk.getPlatform());
        commandLine.setExePath(sdk.getHomeNioPath().resolve("bin").resolve(VM_EXE_NAME).toString());
    }

    /**
     * @implNote do not check JAVA_HOME, {@link #getEnvironmentVariables(Platform)}
     */
    @Override
    public void collectHomePaths(Platform platform, Consumer<Path> consumer) {
        Set<Path> list = new LinkedHashSet<>();
        PlatformOperatingSystem os = platform.os();

        if (os.isMac()) {
            collectJavaPaths(list, platform.fs().getPath("/Library/Java/JavaVirtualMachines"));
            collectJavaPaths(list, platform.fs().getPath("/System/Library/Java/JavaVirtualMachines"));
        }
        else if (os.isLinux()) {
            collectJavaPaths(list, platform.fs().getPath("/usr/java"));
            collectJavaPaths(list, platform.fs().getPath("/opt/java"));
            collectJavaPaths(list, platform.fs().getPath("/usr/lib/jvm"));
        }
        else if (os.isWindows()) {
            String[] subDirs = {
                "Java", // original oracle distro
                "Eclipse Adoptium",
                "Amazon Corretto",
                "Microsoft"
            };

            collectJavaPathsAtWindows(platform, list, "ProgramFiles", subDirs);
            collectJavaPathsAtWindows(platform, list, "ProgramFiles(x86)", subDirs);
        }

        // JDKs in SDKMan located at $HOME/.sdkman/candidates/java/
        Path sdkmanJavaDir = platform.user().homePath().resolve(".sdkman").resolve("candidates").resolve("java");
        collectJavaPaths(list, sdkmanJavaDir);

        list.forEach(consumer);
    }

    @Override
    public Set<String> getEnvironmentVariables(Platform platform) {
        return Set.of("JAVA_HOME");
    }

    /**
     * The JDKs in a directory of them - its children, the links ({@code default}, {@code current}) aside: they are the same JDKs.
     */
    private static void collectJavaPaths(Collection<Path> list, Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> children = Files.list(dir)) {
            children.filter(child -> Files.isDirectory(child) && !Files.isSymbolicLink(child)).forEach(list::add);
        }
        catch (IOException | UncheckedIOException e) {
            LOG.debug(e);
        }
    }

    private static void collectJavaPathsAtWindows(Platform platform, Collection<Path> list, String env, String... subDirs) {
        String programFiles = platform.os().getEnvironmentVariable(env);
        if (programFiles == null) {
            return;
        }

        for (String subDir : subDirs) {
            collectJavaPaths(list, platform.fs().getPath(programFiles).resolve(subDir));
        }
    }

    @Override
    public boolean canCreatePredefinedSdks(Platform platform) {
        return true;
    }

    @Override
    public FileChooserDescriptor getHomeChooserDescriptor(Platform platform) {
        FileChooserDescriptor descriptor = super.getHomeChooserDescriptor(platform);
        descriptor.putUserData(KEY, Boolean.TRUE);
        return descriptor;
    }

    @Override
    public Path adjustSelectedSdkHome(Platform platform, Path homePath) {
        if (platform.os().isMac()) {
            Path home = homePath.resolve("Home");
            if (Files.exists(home)) {
                return home;
            }

            home = homePath.resolve("Contents").resolve("Home");
            if (Files.exists(home)) {
                return home;
            }
        }

        return homePath;
    }

    @Override
    public boolean isValidSdkHome(Platform platform, Path path) {
        return OwnJdkUtil.checkForJdk(path);
    }

    @Override
    public String suggestSdkName(Platform platform, @Nullable String currentSdkName, Path sdkHome) {
        String suggestedName;
        if (currentSdkName != null && !currentSdkName.isEmpty()) {
            Matcher matcher = myVersionStringPattern.matcher(currentSdkName);
            boolean replaceNameWithVersion = matcher.matches();
            if (replaceNameWithVersion) {
                // user did not change name -> set it automatically
                String versionString = getVersionString(platform, sdkHome);
                suggestedName = versionString == null ? currentSdkName : matcher.replaceFirst("$1" + versionString + "$3");
            }
            else {
                suggestedName = currentSdkName;
            }
        }
        else {
            String versionString = getVersionString(platform, sdkHome);
            suggestedName = versionString == null ? ProjectLocalize.sdkJavaUnknownName().get() : getVersionNumber(versionString);
        }
        return suggestedName;
    }

    private static String getVersionNumber(String versionString) {
        if (versionString.startsWith(JAVA_VERSION_PREFIX) || versionString.startsWith(OPENJDK_VERSION_PREFIX)) {
            boolean openJdk = versionString.startsWith(OPENJDK_VERSION_PREFIX);
            versionString = versionString.substring(openJdk ? OPENJDK_VERSION_PREFIX.length() : JAVA_VERSION_PREFIX.length());
            if (versionString.startsWith("\"") && versionString.endsWith("\"")) {
                versionString = versionString.substring(1, versionString.length() - 1);
            }
            int dotIdx = versionString.indexOf('.');
            if (dotIdx > 0) {
                try {
                    int major = Integer.parseInt(versionString.substring(0, dotIdx));
                    int minorDot = versionString.indexOf('.', dotIdx + 1);
                    if (minorDot > 0) {
                        int minor = Integer.parseInt(versionString.substring(dotIdx + 1, minorDot));
                        versionString = major + "." + minor;
                    }
                }
                catch (NumberFormatException e) {
                    // Do nothing. Use original version string if failed to parse according to major.minor pattern.
                }
            }
        }
        return versionString;
    }

    /**
     * The roots are files of the machine of the JDK - the local one, or another platform; the archives of another platform are
     * its {@code platform-} archives, read where they are. The roots are added by their urls, made of the paths - nothing is
     * opened to find them.
     */
    @Override
    public void setupSdkPaths(Sdk sdk) {
        Path jdkHome = sdk.getHomeNioPath();
        boolean mac = sdk.getPlatform().os().isMac();
        VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();

        SdkModificator sdkModificator = sdk.getSdkModificator();

        boolean isModuleJdk = false;
        // the modules of a modular runtime - its sources are by module too: src.zip!/java.base/java/lang/String.java
        List<String> modules = null;
        Path jmodsDirectory = jdkHome.resolve("jmods");
        if (Files.isDirectory(jmodsDirectory)) {
            isModuleJdk = true;
            for (Path jmod : list(jmodsDirectory)) {
                String jmodRoot = ArchiveVfsUtil.getArchiveRootUrl(jmod);
                if (jmodRoot != null) {
                    sdkModificator.addRoot(jmodRoot + "classes", BinariesOrderRootType.ID);
                }
            }
        }
        else if (OwnJdkUtil.isModularRuntime(jdkHome)) {
            String jrtBaseUrl = ArchiveVfsUtil.getArchiveRootUrl(JrtFileSystem.PROTOCOL, jdkHome);
            if (jrtBaseUrl != null) {
                modules = readModulesFromReleaseFile(jdkHome);
                if (modules != null) {
                    for (String module : modules) {
                        sdkModificator.addRoot(jrtBaseUrl + module, BinariesOrderRootType.ID);
                    }
                }
                else {
                    VirtualFile jrt = virtualFileManager.findFileByUrl(jrtBaseUrl);
                    if (jrt != null) {
                        for (VirtualFile virtualFile : jrt.getChildren()) {
                            sdkModificator.addRoot(virtualFile.getUrl(), BinariesOrderRootType.ID);
                        }
                    }
                }
            }
        }
        else {
            addArchiveRoot(sdkModificator, jdkHome.resolve("javafx-src.zip"), SourcesOrderRootType.ID);

            for (Path classesRoot : getJdkClassesRoots(jdkHome, false, mac)) {
                String url = Files.isDirectory(classesRoot) ? virtualFileManager.getUrl(classesRoot) : ArchiveVfsUtil.getArchiveRootUrl(classesRoot);
                if (url != null) {
                    sdkModificator.addRoot(url, BinariesOrderRootType.ID);
                }
            }
        }

        boolean noSources = true;
        Path srcZip = jdkHome.resolve("src.zip");
        if (!Files.isRegularFile(srcZip)) {
            srcZip = jdkHome.resolve("lib").resolve("src.zip");
        }
        String srcZipUrl = Files.isRegularFile(srcZip) ? ArchiveVfsUtil.getArchiveRootUrl(srcZip) : null;
        if (srcZipUrl != null) {
            noSources = false;

            if (isModuleJdk) {
                // a root per module - the archive is to be listed
                VirtualFile srcZipFile = virtualFileManager.findFileByUrl(srcZipUrl);
                if (srcZipFile != null) {
                    for (VirtualFile child : srcZipFile.getChildren()) {
                        if (child.isDirectory()) {
                            sdkModificator.addRoot(child, SourcesOrderRootType.ID);
                        }
                    }
                }
            }
            else if (modules != null) {
                for (String module : modules) {
                    sdkModificator.addRoot(srcZipUrl + module, SourcesOrderRootType.ID);
                }
            }
            else {
                sdkModificator.addRoot(srcZipUrl, SourcesOrderRootType.ID);
            }
        }

        String docs = findDocs(jdkHome, "docs/api");
        if (docs != null) {
            sdkModificator.addRoot(docs, DocumentationOrderRootType.ID);
        }
        else if (mac) {
            String commonDocs = findDocs(jdkHome, "docs");
            if (commonDocs == null) {
                commonDocs = findInArchive(jdkHome.resolve("docs.jar"), "doc/api");
                if (commonDocs == null) {
                    commonDocs = findInArchive(jdkHome.resolve("docs.jar"), "docs/api");
                }
            }
            if (commonDocs != null) {
                sdkModificator.addRoot(commonDocs, DocumentationOrderRootType.ID);
            }

            String appleDocs = findDocs(jdkHome, "appledocs");
            if (appleDocs == null) {
                appleDocs = findInArchive(jdkHome.resolve("appledocs.jar"), "appledoc/api");
            }
            if (appleDocs != null) {
                sdkModificator.addRoot(appleDocs, DocumentationOrderRootType.ID);
            }

            if (commonDocs == null && appleDocs == null && noSources) {
                String url = getDefaultDocumentationUrl(sdk);
                if (url != null) {
                    sdkModificator.addRoot(VirtualFileManager.getInstance().findFileByUrl(url), DocumentationOrderRootType.ID);
                }
            }
        }
        attachJdkAnnotations(sdkModificator);
        sdkModificator.commitChanges();
    }

    private static List<Path> list(Path directory) {
        try (Stream<Path> children = Files.list(directory)) {
            return children.toList();
        }
        catch (IOException | UncheckedIOException e) {
            LOG.debug(e);
            return List.of();
        }
    }

    private static void addArchiveRoot(SdkModificator sdkModificator, Path archive, String rootType) {
        if (Files.isRegularFile(archive)) {
            String url = ArchiveVfsUtil.getArchiveRootUrl(archive);
            if (url != null) {
                sdkModificator.addRoot(url, rootType);
            }
        }
    }

    /**
     * Tries to load the list of modules in the JDK from the 'release' file. Returns null if the 'release' file is not there
     * or doesn't contain the expected information.
     */
    @Nullable
    private static List<String> readModulesFromReleaseFile(Path jrtBaseDir) {
        Path releaseFile = jrtBaseDir.resolve("release");
        if (Files.isRegularFile(releaseFile)) {
            try (InputStream stream = Files.newInputStream(releaseFile)) {
                Properties p = new Properties();
                p.load(stream);
                String modules = p.getProperty("MODULES");
                if (modules != null) {
                    return StringUtil.split(StringUtil.unquoteString(modules), " ");
                }
            }
            catch (IOException | IllegalArgumentException e) {
                LOG.info(e);
            }
        }
        return null;
    }

    public static boolean attachJdkAnnotations(SdkModificator modificator) {
        File pluginPath = PluginManager.getPluginPath(DefaultJavaSdkTypeImpl.class);

        File file = new File(pluginPath, "jdk-annotations.jar");

        VirtualFile localFile = LocalFileSystem.getInstance().findFileByIoFile(file);

        if (localFile == null) {
            LOG.error("jdk annotations not found in: " + file);
            return false;
        }

        VirtualFile jarFile = ArchiveVfsUtil.getArchiveRootForLocalFile(localFile);
        if (jarFile == null) {
            LOG.error("jdk annotations is not archive: " + file);
            return false;
        }

        String annoType = AnnotationOrderRootType.ID;
        modificator.removeRoot(jarFile, annoType);
        modificator.addRoot(jarFile, annoType);
        return true;
    }

    @Override
    public @Nullable String getVersionString(Platform platform, Path sdkHome) {
        OwnJdkVersionDetector.JdkVersionInfo jdkInfo = OwnJdkVersionDetector.getInstance().detectJdkVersionInfo(platform, sdkHome);
        return jdkInfo != null ? OwnJdkVersionDetector.formatVersionString(jdkInfo.version()) : null;
    }

    public static List<File> getJdkClassesRoots(File home, boolean isJre) {
        List<File> files = new ArrayList<>();
        for (Path path : getJdkClassesRoots(home.toPath(), isJre, Platform.current().os().isMac())) {
            files.add(path.toFile());
        }
        return files;
    }

    /**
     * @param mac whether the JDK is of a mac - its layout differs
     */
    public static List<Path> getJdkClassesRoots(Path home, boolean isJre, boolean mac) {
        Path[] jarDirs;
        Path fileName = home.getFileName();
        if (mac && (fileName == null || !fileName.toString().startsWith("mockJDK"))) {
            Path openJdkRtJar = home.resolve("jre/lib/rt.jar");
            if (Files.isRegularFile(openJdkRtJar)) {
                Path libDir = home.resolve("lib");
                Path classesDir = openJdkRtJar.getParent();
                Path libExtDir = classesDir.resolve("ext");
                Path libEndorsedDir = libDir.resolve("endorsed");
                jarDirs = new Path[]{
                    libEndorsedDir,
                    libDir,
                    classesDir,
                    libExtDir
                };
            }
            else {
                Path libDir = home.resolve("lib");
                Path classesDir = home.resolve("../Classes").normalize();
                Path libExtDir = libDir.resolve("ext");
                Path libEndorsedDir = libDir.resolve("endorsed");
                jarDirs = new Path[]{
                    libEndorsedDir,
                    libDir,
                    classesDir,
                    libExtDir
                };
            }
        }
        else {
            Path libDir = isJre ? home.resolve("lib") : home.resolve("jre/lib");
            Path libExtDir = libDir.resolve("ext");
            Path libEndorsedDir = libDir.resolve("endorsed");
            jarDirs = new Path[]{
                libEndorsedDir,
                libDir,
                libExtDir
            };
        }

        Set<String> pathFilter = new HashSet<>();
        List<Path> rootFiles = new ArrayList<>();
        for (Path jarDir : jarDirs) {
            if (!Files.isDirectory(jarDir)) {
                continue;
            }
            List<Path> jarFiles;
            try (Stream<Path> children = Files.list(jarDir)) {
                jarFiles = children.filter(f -> !Files.isDirectory(f) && f.getFileName().toString().endsWith(".jar")).toList();
            }
            catch (IOException | UncheckedIOException e) {
                LOG.debug(e);
                continue;
            }
            for (Path jarFile : jarFiles) {
                String jarFileName = jarFile.getFileName().toString();
                if (jarFileName.equals("alt-rt.jar") || jarFileName.equals("alt-string.jar")) {
                    continue;  // filter out alternative implementations
                }
                String canonicalPath = getCanonicalPath(jarFile);
                if (canonicalPath == null || !pathFilter.add(canonicalPath)) {
                    continue;  // filter out duplicate (symbolically linked) .jar files commonly found in OS X JDK distributions
                }
                rootFiles.add(jarFile);
            }
        }

        Path classesZip = home.resolve("lib/classes.zip");
        if (Files.isRegularFile(classesZip)) {
            rootFiles.add(classesZip);
        }

        Path classesDir = home.resolve("classes");
        if (rootFiles.isEmpty() && Files.isDirectory(classesDir)) {
            rootFiles.add(classesDir);
        }

        return rootFiles;
    }

    @Nullable
    private static String getCanonicalPath(Path file) {
        try {
            return file.toRealPath().toString();
        }
        catch (IOException e) {
            return null;
        }
    }

    /**
     * An entry of an archive - the archive is opened to find it.
     */
    @Nullable
    private static String findInArchive(Path archive, String relativePath) {
        if (!Files.isRegularFile(archive)) {
            return null;
        }
        String rootUrl = ArchiveVfsUtil.getArchiveRootUrl(archive);
        VirtualFile entry = rootUrl == null ? null : VirtualFileManager.getInstance().findFileByUrl(rootUrl + relativePath);
        return entry == null ? null : entry.getUrl();
    }

    @Nullable
    private static String findDocs(Path home, String relativePath) {
        Path docs = home.resolve(relativePath);
        return Files.isDirectory(docs) ? VirtualFileManager.getInstance().getUrl(docs) : null;
    }

    @Override
    public boolean isRootTypeApplicable(String type) {
        return ourRootTypeIds.contains(type);
    }
}
