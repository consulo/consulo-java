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
package com.intellij.compiler.impl.javaCompiler.javac;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import org.jetbrains.annotations.NonNls;
import com.intellij.compiler.CompilerIOUtil;
import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.ModuleChunk;
import com.intellij.compiler.impl.javaCompiler.ExternalCompiler;
import com.intellij.compiler.impl.javaCompiler.JavaCompilerConfiguration;
import com.intellij.compiler.impl.javaCompiler.annotationProcessing.AnnotationProcessingConfiguration;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.OwnJdkVersionDetector;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.projectRoots.impl.MockSdkWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.PathsList;
import consulo.annotation.access.RequiredReadAction;
import consulo.compiler.roots.CompilerPathsImpl;
import consulo.java.compiler.JavaCompilerBundle;
import consulo.java.compiler.JavaCompilerUtil;
import consulo.java.fileTypes.JModFileType;
import consulo.java.module.extension.JavaModuleExtension;
import consulo.java.rt.JavaRtClassNames;
import consulo.roots.impl.ProductionContentFolderTypeProvider;
import consulo.vfs.util.ArchiveVfsUtil;

public class JavacCompiler extends ExternalCompiler
{
	@NonNls
	public static final String TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME = "tests.external.compiler.home";

	private static final Logger LOG = Logger.getInstance(JavacCompiler.class);
	private final Project myProject;
	private final List<File> myTempFiles = new ArrayList<>();
	@NonNls
	private static final String JAVAC_MAIN_CLASS_OLD = "sun.tools.javac.Main";
	@NonNls
	public static final String JAVAC_MAIN_CLASS = "com.sun.tools.javac.Main";
	private boolean myAnnotationProcessorMode = false;

	public JavacCompiler(Project project)
	{
		myProject = project;
	}

	public boolean isAnnotationProcessorMode()
	{
		return myAnnotationProcessorMode;
	}

	/**
	 * @param annotationProcessorMode
	 * @return previous value
	 */
	public boolean setAnnotationProcessorMode(boolean annotationProcessorMode)
	{
		final boolean oldValue = myAnnotationProcessorMode;
		myAnnotationProcessorMode = annotationProcessorMode;
		return oldValue;
	}

	@Override
	public boolean checkCompiler(final CompileScope scope)
	{
		final Module[] modules = scope.getAffectedModules();
		final Set<Sdk> checkedJdks = new HashSet<Sdk>();
		for(final Module module : modules)
		{
			JavaModuleExtension extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
			if(extension == null)
			{
				continue;
			}
			final Sdk javaSdk = JavaCompilerUtil.getSdkForCompilation(module);
			if(javaSdk == null)
			{
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.jdk.is.not.set.for.module", module.getName()), JavaCompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
				return false;
			}

			if(checkedJdks.contains(javaSdk))
			{
				continue;
			}

			checkedJdks.add(javaSdk);
			final SdkTypeId sdkType = javaSdk.getSdkType();
			assert sdkType instanceof JavaSdk;

			final VirtualFile homeDirectory = javaSdk.getHomeDirectory();
			if(homeDirectory == null)
			{
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.jdk.home.missing", javaSdk.getHomePath(), javaSdk.getName()), JavaCompilerBundle.message("compiler" + ".javac" + ".name"), Messages.getErrorIcon());
				return false;
			}
			final String versionString = javaSdk.getVersionString();
			if(versionString == null)
			{
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.unknown.jdk.version", javaSdk.getName()), JavaCompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
				return false;
			}

			JavaSdkVersion javaSdkVersion = JavaSdkVersion.fromVersionString(versionString);
			if(javaSdkVersion == null)
			{
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.unknown.jdk.version", javaSdk.getName()), JavaCompilerBundle.message("compiler.javac.name"), Messages
						.getErrorIcon());
				return false;
			}

			if(!javaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_9))
			{
				final String toolsJarPath = ((JavaSdk) sdkType).getToolsPath(javaSdk);
				if(toolsJarPath == null)
				{
					Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.tools.jar.missing", javaSdk.getName()), JavaCompilerBundle.message("compiler.javac.name"), Messages
							.getErrorIcon());
					return false;
				}
			}

			if(!javaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_5))
			{
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.target.jdk.not.supported"), JavaCompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
				return false;
			}
		}

		return true;
	}

	@Override
	@Nonnull
	public String getPresentableName()
	{
		return JavaCompilerBundle.message("compiler.javac.name");
	}

	@Override
	public OutputParser createErrorParser(@Nonnull final String outputDir, Process process)
	{
		return new JavacOutputParser(myProject);
	}

	@Override
	public OutputParser createOutputParser(@Nonnull final String outputDir)
	{
		return null;
	}

	@Override
	@Nonnull
	public GeneralCommandLine createStartupCommand(final ModuleChunk chunk, final CompileContext context, final String outputPath) throws IOException, IllegalArgumentException
	{
		return ReadAction.compute(() -> createStartupCommand(chunk, outputPath, context, JavacCompilerConfiguration.getInstance(myProject), JavaCompilerConfiguration.getInstance(myProject).isAnnotationProcessorsEnabled()));
	}

	@Nonnull
	@RequiredReadAction
	private GeneralCommandLine createStartupCommand(final ModuleChunk chunk, final String outputPath, final CompileContext compileContext, JpsJavaCompilerOptions javacOptions, final boolean annotationProcessorsEnabled) throws IOException
	{
		final Sdk jdk = getJdkForStartupCommand(chunk);
		final String versionString = jdk.getVersionString();
		JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
		if(versionString == null || version == null || !(jdk.getSdkType() instanceof JavaSdk))
		{
			throw new IllegalArgumentException(JavaCompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()));
		}

		JavaSdk sdkType = (JavaSdk) jdk.getSdkType();

		if(!version.isAtLeast(JavaSdkVersion.JDK_1_9))
		{
			final String toolsJarPath = sdkType.getToolsPath(jdk);
			if(toolsJarPath == null)
			{
				throw new IllegalArgumentException(JavaCompilerBundle.message("javac.error.tools.jar.missing", jdk.getName()));
			}
		}

		GeneralCommandLine commandLine = new GeneralCommandLine();
		sdkType.setupCommandLine(commandLine, jdk);

		ParametersList parametersList = commandLine.getParametersList();

		if(version.isAtLeast(JavaSdkVersion.JDK_1_2))
		{
			parametersList.add("-Xmx" + javacOptions.MAXIMUM_HEAP_SIZE + "m");
		}
		else
		{
			parametersList.add("-mx" + javacOptions.MAXIMUM_HEAP_SIZE + "m");
		}

		final List<String> additionalOptions = addAdditionalSettings(parametersList, javacOptions, myAnnotationProcessorMode, version, chunk, annotationProcessorsEnabled);

		JavaCompilerUtil.addLocaleOptions(parametersList, false);

		parametersList.add("-classpath");

		if(version == JavaSdkVersion.JDK_1_0)
		{
			parametersList.add(sdkType.getToolsPath(jdk)); //  do not use JavacRunner for jdk 1.0
		}
		else if(version.isAtLeast(JavaSdkVersion.JDK_1_9))
		{
			parametersList.add(JavaSdkUtil.getJavaRtJarPath());
			parametersList.add(JavaRtClassNames.JAVAC_RUNNER);
			parametersList.add("\"" + versionString + "\"");
		}
		else
		{
			parametersList.add(sdkType.getToolsPath(jdk) + File.pathSeparator + JavaSdkUtil.getJavaRtJarPath());
			parametersList.add(JavaRtClassNames.JAVAC_RUNNER);
			parametersList.add("\"" + versionString + "\"");
		}

		if(version.isAtLeast(JavaSdkVersion.JDK_1_3))
		{
			parametersList.add(JAVAC_MAIN_CLASS);
		}
		else
		{
			parametersList.add(JAVAC_MAIN_CLASS_OLD);
		}

		addCommandLineOptions(compileContext, chunk, parametersList, outputPath, jdk, version, myTempFiles, true, true, myAnnotationProcessorMode);

		parametersList.addAll(additionalOptions);

		final List<VirtualFile> files = chunk.getFilesToCompile();

		if(version == JavaSdkVersion.JDK_1_0)
		{
			for(VirtualFile file : files)
			{
				String path = file.getPath();
				if(LOG.isDebugEnabled())
				{
					LOG.debug("Adding path for compilation " + path);
				}
				parametersList.add(CompilerUtil.quotePath(path));
			}
		}
		else
		{
			File sourcesFile = File.createTempFile("javac", ".tmp");
			sourcesFile.deleteOnExit();
			myTempFiles.add(sourcesFile);
			try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(sourcesFile))))
			{
				for(final VirtualFile file : files)
				{
					// Important: should use "/" slashes!
					// but not for JDK 1.5 - see SCR 36673
					final String path = version.isAtLeast(JavaSdkVersion.JDK_1_5) ? file.getPath().replace('/', File.separatorChar) : file.getPath();
					if(LOG.isDebugEnabled())
					{
						LOG.debug("Adding path for compilation " + path);
					}
					writer.println(version == JavaSdkVersion.JDK_1_1 ? path : CompilerUtil.quotePath(path));
				}
			}
			parametersList.add("@" + sourcesFile.getAbsolutePath());
		}
		return commandLine;
	}

	public static List<String> addAdditionalSettings(ParametersList parametersList, JpsJavaCompilerOptions javacOptions, boolean isAnnotationProcessing, JavaSdkVersion version, ModuleChunk chunk, boolean annotationProcessorsEnabled)
	{
		final List<String> additionalOptions = new ArrayList<>();
		StringTokenizer tokenizer = new StringTokenizer(new JavacSettingsBuilder(javacOptions).getOptionsString(chunk), " ");
		if(!version.isAtLeast(JavaSdkVersion.JDK_1_6))
		{
			isAnnotationProcessing = false; // makes no sense for these versions
			annotationProcessorsEnabled = false;
		}
		if(isAnnotationProcessing)
		{
			final AnnotationProcessingConfiguration config = JavaCompilerConfiguration.getInstance(chunk.getProject()).getAnnotationProcessingConfiguration(chunk.getModules()[0]);
			additionalOptions.add("-Xprefer:source");
			additionalOptions.add("-implicit:none");
			additionalOptions.add("-proc:only");
			if(!config.isObtainProcessorsFromClasspath())
			{
				final String processorPath = config.getProcessorPath();
				additionalOptions.add("-processorpath");
				additionalOptions.add(FileUtil.toSystemDependentName(processorPath));
			}
			final Set<String> processors = config.getProcessors();
			if(!processors.isEmpty())
			{
				additionalOptions.add("-processor");
				additionalOptions.add(StringUtil.join(processors, ","));
			}
			for(Map.Entry<String, String> entry : config.getProcessorOptions().entrySet())
			{
				additionalOptions.add("-A" + entry.getKey() + "=" + entry.getValue());
			}
		}
		else
		{
			if(annotationProcessorsEnabled)
			{
				// Unless explicitly specified by user, disable annotation processing by default for 'java compilation' mode
				// This is needed to suppress unwanted side-effects from auto-discovered processors from compilation classpath
				additionalOptions.add("-proc:none");
			}
		}

		while(tokenizer.hasMoreTokens())
		{
			@NonNls String token = tokenizer.nextToken();
			if(version == JavaSdkVersion.JDK_1_0 && "-deprecation".equals(token))
			{
				continue; // not supported for this version
			}
			if(!version.isAtLeast(JavaSdkVersion.JDK_1_5) && "-Xlint".equals(token))
			{
				continue; // not supported in these versions
			}
			if(isAnnotationProcessing)
			{
				if(token.startsWith("-proc:"))
				{
					continue;
				}
				if(token.startsWith("-implicit:"))
				{
					continue;
				}
			}
			else
			{ // compiling java
				if(annotationProcessorsEnabled)
				{
					// in this mode we have -proc:none already added above, so user's settings should be ignored
					if(token.startsWith("-proc:"))
					{
						continue;
					}
				}
			}
			if(token.startsWith("-J-"))
			{
				parametersList.add(token.substring("-J".length()));
			}
			else
			{
				additionalOptions.add(token);
			}
		}

		return additionalOptions;
	}

	@RequiredReadAction
	public static void addCommandLineOptions(CompileContext compileContext, ModuleChunk chunk, @NonNls ParametersList commandLine, String outputPath, Sdk jdk, JavaSdkVersion version, List<File> tempFiles, boolean addSourcePath, boolean useTempFile, boolean isAnnotationProcessingMode) throws IOException
	{
		LanguageLevel languageLevel = JavaCompilerUtil.getLanguageLevelForCompilation(chunk);
		boolean isJava9Version = isAtLeast(version, languageLevel, JavaSdkVersion.JDK_1_9);

		if(!isJava9Version)
		{
			JavaCompilerUtil.addSourceCommandLineSwitch(jdk, languageLevel, commandLine);
			JavaCompilerUtil.addTargetCommandLineSwitch(chunk, commandLine);
		}
		else
		{
			JavaModuleExtension<?> extension = ModuleUtilCore.getExtension(chunk.getModule(), JavaModuleExtension.class);
			if(extension != null)
			{
				String bytecodeVersion = extension.getBytecodeVersion();
				if(bytecodeVersion != null)
				{
					commandLine.add("--release");
					commandLine.add(bytecodeVersion);
				}
			}
		}

		commandLine.add("-verbose");

		final Set<VirtualFile> cp = JavaCompilerUtil.getCompilationClasspath(compileContext, chunk);
		final Set<VirtualFile> bootCp = filterModFiles(JavaCompilerUtil.getCompilationBootClasspath(compileContext, chunk));

		final String classPath;
		if(version == JavaSdkVersion.JDK_1_0 || version == JavaSdkVersion.JDK_1_1)
		{
			classPath = asString(bootCp) + File.pathSeparator + asString(cp);
		}
		else
		{
			classPath = asString(cp);
			if(version.isAtLeast(JavaSdkVersion.JDK_1_9))
			{
				// add bootpath if target is 1.8 or lower
				if(languageLevel != null && !languageLevel.isAtLeast(LanguageLevel.JDK_1_9) && !bootCp.isEmpty())
				{
					commandLine.add("-bootclasspath");
					addClassPathValue(jdk, version, commandLine, asString(bootCp), "javac_bootcp", tempFiles, useTempFile);
				}
			}
			else
			{
				commandLine.add("-bootclasspath");
				addClassPathValue(jdk, version, commandLine, asString(bootCp), "javac_bootcp", tempFiles, useTempFile);
			}
		}

		if(isJava9Version)
		{
			commandLine.add("--module-path");
			addClassPathValue(jdk, version, commandLine, classPath, "javac_mp", tempFiles, useTempFile);
		}
		else
		{
			commandLine.add("-classpath");
			addClassPathValue(jdk, version, commandLine, classPath, "javac_cp", tempFiles, useTempFile);
		}

		if(isAtLeast(version, languageLevel, JavaSdkVersion.JDK_1_9))
		{
			//commandLine.add("--module-source-path");
			//commandLine.add(chunk.getSourcePath());
		}
		else if(version != JavaSdkVersion.JDK_1_0 && version != JavaSdkVersion.JDK_1_1 && addSourcePath)
		{
			commandLine.add("-sourcepath");
			// this way we tell the compiler that the sourcepath is "empty". However, javac thinks that sourcepath is 'new File("")'
			// this may cause problems if we have java code in IDEA working directory
			if(isAnnotationProcessingMode)
			{
				final int currentSourcesMode = chunk.getSourcesFilter();
				commandLine.add(chunk.getSourcePath(currentSourcesMode == ModuleChunk.TEST_SOURCES ? ModuleChunk.ALL_SOURCES : currentSourcesMode));
			}
			else
			{
				if(version.isAtLeast(JavaSdkVersion.JDK_1_9))
				{
					commandLine.add(".");
				}
				else
				{
					commandLine.add("\"\"");
				}
			}
		}

		if(isAnnotationProcessingMode)
		{
			commandLine.add("-s");
			commandLine.add(outputPath.replace('/', File.separatorChar));
			final String moduleOutputPath = CompilerPathsImpl.getModuleOutputPath(chunk.getModules()[0], ProductionContentFolderTypeProvider.getInstance());
			if(moduleOutputPath != null)
			{
				commandLine.add("-d");
				commandLine.add(moduleOutputPath.replace('/', File.separatorChar));
			}
		}
		else
		{
			commandLine.add("-d");
			commandLine.add(outputPath.replace('/', File.separatorChar));
		}
	}

	private static boolean isAtLeast(@Nonnull JavaSdkVersion version, @Nullable LanguageLevel languageLevel, @Nonnull JavaSdkVersion target)
	{
		return version.isAtLeast(target) && (languageLevel == null || languageLevel.isAtLeast(target.getMaxLanguageLevel()));
	}

	@Nonnull
	private static Set<VirtualFile> filterModFiles(Set<VirtualFile> files)
	{
		Set<VirtualFile> newFiles = new LinkedHashSet<>(files.size());
		for(VirtualFile file : files)
		{
			if(JrtFileSystem.isModuleRoot(file) || JModFileType.isModuleRoot(file))
			{
				continue;
			}

			newFiles.add(file);
		}
		return newFiles;
	}

	@Nonnull
	private static String asString(Set<VirtualFile> files)
	{
		PathsList pathsList = new PathsList();
		pathsList.addVirtualFiles(files);
		return pathsList.getPathsString();
	}

	private static void addClassPathValue(final Sdk jdk, final JavaSdkVersion version, final ParametersList parametersList, final String cpString, @NonNls final String tempFileName, List<File> tempFiles, boolean useTempFile) throws IOException
	{
		if(!useTempFile)
		{
			parametersList.add(cpString);
			return;
		}
		// must include output path to classpath, otherwise javac will compile all dependent files no matter were they compiled before or not
		if(version == JavaSdkVersion.JDK_1_0)
		{
			parametersList.add(((JavaSdk) jdk.getSdkType()).getToolsPath(jdk) + File.pathSeparator + cpString);
		}
		else
		{
			File cpFile = File.createTempFile(tempFileName, ".tmp");
			cpFile.deleteOnExit();
			tempFiles.add(cpFile);
			final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cpFile)));
			try
			{
				CompilerIOUtil.writeString(cpString, out);
			}
			finally
			{
				out.close();
			}
			parametersList.add("@" + cpFile.getAbsolutePath());
		}
	}

	private Sdk getJdkForStartupCommand(final ModuleChunk chunk)
	{
		final Sdk jdk = JavaCompilerUtil.getSdkForCompilation(chunk);
		if(ApplicationManager.getApplication().isUnitTestMode() && JavacCompilerConfiguration.getInstance(myProject).isTestsUseExternalCompiler())
		{
			final String jdkHomePath = getTestsExternalCompilerHome();
			if(jdkHomePath == null)
			{
				throw new IllegalArgumentException("[TEST-MODE] Cannot determine home directory for JDK to use javac from");
			}
			// when running under Mock JDK use VM executable from the JDK on which the tests run
			return new MockSdkWrapper(jdkHomePath, jdk);
		}
		return jdk;
	}

	public static String getTestsExternalCompilerHome()
	{
		String compilerHome = System.getProperty(TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME, null);
		if(compilerHome == null)
		{
			if(SystemInfo.isMac)
			{
				compilerHome = new File(System.getProperty("java.home")).getAbsolutePath();
			}
			else
			{
				compilerHome = new File(System.getProperty("java.home")).getParentFile().getAbsolutePath();
			}
		}
		return compilerHome;
	}

	@Override
	public void compileFinished()
	{
		FileUtil.asyncDelete(myTempFiles);
		myTempFiles.clear();
	}
}
