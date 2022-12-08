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
package com.intellij.java.compiler.impl.javaCompiler.javac;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.java.compiler.impl.OutputParser;
import com.intellij.java.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.java.compiler.impl.javaCompiler.JavaCompilerConfiguration;
import com.intellij.java.compiler.impl.javaCompiler.annotationProcessing.AnnotationProcessingConfiguration;
import com.intellij.java.indexing.impl.stubs.index.JavaModuleNameIndex;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.light.LightJavaModule;
import com.intellij.java.language.projectRoots.JavaSdk;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.vfs.jrt.JrtFileSystem;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;
import consulo.compiler.CompileContext;
import consulo.compiler.CompilerPaths;
import consulo.compiler.ModuleChunk;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.compiler.scope.CompileScope;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTypeId;
import consulo.java.compiler.JavaCompilerBundle;
import consulo.java.compiler.JavaCompilerUtil;
import consulo.java.compiler.impl.javaCompiler.BackendCompilerMonitor;
import consulo.java.compiler.impl.javaCompiler.BackendCompilerProcessBuilder;
import consulo.java.compiler.impl.javaCompiler.JavaToolMonitor;
import consulo.java.compiler.impl.javaCompiler.NewBackendCompilerProcessBuilder;
import consulo.java.compiler.impl.javaCompiler.old.OldBackendCompilerProcessBuilder;
import consulo.java.language.fileTypes.JModFileType;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.OrderEnumerator;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.module.content.layer.orderEntry.ExportableOrderEntry;
import consulo.process.ProcessHandler;
import consulo.process.cmd.ParametersList;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.PathsList;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@ExtensionImpl
public class JavacCompiler implements BackendCompiler
{
	private final Project myProject;
	private boolean myAnnotationProcessorMode = false;

	@Inject
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
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.jdk.is.not.set.for.module", module.getName()), JavaCompilerBundle.message("compiler.javac.name"),
						Messages.getErrorIcon());
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
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.jdk.home.missing", javaSdk.getHomePath(), javaSdk.getName()), JavaCompilerBundle.message("compiler" +
						"" +
						".javac" + ".name"), Messages.getErrorIcon());
				return false;
			}
			final String versionString = javaSdk.getVersionString();
			if(versionString == null)
			{
				Messages.showMessageDialog(myProject, JavaCompilerBundle.message("javac.error.unknown.jdk.version", javaSdk.getName()), JavaCompilerBundle.message("compiler.javac.name"), Messages
						.getErrorIcon());
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
	public OutputParser createErrorParser(BackendCompilerProcessBuilder processBuilder, @Nonnull final String outputDir, ProcessHandler process)
	{
		if(processBuilder instanceof NewBackendCompilerProcessBuilder)
		{
			return null;
		}
		return new JavacOutputParser(myProject);
	}

	@Nullable
	@Override
	public BackendCompilerMonitor createMonitor(BackendCompilerProcessBuilder processBuilder)
	{
		if(processBuilder instanceof NewBackendCompilerProcessBuilder)
		{
			return new JavaToolMonitor((NewBackendCompilerProcessBuilder) processBuilder);
		}
		return null;
	}

	@Nonnull
	@Override
	public BackendCompilerProcessBuilder prepareProcess(@Nonnull ModuleChunk chunk, @Nonnull String outputDir, @Nonnull CompileContext compileContext) throws IOException
	{
		JpsJavaCompilerOptions javaCompilerOptions = JavacCompilerConfiguration.getInstance(myProject);
		JavaCompilerConfiguration javaCompilerConfiguration = JavaCompilerConfiguration.getInstance(myProject);

		//noinspection ConstantConditions
		return AccessRule.read(() ->
		{
			final Sdk jdk = getJdkForStartupCommand(chunk);

			JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);

			if(version != null && version.isAtLeast(JavaSdkVersion.JDK_1_8))
			{
				return new NewBackendCompilerProcessBuilder(chunk, outputDir, compileContext, javaCompilerOptions, javaCompilerConfiguration.isAnnotationProcessorsEnabled());
			}

			return new OldBackendCompilerProcessBuilder(chunk, outputDir, compileContext, javaCompilerOptions, javaCompilerConfiguration.isAnnotationProcessorsEnabled());
		});
	}

	public static List<String> addAdditionalSettings(ParametersList parametersList,
													 JpsJavaCompilerOptions javacOptions,
													 boolean isAnnotationProcessing,
													 JavaSdkVersion version,
													 ModuleChunk chunk,
													 boolean annotationProcessorsEnabled)
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
			String token = tokenizer.nextToken();
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
	public static void addCommandLineOptions(CompileContext compileContext,
											 ModuleChunk chunk,
											 ParametersList commandLine,
											 String outputPath,
											 Sdk jdk,
											 JavaSdkVersion version,
											 List<File> tempFiles,
											 boolean addSourcePath,
											 boolean isAnnotationProcessingMode,
											 boolean newCompiler) throws IOException
	{
		JavaModuleExtension<?> extension = ModuleUtilCore.getExtension(chunk.getModule(), JavaModuleExtension.class);
		LanguageLevel languageLevel = JavaCompilerUtil.getLanguageLevelForCompilation(chunk);
		boolean isJava9Version = isAtLeast(version, languageLevel, JavaSdkVersion.JDK_1_9);

		Module module = chunk.getModule();
		if(!isJava9Version)
		{
			JavaCompilerUtil.addSourceCommandLineSwitch(jdk, languageLevel, commandLine);
			JavaCompilerUtil.addTargetCommandLineSwitch(chunk, commandLine);
		}
		else
		{
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

		if(extension != null)
		{
			commandLine.addAll(extension.getCompilerArguments());
		}

		commandLine.add("-verbose");

		final Set<VirtualFile> cp = JavaCompilerUtil.getCompilationClasspath(compileContext, chunk);
		final Set<VirtualFile> bootCp = filterModFiles(JavaCompilerUtil.getCompilationBootClasspath(compileContext, chunk));

		final List<String> classPath;
		if(version == JavaSdkVersion.JDK_1_0 || version == JavaSdkVersion.JDK_1_1)
		{
			classPath = new ArrayList<>();
			classPath.addAll(toStringList(bootCp));
			classPath.addAll(toStringList(cp));
		}
		else
		{
			classPath = toStringList(cp);
			if(version.isAtLeast(JavaSdkVersion.JDK_1_9))
			{
				// add bootpath if target is 1.8 or lower
				if(languageLevel != null && !languageLevel.isAtLeast(LanguageLevel.JDK_1_9) && !bootCp.isEmpty())
				{
					commandLine.add("-bootclasspath");
					addClassPathValue(jdk, version, commandLine, toStringList(bootCp), "javac_bootcp", tempFiles);
				}
			}
			else
			{
				commandLine.add("-bootclasspath");
				addClassPathValue(jdk, version, commandLine, toStringList(bootCp), "javac_bootcp", tempFiles);
			}
		}

		PsiJavaModule javaModule = isJava9Version ? JavaModuleGraphUtil.findDescriptorByModule(chunk.getModule(), chunk.getSourcesFilter() == ModuleChunk.TEST_SOURCES) : null;

		// enable module-path only if module contains module-info
		if(isJava9Version && javaModule != null)
		{
			commandLine.add("--module-path");
			addClassPathValue(jdk, version, commandLine, classPath, "javac_mp", tempFiles);
		}
		else
		{
			commandLine.add("-classpath");
			addClassPathValue(jdk, version, commandLine, classPath, "javac_cp", tempFiles);
		}

		if(isJava9Version && chunk.getSourcesFilter() == ModuleChunk.TEST_SOURCES)
		{
			String moduleName = findModuleName(module);
			if(moduleName != null)
			{
				String url = ModuleCompilerPathsManager.getInstance(module).getCompilerOutputUrl(TestContentFolderTypeProvider.getInstance());

				List<VirtualFile> testLibs = OrderEnumerator.orderEntries(module).satisfying(orderEntry -> {
					if(orderEntry instanceof ExportableOrderEntry)
					{
						DependencyScope scope = ((ExportableOrderEntry) orderEntry).getScope();
						return scope == DependencyScope.TEST;
					}
					return false;
				}).compileOnly().getPathsList().getRootDirs();

				Set<String> moduleNames = new HashSet<>();

				for(VirtualFile testLib : testLibs)
				{
					VirtualFile virtualFile = JavaModuleNameIndex.descriptorFile(testLib);

					String psiJavaModule = virtualFile == null ? null : findModuleName(module.getProject(), virtualFile);

					if(psiJavaModule == null)
					{
						psiJavaModule = LightJavaModule.moduleName(testLib);
					}

					moduleNames.add(psiJavaModule);
				}
				if(url != null)
				{
					commandLine.add("--patch-module");
					commandLine.add(moduleName + "=" + VirtualFileUtil.urlToPath(url));
				}

				String joinedModuleNames = String.join(",", moduleNames);


				if(!moduleNames.isEmpty())
				{
					commandLine.add("--add-modules");
					commandLine.add(joinedModuleNames + "," + moduleName);

					commandLine.add("--add-reads");
					commandLine.add(moduleName + "=" + joinedModuleNames);

					commandLine.add("--add-opens");
					commandLine.add(moduleName + "=" + joinedModuleNames);
				}
				else
				{
					commandLine.add("--add-modules");
					commandLine.add(joinedModuleNames + "," + moduleName);
				}
			}
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
				if(newCompiler)
				{
					VirtualFile[] sourcePaths = chunk.getSourceRoots(currentSourcesMode == ModuleChunk.TEST_SOURCES ? ModuleChunk.ALL_SOURCES : currentSourcesMode);
					addClassPathValue(jdk, version, commandLine, toStringList(Set.of(sourcePaths)), "javac_sp", tempFiles);
				}
				else
				{
					commandLine.add(chunk.getSourcePath(currentSourcesMode == ModuleChunk.TEST_SOURCES ? ModuleChunk.ALL_SOURCES : currentSourcesMode));
				}
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
			final String moduleOutputPath = CompilerPaths.getModuleOutputPath(chunk.getModules()[0], ProductionContentFolderTypeProvider.getInstance());
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

	@Nullable
	public static String findModuleName(@Nonnull Module module)
	{
		VirtualFile[] folders = AccessRule.read(() -> ModuleRootManager.getInstance(module).getContentFolderFiles(LanguageContentFolderScopes.onlyProduction()));
		assert folders != null;
		for(VirtualFile folder : folders)
		{
			VirtualFile moduleInfo = folder.findChild(PsiJavaModule.MODULE_INFO_FILE);
			if(moduleInfo == null)
			{
				continue;
			}

			String javaModule = findModuleName(module.getProject(), moduleInfo);
			if(javaModule != null)
			{
				return javaModule;
			}
		}

		return null;
	}

	@Nullable
	private static String findModuleName(@Nonnull Project project, @Nonnull VirtualFile moduleInfo)
	{
		PsiFile file = AccessRule.read(() -> PsiManager.getInstance(project).findFile(moduleInfo));
		if(file instanceof PsiJavaFile)
		{
			PsiJavaModule moduleDeclaration = AccessRule.read(((PsiJavaFile) file)::getModuleDeclaration);
			if(moduleDeclaration != null)
			{
				return AccessRule.read(moduleDeclaration::getName);
			}
		}
		return null;
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
	private static List<String> toStringList(Set<VirtualFile> files)
	{
		PathsList pathsList = new PathsList();
		pathsList.addVirtualFiles(files);
		return pathsList.getPathList();
	}

	private static void addClassPathValue(final Sdk jdk,
										  final JavaSdkVersion version,
										  final ParametersList parametersList,
										  final List<String> cpString,
										  final String tempFileName,
										  List<File> tempFiles) throws IOException
	{
		File cpFile = File.createTempFile(tempFileName, ".tmp");
		cpFile.deleteOnExit();
		tempFiles.add(cpFile);

		Files.write(cpFile.toPath(), cpString);

		parametersList.add("@" + cpFile.getAbsolutePath());
	}

	public static Sdk getJdkForStartupCommand(final ModuleChunk chunk)
	{
		return JavaCompilerUtil.getSdkForCompilation(chunk);
	}
}
