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
import consulo.application.util.SystemInfo;
import consulo.container.plugin.PluginManager;
import consulo.content.OrderRootType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.base.DocumentationOrderRootType;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.java.language.fileTypes.JModFileType;
import consulo.java.language.impl.icon.JavaPsiImplIconGroup;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.ProjectBundle;
import consulo.ui.image.Image;
import consulo.util.collection.Sets;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.SystemProperties;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.virtualFileSystem.archive.ZipArchiveFileType;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  public DefaultJavaSdkTypeImpl() {
    super("JDK");
  }

  @Nonnull
  @Override
  public String getPresentableName() {
    return ProjectBundle.message("sdk.java.name");
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return JavaPsiImplIconGroup.java();
  }

  @Nonnull
  @Override
  public String getHelpTopic() {
    return "reference.project.structure.sdk.java";
  }

  @NonNls
  @Override
  @Nullable
  public String getDefaultDocumentationUrl(@Nonnull final Sdk sdk) {
    final JavaSdkVersion version = JavaSdkTypeUtil.getVersion(sdk);
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
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getBinPath(Sdk sdk) {
    return getConvertedHomePath(sdk) + "bin";
  }

  @Override
  @NonNls
  public String getToolsPath(Sdk sdk) {
    final String versionString = sdk.getVersionString();
    final boolean isJdk1_x = versionString != null && (versionString.contains("1.0") || versionString.contains("1.1"));
    return getConvertedHomePath(sdk) + "lib" + File.separator + (isJdk1_x ? "classes.zip" : "tools.jar");
  }

  @Override
  public void setupCommandLine(@Nonnull GeneralCommandLine commandLine, @Nonnull Sdk sdk) {
    commandLine.setExePath(getBinPath(sdk) + File.separator + VM_EXE_NAME);
  }

  private static String getConvertedHomePath(Sdk sdk) {
    String homePath = sdk.getHomePath();
    assert homePath != null : sdk;
    String path = FileUtil.toSystemDependentName(homePath);
    if (!path.endsWith(File.separator)) {
      path += File.separator;
    }
    return path;
  }

  /**
   * @implNote do not chech JAVA_HOME, {@link #getEnviromentVariables(Platform)}
   */
  @Nonnull
  @Override
  public Collection<String> suggestHomePaths() {
    List<String> list = new ArrayList<>();
    if (SystemInfo.isMac) {
      collectJavaPathsAtMac(list, "/Library/Java/JavaVirtualMachines");
      collectJavaPathsAtMac(list, "/System/Library/Java/JavaVirtualMachines");
      list.add("/usr/libexec/java_home");
    }
    else if (SystemInfo.isSolaris) {
      list.add("/usr/jdk");
    }
    else if (SystemInfo.isLinux) {
      list.add("/usr/java");
      list.add("/opt/java");
      list.add("/usr/lib/jvm");
    }
    else if (SystemInfo.isWindows) {
      collectJavaPathsAtWindows(list, "ProgramFiles");
      collectJavaPathsAtWindows(list, "ProgramFiles(x86)");
    }

    // JDKs in SDKMan located at $HOME/.sdkman/candidates/java/
    File sdkmanJavaDir = new File(SystemProperties.getUserHome(), "/.sdkman/candidates/java/");
    if (sdkmanJavaDir.exists()) {
      collectJavaPathsAtSdkman(list, sdkmanJavaDir);
    }

    return list;
  }

  @Nonnull
  @Override
  public Set<String> getEnviromentVariables(@Nonnull Platform platform) {
    return Set.of("JAVA_HOME");
  }

  private void collectJavaPathsAtSdkman(List<String> list, File sdkmanJavaDir) {
    File[] files = sdkmanJavaDir.listFiles();
    if (files != null) {
      for (File dir : files) {
        if (!dir.getName().equals("current")) // skip "current" directory, because it's link
        {
          list.add(dir.getAbsolutePath());
        }
      }
    }
  }

  private static void collectJavaPathsAtMac(List<String> list, String path) {
    File dir = new File(path);
    if (dir.exists()) {
      File[] files = dir.listFiles();
      if (files != null) {
        for (File file : files) {
          list.add(file.getPath());
        }
      }
    }
  }

  private static void collectJavaPathsAtWindows(List<String> list, String env) {
    String programFiles = Platform.current().os().getEnvironmentVariable(env);
    if (programFiles != null) {
      File temp = new File(programFiles, "Java");
      File[] files = temp.listFiles();
      if (files != null) {
        for (File file : files) {
          list.add(file.getPath());
        }
      }
    }
  }

  @Override
  public boolean canCreatePredefinedSdks() {
    return true;
  }

  @Override
  public FileChooserDescriptor getHomeChooserDescriptor() {
    FileChooserDescriptor descriptor = super.getHomeChooserDescriptor();
    descriptor.putUserData(KEY, Boolean.TRUE);
    return descriptor;
  }

  @NonNls
  public static final String MAC_HOME_PATH = "/Home";

  @Override
  public String adjustSelectedSdkHome(String homePath) {
    if (SystemInfo.isMac) {
      File home = new File(homePath, MAC_HOME_PATH);
      if (home.exists()) {
        return home.getPath();
      }

      home = new File(new File(homePath, "Contents"), "Home");
      if (home.exists()) {
        return home.getPath();
      }
    }

    return homePath;
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return OwnJdkUtil.checkForJdk(new File(path));
  }

  @Override
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    final String suggestedName;
    if (currentSdkName != null && !currentSdkName.isEmpty()) {
      final Matcher matcher = myVersionStringPattern.matcher(currentSdkName);
      final boolean replaceNameWithVersion = matcher.matches();
      if (replaceNameWithVersion) {
        // user did not change name -> set it automatically
        final String versionString = getVersionString(sdkHome);
        suggestedName = versionString == null ? currentSdkName : matcher.replaceFirst("$1" + versionString + "$3");
      }
      else {
        suggestedName = currentSdkName;
      }
    }
    else {
      String versionString = getVersionString(sdkHome);
      suggestedName = versionString == null ? ProjectBundle.message("sdk.java.unknown.name") : getVersionNumber(versionString);
    }
    return suggestedName;
  }

  @Nonnull
  private static String getVersionNumber(@Nonnull String versionString) {
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

  @Override
  public void setupSdkPaths(Sdk sdk) {
    final File jdkHome = new File(sdk.getHomePath());

    final SdkModificator sdkModificator = sdk.getSdkModificator();

    boolean isModuleJdk = false;
    File jmodsDirectory = new File(jdkHome, "jmods");
    if (jmodsDirectory.exists() && jmodsDirectory.isDirectory()) {
      isModuleJdk = true;
      File[] files = jmodsDirectory.listFiles();
      for (File file : files) {
        VirtualFile jmodFile = JModFileType.INSTANCE.getFileSystem().findLocalVirtualFileByPath(file.getPath());
        if (jmodFile != null) {
          VirtualFile classesDir = jmodFile.findChild("classes");
          if (classesDir != null) {
            sdkModificator.addRoot(classesDir, BinariesOrderRootType.getInstance());
          }
        }
      }
    }
    else if (OwnJdkUtil.isModularRuntime(jdkHome)) {
      String jrtBaseUrl = JrtFileSystem.PROTOCOL_PREFIX + getPath(jdkHome) + JrtFileSystem.SEPARATOR;
      List<String> modules = readModulesFromReleaseFile(jdkHome);
      if (modules != null) {
        for (String module : modules) {
          sdkModificator.addRoot(jrtBaseUrl + module, BinariesOrderRootType.getInstance());
        }
      }
      else {
        VirtualFile jrt = VirtualFileManager.getInstance().findFileByUrl(jrtBaseUrl);
        if (jrt != null) {
          for (VirtualFile virtualFile : jrt.getChildren()) {
            sdkModificator.addRoot(virtualFile.getUrl(), BinariesOrderRootType.getInstance());
          }
        }
      }
    }
    else {
      addJavaFxSources(jdkHome, sdkModificator);

      for (VirtualFile aClass : findClasses(jdkHome, false)) {
        sdkModificator.addRoot(aClass, BinariesOrderRootType.getInstance());
      }
    }

    boolean noSources = true;
    VirtualFile srcZipFile = ZipArchiveFileType.INSTANCE.getFileSystem().findLocalVirtualFileByPath(new File(jdkHome, "src.zip").getPath());
    if (srcZipFile == null) {
      srcZipFile = ZipArchiveFileType.INSTANCE.getFileSystem().findLocalVirtualFileByPath(new File(jdkHome, "lib/src.zip").getPath());
    }

    if (srcZipFile != null) {
      noSources = false;

      if (isModuleJdk) {
        VirtualFile[] children = srcZipFile.getChildren();
        for (VirtualFile child : children) {
          if (child.isDirectory()) {
            sdkModificator.addRoot(child, SourcesOrderRootType.getInstance());
          }
        }
      }
      else {
        sdkModificator.addRoot(srcZipFile, SourcesOrderRootType.getInstance());
      }
    }

    VirtualFile docs = findDocs(jdkHome, "docs/api");
    if (docs != null) {
      sdkModificator.addRoot(docs, DocumentationOrderRootType.getInstance());
    }
    else if (SystemInfo.isMac) {
      VirtualFile commonDocs = findDocs(jdkHome, "docs");
      if (commonDocs == null) {
        commonDocs = findInJar(new File(jdkHome, "docs.jar"), "doc/api");
        if (commonDocs == null) {
          commonDocs = findInJar(new File(jdkHome, "docs.jar"), "docs/api");
        }
      }
      if (commonDocs != null) {
        sdkModificator.addRoot(commonDocs, DocumentationOrderRootType.getInstance());
      }

      VirtualFile appleDocs = findDocs(jdkHome, "appledocs");
      if (appleDocs == null) {
        appleDocs = findInJar(new File(jdkHome, "appledocs.jar"), "appledoc/api");
      }
      if (appleDocs != null) {
        sdkModificator.addRoot(appleDocs, DocumentationOrderRootType.getInstance());
      }

      if (commonDocs == null && appleDocs == null && noSources) {
        String url = getDefaultDocumentationUrl(sdk);
        if (url != null) {
          sdkModificator.addRoot(VirtualFileManager.getInstance().findFileByUrl(url), DocumentationOrderRootType.getInstance());
        }
      }
    }
    attachJdkAnnotations(sdkModificator);
    sdkModificator.commitChanges();
  }

  private static String getPath(File jarFile) {
    return FileUtil.toSystemIndependentName(jarFile.getAbsolutePath());
  }

  /**
   * Tries to load the list of modules in the JDK from the 'release' file. Returns null if the 'release' file is not there
   * or doesn't contain the expected information.
   */
  @Nullable
  private static List<String> readModulesFromReleaseFile(File jrtBaseDir) {
    File releaseFile = new File(jrtBaseDir, "release");
    if (releaseFile.isFile()) {
      try (FileInputStream stream = new FileInputStream(releaseFile)) {
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

  public static boolean attachJdkAnnotations(@Nonnull SdkModificator modificator) {
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

    OrderRootType annoType = AnnotationOrderRootType.getInstance();
    modificator.removeRoot(jarFile, annoType);
    modificator.addRoot(jarFile, annoType);
    return true;
  }

  @Override
  public final String getVersionString(final String sdkHome) {
    OwnJdkVersionDetector.JdkVersionInfo jdkInfo = OwnJdkVersionDetector.getInstance().detectJdkVersionInfo(sdkHome);
    return jdkInfo != null ? OwnJdkVersionDetector.formatVersionString(jdkInfo.version) : null;
  }

  private static List<VirtualFile> findClasses(File file, boolean isJre) {
    List<VirtualFile> result = new ArrayList<>();

    List<File> rootFiles = getJdkClassesRoots(file, isJre);
    for (File child : rootFiles) {
      String url = VirtualFileUtil.getUrlForLibraryRoot(child);
      VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
      if (vFile != null) {
        result.add(vFile);
      }
    }

    return result;
  }

  public static List<File> getJdkClassesRoots(File home, boolean isJre) {
    FileFilter jarFileFilter = f -> !f.isDirectory() && f.getName().endsWith(".jar");

    File[] jarDirs;
    if (SystemInfo.isMac && !home.getName().startsWith("mockJDK")) {
      File openJdkRtJar = new File(home, "jre/lib/rt.jar");
      if (openJdkRtJar.exists() && !openJdkRtJar.isDirectory()) {
        File libDir = new File(home, "lib");
        File classesDir = openJdkRtJar.getParentFile();
        File libExtDir = new File(openJdkRtJar.getParentFile(), "ext");
        File libEndorsedDir = new File(libDir, "endorsed");
        jarDirs = new File[]{
          libEndorsedDir,
          libDir,
          classesDir,
          libExtDir
        };
      }
      else {
        File libDir = new File(home, "lib");
        File classesDir = new File(home, "../Classes");
        File libExtDir = new File(libDir, "ext");
        File libEndorsedDir = new File(libDir, "endorsed");
        jarDirs = new File[]{
          libEndorsedDir,
          libDir,
          classesDir,
          libExtDir
        };
      }
    }
    else {
      File libDir = isJre ? new File(home, "lib") : new File(home, "jre/lib");
      File libExtDir = new File(libDir, "ext");
      File libEndorsedDir = new File(libDir, "endorsed");
      jarDirs = new File[]{
        libEndorsedDir,
        libDir,
        libExtDir
      };
    }

    Set<String> pathFilter = Sets.newHashSet(FileUtil.PATH_HASHING_STRATEGY);
    List<File> rootFiles = new ArrayList<>();
    for (File jarDir : jarDirs) {
      if (jarDir != null && jarDir.isDirectory()) {
        File[] jarFiles = jarDir.listFiles(jarFileFilter);
        for (File jarFile : jarFiles) {
          String jarFileName = jarFile.getName();
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
    }

    File classesZip = new File(home, "lib/classes.zip");
    if (classesZip.isFile()) {
      rootFiles.add(classesZip);
    }

    File classesDir = new File(home, "classes");
    if (rootFiles.isEmpty() && classesDir.isDirectory()) {
      rootFiles.add(classesDir);
    }

    return rootFiles;
  }


  @Nullable
  private static String getCanonicalPath(File file) {
    try {
      return file.getCanonicalPath();
    }
    catch (IOException e) {
      return null;
    }
  }

  private static void addJavaFxSources(File file, SdkModificator sdkModificator) {
    VirtualFile fileByIoFile = LocalFileSystem.getInstance().findFileByIoFile(new File(file, "javafx-src.zip"));
    if (fileByIoFile == null) {
      return;
    }
    VirtualFile archiveRootForLocalFile = ArchiveVfsUtil.getArchiveRootForLocalFile(fileByIoFile);
    if (archiveRootForLocalFile == null) {
      return;
    }
    sdkModificator.addRoot(archiveRootForLocalFile, SourcesOrderRootType.getInstance());
  }

  @Nullable
  private static VirtualFile findInJar(File jarFile, String relativePath) {
    VirtualFile fileByIoFile = LocalFileSystem.getInstance().findFileByIoFile(jarFile);
    if (fileByIoFile == null) {
      return null;
    }
    VirtualFile archiveRootForLocalFile = ArchiveVfsUtil.getArchiveRootForLocalFile(fileByIoFile);
    return archiveRootForLocalFile == null ? null : archiveRootForLocalFile.findFileByRelativePath(relativePath);
  }

  @Nullable
  public static VirtualFile findDocs(File file, final String relativePath) {
    file = new File(file.getAbsolutePath() + File.separator + relativePath.replace('/', File.separatorChar));
    if (!file.exists() || !file.isDirectory()) {
      return null;
    }
    String path = file.getAbsolutePath().replace(File.separatorChar, '/');
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  @Override
  public boolean isRootTypeApplicable(OrderRootType type) {
    return type == BinariesOrderRootType.getInstance() ||
      type == SourcesOrderRootType.getInstance() ||
      type == DocumentationOrderRootType.getInstance() ||
      type == AnnotationOrderRootType.getInstance();
  }
}
