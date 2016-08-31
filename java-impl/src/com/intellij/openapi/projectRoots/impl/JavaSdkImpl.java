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
package com.intellij.openapi.projectRoots.impl;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Icon;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.java.JavaIcons;
import org.mustbe.consulo.java.library.jimage.JImageFileType;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.highlighter.JarArchiveFileType;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import consulo.roots.types.BinariesOrderRootType;
import consulo.roots.types.DocumentationOrderRootType;
import consulo.roots.types.SourcesOrderRootType;
import consulo.vfs.util.ArchiveVfsUtil;

/**
 * @author Eugene Zhuravlev
 * @since Sep 17, 2004
 */
public class JavaSdkImpl extends JavaSdk
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.JavaSdkImpl");
	// do not use javaw.exe for Windows because of issues with encoding
	@NonNls
	private static final String VM_EXE_NAME = "java";
	@NonNls
	private final Pattern myVersionStringPattern = Pattern.compile("^(.*)java version \"([1234567890_.]*)\"(.*)$");
	@NonNls
	private static final String JAVA_VERSION_PREFIX = "java version ";
	@NonNls
	private static final String OPENJDK_VERSION_PREFIX = "openjdk version ";
	public static final DataKey<Boolean> KEY = DataKey.create("JavaSdk");

	public JavaSdkImpl()
	{
		super("JDK");
	}

	@NotNull
	@Override
	public String getPresentableName()
	{
		return ProjectBundle.message("sdk.java.name");
	}

	@Override
	public Icon getIcon()
	{
		return JavaIcons.Java;
	}

	@NotNull
	@Override
	public String getHelpTopic()
	{
		return "reference.project.structure.sdk.java";
	}

	@NonNls
	@Override
	@Nullable
	public String getDefaultDocumentationUrl(@NotNull final Sdk sdk)
	{
		final JavaSdkVersion version = getVersion(sdk);
		if(version == JavaSdkVersion.JDK_1_5)
		{
			return "http://docs.oracle.com/javase/1.5.0/docs/api/";
		}
		if(version == JavaSdkVersion.JDK_1_6)
		{
			return "http://docs.oracle.com/javase/6/docs/api/";
		}
		if(version == JavaSdkVersion.JDK_1_7)
		{
			return "http://docs.oracle.com/javase/7/docs/api/";
		}
		if(version == JavaSdkVersion.JDK_1_8)
		{
			return "http://download.java.net/jdk8/docs/api/";
		}
		if(version == JavaSdkVersion.JDK_1_9)
		{
			return "http://download.java.net/jdk9/docs/api/";
		}
		return null;
	}

	@Override
	@SuppressWarnings({"HardCodedStringLiteral"})
	public String getBinPath(Sdk sdk)
	{
		return getConvertedHomePath(sdk) + "bin";
	}

	@Override
	@NonNls
	public String getToolsPath(Sdk sdk)
	{
		final String versionString = sdk.getVersionString();
		final boolean isJdk1_x = versionString != null && (versionString.contains("1.0") || versionString.contains("1.1"));
		return getConvertedHomePath(sdk) + "lib" + File.separator + (isJdk1_x ? "classes.zip" : "tools.jar");
	}

	@Override
	public void setupCommandLine(@NotNull GeneralCommandLine commandLine, @NotNull Sdk sdk)
	{
		commandLine.setExePath(getBinPath(sdk) + File.separator + VM_EXE_NAME);
	}

	private static String getConvertedHomePath(Sdk sdk)
	{
		String homePath = sdk.getHomePath();
		assert homePath != null : sdk;
		String path = FileUtil.toSystemDependentName(homePath);
		if(!path.endsWith(File.separator))
		{
			path += File.separator;
		}
		return path;
	}

	@NotNull
	@Override
	public Collection<String> suggestHomePaths()
	{
		List<String> list = new SmartList<String>();
		if(SystemInfo.isMac)
		{
			list.add("/System/Library/Frameworks/JavaVM.framework/Versions");
			list.add("/usr/libexec/java_home");
		}
		else if(SystemInfo.isSolaris)
		{
			list.add("/usr/jdk");
		}
		else if(SystemInfo.isLinux)
		{
			list.add("/usr/java");
			list.add("/opt/java");
			list.add("/usr/lib/jvm");
		}
		else if(SystemInfo.isWindows)
		{
			collectJavaPaths(list, "ProgramFiles");
			collectJavaPaths(list, "ProgramFiles(x86)");
			ContainerUtil.addIfNotNull(list, System.getProperty("java.home"));
		}

		return list;
	}

	private static void collectJavaPaths(List<String> list, String env)
	{
		String programFiles = System.getenv(env);
		if(programFiles != null)
		{
			File temp = new File(programFiles, "Java");
			File[] files = temp.listFiles();
			if(files != null)
			{
				for(File file : files)
				{
					list.add(file.getPath());
				}
			}
		}
	}

	@Override
	public boolean canCreatePredefinedSdks()
	{
		return true;
	}

	@Override
	public FileChooserDescriptor getHomeChooserDescriptor()
	{
		FileChooserDescriptor descriptor = super.getHomeChooserDescriptor();
		descriptor.putUserData(KEY, Boolean.TRUE);
		return descriptor;
	}

	@NonNls
	public static final String MAC_HOME_PATH = "/Home";

	@Override
	public String adjustSelectedSdkHome(String homePath)
	{
		if(SystemInfo.isMac)
		{
			File home = new File(homePath, MAC_HOME_PATH);
			if(home.exists())
			{
				return home.getPath();
			}

			home = new File(new File(homePath, "Contents"), "Home");
			if(home.exists())
			{
				return home.getPath();
			}
		}

		return homePath;
	}

	@Override
	public boolean isValidSdkHome(String path)
	{
		return JavaSdkTypeUtil.checkForJdk(new File(path));
	}

	@Override
	public String suggestSdkName(String currentSdkName, String sdkHome)
	{
		final String suggestedName;
		if(currentSdkName != null && !currentSdkName.isEmpty())
		{
			final Matcher matcher = myVersionStringPattern.matcher(currentSdkName);
			final boolean replaceNameWithVersion = matcher.matches();
			if(replaceNameWithVersion)
			{
				// user did not change name -> set it automatically
				final String versionString = getVersionString(sdkHome);
				suggestedName = versionString == null ? currentSdkName : matcher.replaceFirst("$1" + versionString + "$3");
			}
			else
			{
				suggestedName = currentSdkName;
			}
		}
		else
		{
			String versionString = getVersionString(sdkHome);
			suggestedName = versionString == null ? ProjectBundle.message("sdk.java.unknown.name") : getVersionNumber(versionString);
		}
		return suggestedName;
	}

	@NotNull
	private static String getVersionNumber(@NotNull String versionString)
	{
		if(versionString.startsWith(JAVA_VERSION_PREFIX) || versionString.startsWith(OPENJDK_VERSION_PREFIX))
		{
			boolean openJdk = versionString.startsWith(OPENJDK_VERSION_PREFIX);
			versionString = versionString.substring(openJdk ? OPENJDK_VERSION_PREFIX.length() : JAVA_VERSION_PREFIX.length());
			if(versionString.startsWith("\"") && versionString.endsWith("\""))
			{
				versionString = versionString.substring(1, versionString.length() - 1);
			}
			int dotIdx = versionString.indexOf('.');
			if(dotIdx > 0)
			{
				try
				{
					int major = Integer.parseInt(versionString.substring(0, dotIdx));
					int minorDot = versionString.indexOf('.', dotIdx + 1);
					if(minorDot > 0)
					{
						int minor = Integer.parseInt(versionString.substring(dotIdx + 1, minorDot));
						versionString = major + "." + minor;
					}
				}
				catch(NumberFormatException e)
				{
					// Do nothing. Use original version string if failed to parse according to major.minor pattern.
				}
			}
		}
		return versionString;
	}

	@Override
	@SuppressWarnings({"HardCodedStringLiteral"})
	public void setupSdkPaths(Sdk sdk)
	{
		final File jdkHome = new File(sdk.getHomePath());
		List<VirtualFile> classes = findClasses(jdkHome, false);
		VirtualFile sources = findSources(jdkHome);
		VirtualFile docs = findDocs(jdkHome, "docs/api");

		final SdkModificator sdkModificator = sdk.getSdkModificator();
		final Set<VirtualFile> previousRoots = new LinkedHashSet<VirtualFile>(Arrays.asList(sdkModificator.getRoots(BinariesOrderRootType
				.getInstance())));
		sdkModificator.removeRoots(BinariesOrderRootType.getInstance());
		previousRoots.removeAll(new HashSet<VirtualFile>(classes));
		for(VirtualFile aClass : classes)
		{
			sdkModificator.addRoot(aClass, BinariesOrderRootType.getInstance());
		}
		for(VirtualFile root : previousRoots)
		{
			sdkModificator.addRoot(root, BinariesOrderRootType.getInstance());
		}
		if(sources != null)
		{
			sdkModificator.addRoot(sources, SourcesOrderRootType.getInstance());
		}
		addJavaFxSources(jdkHome, sdkModificator);

		File modulesDir = new File(jdkHome, "lib/modules");
		if(modulesDir.exists() && modulesDir.isDirectory())
		{
			for(File file : modulesDir.listFiles())
			{
				VirtualFile maybeJImageFile = LocalFileSystem.getInstance().findFileByIoFile(file);
				if(maybeJImageFile == null || maybeJImageFile.getFileType() != JImageFileType.INSTANCE)
				{
					continue;
				}
				sdkModificator.addRoot(maybeJImageFile, BinariesOrderRootType.getInstance());
			}
		}

		File jmodsFile = new File(jdkHome, "jmods");
		if(jmodsFile.exists() && jmodsFile.isDirectory())
		{
			VirtualFile javaBaseModule = LocalFileSystem.getInstance().findFileByIoFile(new File(jmodsFile, "java.base.jmod"));
			if(javaBaseModule != null)
			{
				sdkModificator.addRoot(javaBaseModule, BinariesOrderRootType.getInstance());
			}
		}

		if(docs != null)
		{
			sdkModificator.addRoot(docs, DocumentationOrderRootType.getInstance());
		}
		else if(SystemInfo.isMac)
		{
			VirtualFile commonDocs = findDocs(jdkHome, "docs");
			if(commonDocs == null)
			{
				commonDocs = findInJar(new File(jdkHome, "docs.jar"), "doc/api");
				if(commonDocs == null)
				{
					commonDocs = findInJar(new File(jdkHome, "docs.jar"), "docs/api");
				}
			}
			if(commonDocs != null)
			{
				sdkModificator.addRoot(commonDocs, DocumentationOrderRootType.getInstance());
			}

			VirtualFile appleDocs = findDocs(jdkHome, "appledocs");
			if(appleDocs == null)
			{
				appleDocs = findInJar(new File(jdkHome, "appledocs.jar"), "appledoc/api");
			}
			if(appleDocs != null)
			{
				sdkModificator.addRoot(appleDocs, DocumentationOrderRootType.getInstance());
			}

			if(commonDocs == null && appleDocs == null && sources == null)
			{
				String url = getDefaultDocumentationUrl(sdk);
				if(url != null)
				{
					sdkModificator.addRoot(VirtualFileManager.getInstance().findFileByUrl(url), DocumentationOrderRootType.getInstance());
				}
			}
		}
		attachJdkAnnotations(sdkModificator);
		sdkModificator.commitChanges();
	}

	public static boolean attachJdkAnnotations(@NotNull SdkModificator modificator)
	{
		IdeaPluginDescriptor plugin = PluginManager.getPlugin(((PluginClassLoader) JavaSdkImpl.class.getClassLoader()).getPluginId());
		assert plugin != null;

		File javaPluginPath = plugin.getPath();

		String absolutePath = new File(javaPluginPath, "lib/jdk-annotations.jar").getAbsolutePath();

		VirtualFile jarFile = JarArchiveFileType.INSTANCE.getFileSystem().findLocalVirtualFileByPath(absolutePath);

		if(jarFile == null)
		{
			LOG.error("jdk annotations not found in: " + absolutePath);
			return false;
		}

		OrderRootType annoType = AnnotationOrderRootType.getInstance();
		modificator.removeRoot(jarFile, annoType);
		modificator.addRoot(jarFile, annoType);
		return true;
	}

	private final Map<String, String> myCachedVersionStrings = new HashMap<String, String>();

	@Override
	public final String getVersionString(final String sdkHome)
	{
		if(myCachedVersionStrings.containsKey(sdkHome))
		{
			return myCachedVersionStrings.get(sdkHome);
		}
		String versionString = getJdkVersion(sdkHome);
		if(versionString != null && versionString.isEmpty())
		{
			versionString = null;
		}

		if(versionString != null)
		{
			myCachedVersionStrings.put(sdkHome, versionString);
		}

		return versionString;
	}

	@Override
	public int compareTo(@NotNull String versionString, @NotNull String versionNumber)
	{
		return getVersionNumber(versionString).compareTo(versionNumber);
	}

	@Override
	public JavaSdkVersion getVersion(@NotNull Sdk sdk)
	{
		return getVersion1(sdk);
	}

	private static JavaSdkVersion getVersion1(Sdk sdk)
	{
		String version = sdk.getVersionString();
		if(version == null)
		{
			return null;
		}
		return JdkVersionUtil.getVersion(version);
	}

	@Override
	@Nullable
	public JavaSdkVersion getVersion(@NotNull String versionString)
	{
		return JdkVersionUtil.getVersion(versionString);
	}

	@Override
	public boolean isOfVersionOrHigher(@NotNull Sdk sdk, @NotNull JavaSdkVersion version)
	{
		JavaSdkVersion sdkVersion = getVersion(sdk);
		return sdkVersion != null && sdkVersion.isAtLeast(version);
	}

	@Override
	public Sdk createJdk(@NotNull String jdkName, @NotNull String home, boolean isJre)
	{
		SdkImpl jdk = new SdkImpl(jdkName, this);
		SdkModificator sdkModificator = jdk.getSdkModificator();

		String path = home.replace(File.separatorChar, '/');
		sdkModificator.setHomePath(path);
		sdkModificator.setVersionString(jdkName); // must be set after home path, otherwise setting home path clears the version string

		File jdkHomeFile = new File(home);
		addClasses(jdkHomeFile, sdkModificator, isJre);
		addSources(jdkHomeFile, sdkModificator);
		addJavaFxSources(jdkHomeFile, sdkModificator);
		addDocs(jdkHomeFile, sdkModificator);
		sdkModificator.commitChanges();

		return jdk;
	}

	private static void addClasses(File file, SdkModificator sdkModificator, boolean isJre)
	{
		for(VirtualFile virtualFile : findClasses(file, isJre))
		{
			sdkModificator.addRoot(virtualFile, BinariesOrderRootType.getInstance());
		}
	}

	private static List<VirtualFile> findClasses(File file, boolean isJre)
	{
		List<VirtualFile> result = ContainerUtil.newArrayList();

		List<File> rootFiles = JavaSdkUtil.getJdkClassesRoots(file, isJre);
		for(File child : rootFiles)
		{
			String url = VfsUtil.getUrlForLibraryRoot(child);
			VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
			if(vFile != null)
			{
				result.add(vFile);
			}
		}

		return result;
	}

	private static void addJavaFxSources(File file, SdkModificator sdkModificator)
	{
		VirtualFile fileByIoFile = LocalFileSystem.getInstance().findFileByIoFile(new File(file, "javafx-src.zip"));
		if(fileByIoFile == null)
		{
			return;
		}
		VirtualFile archiveRootForLocalFile = ArchiveVfsUtil.getArchiveRootForLocalFile(fileByIoFile);
		if(archiveRootForLocalFile == null)
		{
			return;
		}
		sdkModificator.addRoot(archiveRootForLocalFile, SourcesOrderRootType.getInstance());
	}

	private static void addSources(File file, SdkModificator sdkModificator)
	{
		VirtualFile vFile = findSources(file);
		if(vFile != null)
		{
			sdkModificator.addRoot(vFile, SourcesOrderRootType.getInstance());
		}
	}

	@Nullable
	@SuppressWarnings({"HardCodedStringLiteral"})
	public static VirtualFile findSources(File file)
	{
		File srcDir = new File(file, "src");
		File jarFile = new File(file, "src.jar");
		if(!jarFile.exists())
		{
			jarFile = new File(file, "src.zip");
		}

		if(jarFile.exists())
		{
			VirtualFile vFile = findInJar(jarFile, "src");
			if(vFile != null)
			{
				return vFile;
			}
			// try 1.4 format
			vFile = findInJar(jarFile, "");
			return vFile;
		}
		else
		{
			if(!srcDir.exists() || !srcDir.isDirectory())
			{
				return null;
			}
			String path = srcDir.getAbsolutePath().replace(File.separatorChar, '/');
			return LocalFileSystem.getInstance().findFileByPath(path);
		}
	}

	@SuppressWarnings({"HardCodedStringLiteral"})
	private static void addDocs(File file, SdkModificator rootContainer)
	{
		VirtualFile vFile = findDocs(file, "docs/api");
		if(vFile != null)
		{
			rootContainer.addRoot(vFile, DocumentationOrderRootType.getInstance());
		}
	}

	@Nullable
	private static VirtualFile findInJar(File jarFile, String relativePath)
	{
		VirtualFile fileByIoFile = LocalFileSystem.getInstance().findFileByIoFile(jarFile);
		if(fileByIoFile == null)
		{
			return null;
		}
		VirtualFile archiveRootForLocalFile = ArchiveVfsUtil.getArchiveRootForLocalFile(fileByIoFile);
		return archiveRootForLocalFile == null ? null : archiveRootForLocalFile.findFileByRelativePath(relativePath);
	}

	@Nullable
	public static VirtualFile findDocs(File file, final String relativePath)
	{
		file = new File(file.getAbsolutePath() + File.separator + relativePath.replace('/', File.separatorChar));
		if(!file.exists() || !file.isDirectory())
		{
			return null;
		}
		String path = file.getAbsolutePath().replace(File.separatorChar, '/');
		return LocalFileSystem.getInstance().findFileByPath(path);
	}

	@Override
	public boolean isRootTypeApplicable(OrderRootType type)
	{
		return type == BinariesOrderRootType.getInstance() ||
				type == SourcesOrderRootType.getInstance() ||
				type == DocumentationOrderRootType.getInstance() ||
				type == AnnotationOrderRootType.getInstance();
	}
}
