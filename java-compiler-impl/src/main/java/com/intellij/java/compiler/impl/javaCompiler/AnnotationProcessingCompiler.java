/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author: Eugene Zhuravlev
 * Date: Jan 17, 2003
 * Time: 3:22:59 PM
 */
package com.intellij.java.compiler.impl.javaCompiler;

import com.intellij.java.compiler.CompilerException;
import com.intellij.java.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.JavaFileType;
import consulo.application.ApplicationManager;
import consulo.application.CommonBundle;
import consulo.compiler.*;
import consulo.compiler.scope.CompileScope;
import consulo.compiler.util.ModuleCompilerUtil;
import consulo.java.compiler.impl.javaCompiler.JavaAdditionalOutputDirectoriesProvider;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.ui.view.internal.ProjectSettingsService;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.Chunk;
import consulo.util.lang.ExceptionUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;
import java.util.*;

public class AnnotationProcessingCompiler implements TranslatingCompiler
{
	private static final Logger LOGGER = Logger.getInstance(AnnotationProcessingCompiler.class);
	private final Project myProject;
	private final JavaCompilerConfiguration myCompilerConfiguration;

	@Inject
	public AnnotationProcessingCompiler(Project project)
	{
		myProject = project;
		myCompilerConfiguration = JavaCompilerConfiguration.getInstance(project);
	}

	@Override
	@Nonnull
	public String getDescription()
	{
		return CompilerBundle.message("annotation.processing.compiler.description");
	}

	@Override
	public boolean isCompilableFile(VirtualFile file, CompileContext context)
	{
		if(!myCompilerConfiguration.isAnnotationProcessorsEnabled())
		{
			return false;
		}
		return file.getFileType() == JavaFileType.INSTANCE && !isExcludedFromAnnotationProcessing(file, context);
	}

	@Override
	public void compile(final CompileContext context, final Chunk<Module> moduleChunk, final VirtualFile[] files, OutputSink sink)
	{
		if(!myCompilerConfiguration.isAnnotationProcessorsEnabled())
		{
			return;
		}
		final LocalFileSystem lfs = LocalFileSystem.getInstance();
		final CompileContextEx _context = new CompileContextExDelegate((CompileContextEx) context)
		{
			@Override
			public VirtualFile getModuleOutputDirectory(Module module)
			{
				final String path = JavaAdditionalOutputDirectoriesProvider.getAnnotationProcessorsGenerationPath(module);
				return path != null ? lfs.findFileByPath(path) : null;
			}

			@Override
			public VirtualFile getModuleOutputDirectoryForTests(Module module)
			{
				return getModuleOutputDirectory(module);
			}
		};
		final JavacCompiler javacCompiler = getBackEndCompiler();
		final boolean processorMode = javacCompiler.setAnnotationProcessorMode(true);
		final BackendCompilerWrapper wrapper = new BackendCompilerWrapper(this, moduleChunk, myProject, Arrays.asList(files), _context, javacCompiler, sink);
		wrapper.setForceCompileTestsSeparately(true);
		try
		{
			wrapper.compile(new HashMap<>());
		}
		catch(CompilerException e)
		{
			_context.addMessage(CompilerMessageCategory.ERROR, ExceptionUtil.getThrowableText(e), null, -1, -1);
		}
		catch(CacheCorruptedException e)
		{
			LOGGER.info(e);
			_context.requestRebuildNextTime(e.getMessage());
		}
		finally
		{
			javacCompiler.setAnnotationProcessorMode(processorMode);
			final Set<VirtualFile> dirsToRefresh = new HashSet<VirtualFile>();
			ApplicationManager.getApplication().runReadAction(new Runnable()
			{
				@Override
				public void run()
				{
					for(Module module : moduleChunk.getNodes())
					{
						final VirtualFile out = _context.getModuleOutputDirectory(module);
						if(out != null)
						{
							dirsToRefresh.add(out);
						}
					}
				}
			});
			for(VirtualFile root : dirsToRefresh)
			{
				root.refresh(false, true);
			}
		}
	}

	@Nonnull
	@Override
	public FileType[] getInputFileTypes()
	{
		return new FileType[]{JavaFileType.INSTANCE};
	}

	@Nonnull
	@Override
	public FileType[] getOutputFileTypes()
	{
		return new FileType[]{
				JavaFileType.INSTANCE,
				JavaClassFileType.INSTANCE
		};
	}

	private boolean isExcludedFromAnnotationProcessing(VirtualFile file, CompileContext context)
	{
		if(!myCompilerConfiguration.isAnnotationProcessorsEnabled())
		{
			return true;
		}
		final Module module = context.getModuleByFile(file);
		if(module != null)
		{
			if(!myCompilerConfiguration.getAnnotationProcessingConfiguration(module).isEnabled())
			{
				return true;
			}
			final String path = JavaAdditionalOutputDirectoriesProvider.getAnnotationProcessorsGenerationPath(module);
			final VirtualFile generationDir = path != null ? LocalFileSystem.getInstance().findFileByPath(path) : null;
			if(generationDir != null && VirtualFileUtil.isAncestor(generationDir, file, false))
			{
				return true;
			}
		}
		return CompilerManager.getInstance(myProject).isExcludedFromCompilation(file);
	}

	@Override
	public boolean validateConfiguration(CompileScope scope)
	{
		final List<Chunk<Module>> chunks = ModuleCompilerUtil.getSortedModuleChunks(myProject, Arrays.asList(scope.getAffectedModules()));
		for(final Chunk<Module> chunk : chunks)
		{
			final Set<Module> chunkModules = chunk.getNodes();
			if(chunkModules.size() <= 1)
			{
				continue; // no need to check one-module chunks
			}
			for(Module chunkModule : chunkModules)
			{
				if(myCompilerConfiguration.getAnnotationProcessingConfiguration(chunkModule).isEnabled())
				{
					showCyclesNotSupportedForAnnotationProcessors(chunkModules.toArray(new Module[chunkModules.size()]));
					return false;
				}
			}
		}

		final JavacCompiler compiler = getBackEndCompiler();
		final boolean previousValue = compiler.setAnnotationProcessorMode(true);
		try
		{
			return compiler.checkCompiler(scope);
		}
		finally
		{
			compiler.setAnnotationProcessorMode(previousValue);
		}
	}

	private void showCyclesNotSupportedForAnnotationProcessors(Module[] modulesInChunk)
	{
		LOGGER.assertTrue(modulesInChunk.length > 0);
		String moduleNameToSelect = modulesInChunk[0].getName();
		final String moduleNames = getModulesString(modulesInChunk);
		Messages.showMessageDialog(myProject, CompilerBundle.message("error.annotation.processing.not.supported.for.module.cycles", moduleNames), CommonBundle.getErrorTitle(), Messages.getErrorIcon
				());
		showConfigurationDialog(moduleNameToSelect, null);
	}

	private void showConfigurationDialog(String moduleNameToSelect, String tabNameToSelect)
	{
		ProjectSettingsService.getInstance(myProject).showModuleConfigurationDialog(moduleNameToSelect, tabNameToSelect);
	}

	private static String getModulesString(Module[] modulesInChunk)
	{
		final StringBuilder moduleNames = new StringBuilder();
		for(Module module : modulesInChunk)
		{
			if(moduleNames.length() > 0)
			{
				moduleNames.append("\n");
			}
			moduleNames.append("\"").append(module.getName()).append("\"");
		}
		return moduleNames.toString();
	}

	private JavacCompiler getBackEndCompiler()
	{
		return (JavacCompiler) myCompilerConfiguration.findCompiler(JavaCompilerConfiguration.DEFAULT_COMPILER);
	}
}
