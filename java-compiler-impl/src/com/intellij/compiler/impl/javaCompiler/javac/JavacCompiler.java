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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.mustbe.consulo.java.module.extension.JavaModuleExtension;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.compiler.roots.CompilerPathsImpl;
import org.mustbe.consulo.roots.impl.ProductionContentFolderTypeProvider;
import com.intellij.compiler.CompilerIOUtil;
import com.intellij.compiler.JavaCompilerBundle;
import com.intellij.compiler.JavaCompilerUtil;
import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.ModuleChunk;
import com.intellij.compiler.impl.javaCompiler.ExternalCompiler;
import com.intellij.compiler.impl.javaCompiler.JavaCompilerConfiguration;
import com.intellij.compiler.impl.javaCompiler.annotationProcessing.AnnotationProcessingConfiguration;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.projectRoots.impl.MockSdkWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.rt.compiler.JavacRunner;

public class JavacCompiler extends ExternalCompiler
{
	@NonNls
	public static final String TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME = "tests.external.compiler.home";

	private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.javac.JavacCompiler");
	private final Project myProject;
	private final List<File> myTempFiles = new ArrayList<File>();
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
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.jdk.is.not.set.for.module", module.getName()),
						JavaCompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
				return false;
			}

			if(checkedJdks.contains(javaSdk))
			{
				continue;
			}

			checkedJdks.add(javaSdk);
			final SdkTypeId sdkType = javaSdk.getSdkType();
			assert sdkType instanceof JavaSdkType;

			final VirtualFile homeDirectory = javaSdk.getHomeDirectory();
			if(homeDirectory == null)
			{
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.jdk.home.missing", javaSdk.getHomePath()),
						JavaCompilerBundle.message("compiler.javac" + ".name"), Messages.getErrorIcon());
				return false;
			}
			final String toolsJarPath = ((JavaSdkType) sdkType).getToolsPath(javaSdk);
			if(toolsJarPath == null)
			{
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.tools.jar.missing", javaSdk.getName()),
						JavaCompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
				return false;
			}
			final String versionString = javaSdk.getVersionString();
			if(versionString == null)
			{
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.unknown.jdk.version", javaSdk.getName()),
						JavaCompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
				return false;
			}

			if(CompilerUtil.isOfVersion(versionString, "1.0"))
			{
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.1_0_compilation.not.supported"),
						JavaCompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
				return false;
			}
		}

		return true;
	}

	@Override
	@NotNull
	public String getPresentableName()
	{
		return JavaCompilerBundle.message("compiler.javac.name");
	}

	@Override
	@NotNull
	public Configurable createConfigurable()
	{
		return new JavacConfigurable(myProject);
	}

	@Override
	public OutputParser createErrorParser(@NotNull final String outputDir, Process process)
	{
		return new JavacOutputParser(myProject);
	}

	@Override
	public OutputParser createOutputParser(@NotNull final String outputDir)
	{
		return null;
	}

	private static class MyException extends RuntimeException
	{
		private MyException(Throwable cause)
		{
			super(cause);
		}
	}

	@Override
	@NotNull
	public GeneralCommandLine createStartupCommand(final ModuleChunk chunk,
			final CompileContext context,
			final String outputPath) throws IOException, IllegalArgumentException
	{

		try
		{
			return ApplicationManager.getApplication().runReadAction(new Computable<GeneralCommandLine>()
			{
				@Override
				public GeneralCommandLine compute()
				{
					try
					{
						return createStartupCommand(chunk, outputPath, context, JavacCompilerConfiguration.getInstance(myProject),
								JavaCompilerConfiguration.getInstance(myProject).isAnnotationProcessorsEnabled());
					}
					catch(IOException e)
					{
						throw new MyException(e);
					}
				}
			});
		}
		catch(MyException e)
		{
			Throwable cause = e.getCause();
			if(cause instanceof IOException)
			{
				throw (IOException) cause;
			}
			throw e;
		}
	}

	@NotNull
	private GeneralCommandLine createStartupCommand(final ModuleChunk chunk,
			final String outputPath,
			final CompileContext compileContext,
			JpsJavaCompilerOptions javacOptions,
			final boolean annotationProcessorsEnabled) throws IOException
	{
		final Sdk jdk = getJdkForStartupCommand(chunk);
		final String versionString = jdk.getVersionString();
		JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
		if(versionString == null || version == null || !(jdk.getSdkType() instanceof JavaSdkType))
		{
			throw new IllegalArgumentException(JavaCompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()));
		}

		JavaSdkType sdkType = (JavaSdkType) jdk.getSdkType();

		final String toolsJarPath = sdkType.getToolsPath(jdk);
		if(toolsJarPath == null)
		{
			throw new IllegalArgumentException(JavaCompilerBundle.message("javac.error.tools.jar.missing", jdk.getName()));
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

		final List<String> additionalOptions = addAdditionalSettings(parametersList, javacOptions, myAnnotationProcessorMode, version, chunk,
				annotationProcessorsEnabled);

		JavaCompilerUtil.addLocaleOptions(parametersList, false);

		parametersList.add("-classpath");

		if(version == JavaSdkVersion.JDK_1_0)
		{
			parametersList.add(sdkType.getToolsPath(jdk)); //  do not use JavacRunner for jdk 1.0
		}
		else
		{
			parametersList.add(sdkType.getToolsPath(jdk) + File.pathSeparator + JavaSdkUtil.getIdeaRtJarPath());
			parametersList.add(JavacRunner.class.getName());
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
			File sourcesFile = FileUtil.createTempFile("javac", ".tmp");
			sourcesFile.deleteOnExit();
			myTempFiles.add(sourcesFile);
			final PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(sourcesFile)));
			try
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
			finally
			{
				writer.close();
			}
			parametersList.add("@" + sourcesFile.getAbsolutePath());
		}
		return commandLine;
	}

	public static List<String> addAdditionalSettings(ParametersList parametersList,
			JpsJavaCompilerOptions javacOptions,
			boolean isAnnotationProcessing,
			JavaSdkVersion version,
			ModuleChunk chunk,
			boolean annotationProcessorsEnabled)
	{
		final List<String> additionalOptions = new ArrayList<String>();
		StringTokenizer tokenizer = new StringTokenizer(new JavacSettingsBuilder(javacOptions).getOptionsString(chunk), " ");
		if(!version.isAtLeast(JavaSdkVersion.JDK_1_6))
		{
			isAnnotationProcessing = false; // makes no sense for these versions
			annotationProcessorsEnabled = false;
		}
		if(isAnnotationProcessing)
		{
			final AnnotationProcessingConfiguration config = JavaCompilerConfiguration.getInstance(chunk.getProject())
					.getAnnotationProcessingConfiguration(chunk.getModules()[0]);
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

	public static void addCommandLineOptions(CompileContext compileContext,
			ModuleChunk chunk,
			@NonNls ParametersList commandLine,
			String outputPath,
			Sdk jdk,
			JavaSdkVersion version,
			List<File> tempFiles,
			boolean addSourcePath,
			boolean useTempFile,
			boolean isAnnotationProcessingMode) throws IOException
	{

		LanguageLevel languageLevel = JavaCompilerUtil.getLanguageLevelForCompilation(chunk);
		JavaCompilerUtil.addSourceCommandLineSwitch(jdk, languageLevel, commandLine);
		JavaCompilerUtil.addTargetCommandLineSwitch(chunk, commandLine);

		commandLine.add("-verbose");

		final String cp = JavaCompilerUtil.getCompilationClasspath(compileContext, chunk);
		final String bootCp = JavaCompilerUtil.getCompilationBootClasspath(compileContext, chunk);

		final String classPath;
		if(version == JavaSdkVersion.JDK_1_0 || version == JavaSdkVersion.JDK_1_1)
		{
			classPath = bootCp + File.pathSeparator + cp;
		}
		else
		{
			classPath = cp;
			commandLine.add("-bootclasspath");
			addClassPathValue(jdk, version, commandLine, bootCp, "javac_bootcp", tempFiles, useTempFile);
		}

		commandLine.add("-classpath");
		addClassPathValue(jdk, version, commandLine, classPath, "javac_cp", tempFiles, useTempFile);

		if(version != JavaSdkVersion.JDK_1_0 && version != JavaSdkVersion.JDK_1_1 && addSourcePath)
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
			final String moduleOutputPath = CompilerPathsImpl.getModuleOutputPath(chunk.getModules()[0],
					ProductionContentFolderTypeProvider.getInstance());
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

	private static void addClassPathValue(final Sdk jdk,
			final JavaSdkVersion version,
			final ParametersList parametersList,
			final String cpString,
			@NonNls final String tempFileName,
			List<File> tempFiles,
			boolean useTempFile) throws IOException
	{
		if(!useTempFile)
		{
			parametersList.add(cpString);
			return;
		}
		// must include output path to classpath, otherwise javac will compile all dependent files no matter were they compiled before or not
		if(version == JavaSdkVersion.JDK_1_0)
		{
			parametersList.add(((JavaSdkType) jdk.getSdkType()).getToolsPath(jdk) + File.pathSeparator + cpString);
		}
		else
		{
			File cpFile = FileUtil.createTempFile(tempFileName, ".tmp");
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
