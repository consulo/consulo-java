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
package com.intellij.java.compiler.impl.javaCompiler;

import com.intellij.java.compiler.impl.CompilerException;
import com.intellij.java.compiler.impl.OutputParser;
import com.intellij.java.compiler.impl.cache.Cache;
import com.intellij.java.compiler.impl.cache.JavaDependencyCache;
import com.intellij.java.compiler.impl.cache.JavaMakeUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.util.cls.ClsFormatException;
import consulo.application.AccessRule;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.util.AsyncFileService;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.application.util.function.Computable;
import consulo.compiler.*;
import consulo.compiler.util.CompilerUtil;
import consulo.content.ContentIterator;
import consulo.java.compiler.impl.javaCompiler.BackendCompilerMonitor;
import consulo.java.compiler.impl.javaCompiler.BackendCompilerProcessBuilder;
import consulo.language.file.FileTypeManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScopesCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ModuleFileIndex;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.Project;
import consulo.util.collection.Chunk;
import consulo.util.dataholder.Key;
import consulo.util.io.FilePermissionCopier;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.util.VirtualFileVisitor;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author Eugene Zhuravlev
 * @since Jan 24, 2003
 */
public class BackendCompilerWrapper
{
	public static final Key<ClassParsingHandler> CLASS_PARSING_HANDLER_KEY = Key.create(ClassParsingHandler.class.getName());

	private static final Logger LOG = Logger.getInstance(BackendCompilerWrapper.class);

	private final BackendCompiler myCompiler;

	private final CompileContextEx myCompileContext;
	private final List<VirtualFile> myFilesToCompile;
	private final TranslatingCompiler.OutputSink mySink;
	private final TranslatingCompiler myTranslatingCompiler;
	private final Chunk<Module> myChunk;
	private final Project myProject;
	private final Map<Module, VirtualFile> myModuleToTempDirMap = new HashMap<>();
	private final ProjectFileIndex myProjectFileIndex;
	@NonNls
	private static final String PACKAGE_ANNOTATION_FILE_NAME = "package-info.java";
	private static final FileObject ourStopThreadToken = new FileObject(new File(""), new byte[0]);
	public final Map<String, Set<CompiledClass>> myFileNameToSourceMap = new HashMap<>();
	private final Set<VirtualFile> myProcessedPackageInfos = new HashSet<>();
	private final CompileStatistics myStatistics;
	private volatile String myModuleName = null;
	private boolean myForceCompileTestsSeparately = false;

	public BackendCompilerWrapper(TranslatingCompiler translatingCompiler,
								  Chunk<Module> chunk,
								  @jakarta.annotation.Nonnull final Project project,
								  @jakarta.annotation.Nonnull List<VirtualFile> filesToCompile,
								  @jakarta.annotation.Nonnull CompileContextEx compileContext,
								  @jakarta.annotation.Nonnull BackendCompiler compiler,
								  TranslatingCompiler.OutputSink sink)
	{
		myTranslatingCompiler = translatingCompiler;
		myChunk = chunk;
		myProject = project;
		myCompiler = compiler;
		myCompileContext = compileContext;
		myFilesToCompile = filesToCompile;
		mySink = sink;
		myProjectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
		CompileStatistics stat = compileContext.getUserData(CompileStatistics.KEY);
		if(stat == null)
		{
			stat = new CompileStatistics();
			compileContext.putUserData(CompileStatistics.KEY, stat);
		}
		myStatistics = stat;
	}

	public void compile(@jakarta.annotation.Nonnull Map<File, FileObject> parsingInfo) throws CompilerException, CacheCorruptedException
	{
		Application application = ApplicationManager.getApplication();
		try
		{
			if(!myFilesToCompile.isEmpty())
			{
				if(application.isUnitTestMode())
				{
					saveTestData();
				}
				compileModules(buildModuleToFilesMap(myFilesToCompile), parsingInfo);
			}
		}
		catch(SecurityException e)
		{
			throw new CompilerException(CompilerBundle.message("error.compiler.process.not.started", e.getMessage()), e);
		}
		catch(IllegalArgumentException e)
		{
			throw new CompilerException(e.getMessage(), e);
		}
		finally
		{
			for(final VirtualFile file : myModuleToTempDirMap.values())
			{
				if(file != null)
				{
					final File ioFile = new File(file.getPath());
					getAsyncFileService().asyncDelete(ioFile);
				}
			}
			myModuleToTempDirMap.clear();
		}

		if(!myFilesToCompile.isEmpty() && myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) == 0)
		{
			// package-info.java hack
			final List<TranslatingCompiler.OutputItem> outputs = new ArrayList<>();
			ApplicationManager.getApplication().runReadAction(new Runnable()
			{
				@Override
				public void run()
				{
					for(final VirtualFile file : myFilesToCompile)
					{
						if(PACKAGE_ANNOTATION_FILE_NAME.equals(file.getName()) && !myProcessedPackageInfos.contains(file))
						{
							outputs.add(new OutputItemImpl(file));
						}
					}
				}
			});
			if(!outputs.isEmpty())
			{
				mySink.add(null, outputs, VirtualFile.EMPTY_ARRAY);
			}
		}
	}

	public boolean isForceCompileTestsSeparately()
	{
		return myForceCompileTestsSeparately;
	}

	public void setForceCompileTestsSeparately(boolean forceCompileTestsSeparately)
	{
		myForceCompileTestsSeparately = forceCompileTestsSeparately;
	}

	private Map<Module, List<VirtualFile>> buildModuleToFilesMap(final List<VirtualFile> filesToCompile)
	{
		if(myChunk.getNodes().size() == 1)
		{
			return Collections.singletonMap(myChunk.getNodes().iterator().next(), Collections.unmodifiableList(filesToCompile));
		}
		return CompilerUtil.buildModuleToFilesMap(myCompileContext, filesToCompile);
	}

	@Nonnull
	private AsyncFileService getAsyncFileService()
	{
		return Application.get().getInstance(AsyncFileService.class);
	}

	private void compileModules(final Map<Module, List<VirtualFile>> moduleToFilesMap, Map<File, FileObject> parsingInfo) throws CompilerException
	{
		try
		{
			compileChunk(new ModuleChunk(myCompileContext, myChunk, moduleToFilesMap), parsingInfo);
		}
		catch(IOException e)
		{
			throw new CompilerException(e.getMessage(), e);
		}
	}

	private void compileChunk(ModuleChunk chunk, Map<File, FileObject> parsingInfo) throws IOException
	{
		final String chunkPresentableName = getPresentableNameFor(chunk);
		myModuleName = chunkPresentableName;

		// validate encodings
		if(chunk.getModuleCount() > 1)
		{
			validateEncoding(chunk, chunkPresentableName);
			// todo: validation for bytecode target?
		}

		runTransformingCompilers(chunk);


		final List<OutputDir> outs = new ArrayList<>();
		File fileToDelete = getOutputDirsToCompileTo(chunk, outs);

		try
		{
			for(final OutputDir outputDir : outs)
			{
				chunk.setSourcesFilter(outputDir.getKind());

				doCompile(chunk, outputDir.getPath(), parsingInfo);
			}
		}
		finally
		{
			if(fileToDelete != null)
			{
				getAsyncFileService().asyncDelete(fileToDelete);
			}
		}
	}

	private void validateEncoding(ModuleChunk chunk, String chunkPresentableName)
	{
		final CompilerEncodingService es = CompilerEncodingService.getInstance(myProject);
		Charset charset = null;
		for(Module module : chunk.getModules())
		{
			final Charset moduleCharset = es.getPreferredModuleEncoding(module);
			if(charset == null)
			{
				charset = moduleCharset;
			}
			else
			{
				if(!Comparing.equal(charset, moduleCharset))
				{
					// warn user
					final Charset chunkEncoding = CompilerEncodingService.getPreferredModuleEncoding(chunk);
					final StringBuilder message = new StringBuilder();
					message.append("Modules in chunk [");
					message.append(chunkPresentableName);
					message.append("] configured to use different encodings.\n");
					if(chunkEncoding != null)
					{
						message.append("\"").append(chunkEncoding.name()).append("\" encoding will be used to compile the chunk");
					}
					else
					{
						message.append("Default compiler encoding will be used to compile the chunk");
					}
					myCompileContext.addMessage(CompilerMessageCategory.INFORMATION, message.toString(), null, -1, -1);
					break;
				}
			}
		}
	}


	private static String getPresentableNameFor(final ModuleChunk chunk)
	{
		return ApplicationManager.getApplication().runReadAction(new Computable<String>()
		{
			@Override
			public String compute()
			{
				final Module[] modules = chunk.getModules();
				StringBuilder moduleName = new StringBuilder(Math.min(128, modules.length * 8));
				for(int idx = 0; idx < modules.length; idx++)
				{
					final Module module = modules[idx];
					if(idx > 0)
					{
						moduleName.append(", ");
					}
					moduleName.append(module.getName());
					if(moduleName.length() > 128 && idx + 1 < modules.length /*name is already too long and seems to grow longer*/)
					{
						moduleName.append("...");
						break;
					}
				}
				return moduleName.toString();
			}
		});
	}

	@Nullable
	private File getOutputDirsToCompileTo(ModuleChunk chunk, final List<OutputDir> dirs) throws IOException
	{
		File fileToDelete = null;
		if(chunk.getModuleCount() == 1)
		{ // optimization
			final Module module = chunk.getModules()[0];
			ApplicationManager.getApplication().runReadAction(new Runnable()
			{
				@Override
				public void run()
				{
					final String sourcesOutputDir = getOutputDir(module);
					if(shouldCompileTestsSeparately(module))
					{
						if(sourcesOutputDir != null)
						{
							dirs.add(new OutputDir(sourcesOutputDir, ModuleChunk.SOURCES));
						}
						final String testsOutputDir = getTestsOutputDir(module);
						if(testsOutputDir == null)
						{
							LOG.error("Tests output dir is null for module \"" + module.getName() + "\"");
						}
						else
						{
							dirs.add(new OutputDir(testsOutputDir, ModuleChunk.TEST_SOURCES));
						}
					}
					else
					{ // both sources and test sources go into the same output
						if(sourcesOutputDir == null)
						{
							LOG.error("Sources output dir is null for module \"" + module.getName() + "\"");
						}
						else
						{
							dirs.add(new OutputDir(sourcesOutputDir, ModuleChunk.ALL_SOURCES));
						}
					}
				}
			});
		}
		else
		{ // chunk has several modules
			final File outputDir = Files.createTempDirectory("compileOutput").toFile();
			fileToDelete = outputDir;
			dirs.add(new OutputDir(outputDir.getPath(), ModuleChunk.ALL_SOURCES));
		}
		return fileToDelete;
	}


	private boolean shouldCompileTestsSeparately(Module module)
	{
		if(myForceCompileTestsSeparately)
		{
			return true;
		}
		final String moduleTestOutputDirectory = getTestsOutputDir(module);
		if(moduleTestOutputDirectory == null)
		{
			return false;
		}
		// here we have test output specified
		final String moduleOutputDirectory = getOutputDir(module);
		if(moduleOutputDirectory == null)
		{
			// only test output is specified, so should return true
			return true;
		}
		return !FileUtil.pathsEqual(moduleTestOutputDirectory, moduleOutputDirectory);
	}

	private void saveTestData()
	{
	/*ApplicationManager.getApplication().runReadAction(new Runnable() {
	  public void run() {
        for (VirtualFile file : myFilesToCompile) {
          CompilerManagerImpl.addCompiledPath(file.getPath());
        }
      }
    }); */
	}

	private class CompilerParsingHandler extends CompilerParsingThread
	{
		private final ClassParsingHandler myClassParsingThread;

		private CompilerParsingHandler(ProcessHandler processHandler,
									   CompileContext context,
									   OutputParser outputParser,
									   ClassParsingHandler classParsingThread,
									   boolean readErrorStream,
									   boolean trimLines)
		{
			super(processHandler, outputParser, readErrorStream, trimLines, context);
			myClassParsingThread = classParsingThread;
		}

		@Override
		protected void processCompiledClass(final FileObject classFileToProcess) throws CacheCorruptedException
		{
			myClassParsingThread.addPath(classFileToProcess);
		}
	}

	private void doCompile(@jakarta.annotation.Nonnull final ModuleChunk chunk, @jakarta.annotation.Nonnull String outputDir, Map<File, FileObject> parsingInfo) throws IOException
	{
		myCompileContext.getProgressIndicator().checkCanceled();

		if(AccessRule.read(() -> chunk.getFilesToCompile().isEmpty()))
		{
			return; // should not invoke javac with empty sources list
		}

		BackendCompilerProcessBuilder processBuilder = null;
		int exitValue = 0;
		try
		{
			ClassParsingHandler classParsingHandler = new ClassParsingHandler(parsingInfo);

			myCompileContext.putUserData(CLASS_PARSING_HANDLER_KEY, classParsingHandler);

			processBuilder = myCompiler.prepareProcess(chunk, outputDir, myCompileContext);

			BackendCompilerMonitor monitor = myCompiler.createMonitor(processBuilder);

			GeneralCommandLine commandLine = AccessRule.read(processBuilder::buildCommandLine);

			assert commandLine != null;

			ProcessHandler process;
			try
			{
				process = processBuilder.createProcess(commandLine);
				if(monitor != null)
				{
					monitor.handleProcessStart(process);
				}
			}
			catch(ExecutionException e)
			{
				if(monitor != null)
				{
					monitor.disposeWithTree();
				}
				throw new IOException(e);
			}

			ExecutorService executorService = AppExecutorUtil.getAppExecutorService();
			Future<?> classParsingFuture = executorService.submit(classParsingHandler);

			OutputParser errorParser = myCompiler.createErrorParser(processBuilder, outputDir, process);
			CompilerParsingThread errorParsingThread = errorParser == null ? null : new CompilerParsingHandler(process, myCompileContext, errorParser, classParsingHandler, true, errorParser
					.isTrimLines());

			Future<?> errorParsingFuture = CompletableFuture.completedFuture(null);
			if(errorParsingThread != null)
			{
				errorParsingFuture = executorService.submit(errorParsingThread);
			}

			Future<?> outputParsingFuture = CompletableFuture.completedFuture(null);
			OutputParser outputParser = myCompiler.createOutputParser(processBuilder, outputDir);
			CompilerParsingThread outputParsingHandler = outputParser == null ? null : new CompilerParsingHandler(process, myCompileContext, outputParser, classParsingHandler, false, outputParser
					.isTrimLines());
			if(outputParsingHandler != null)
			{
				outputParsingFuture = executorService.submit(outputParsingHandler);
			}

			try
			{
				process.startNotify();

				process.waitFor();
			}
			catch(Throwable e)
			{
				if(monitor != null)
				{
					monitor.disposeWithTree();
				}

				throw new IOException(e);
			}
			finally
			{
				if(errorParsingThread != null)
				{
					errorParsingThread.stopParsing();
				}

				if(outputParsingHandler != null)
				{
					outputParsingHandler.stopParsing();
				}
				classParsingHandler.stopParsing();

				if(monitor != null)
				{
					monitor.disposeWithTree();
				}

				waitABit(classParsingFuture);
				waitABit(errorParsingFuture);
				waitABit(outputParsingFuture);

				registerParsingException(outputParsingHandler);
				registerParsingException(errorParsingThread);

				assert outputParsingHandler == null || !outputParsingHandler.processing;
				assert errorParsingThread == null || !errorParsingThread.processing;
				assert classParsingHandler == null || !classParsingHandler.processing;
			}
		}
		finally
		{
			if(processBuilder != null)
			{
				processBuilder.clearTempFiles();
			}
			compileFinished(exitValue, chunk, outputDir);
			myModuleName = null;
		}
	}

	private static void waitABit(final Future<?> threadFuture)
	{
		if(threadFuture != null)
		{
			try
			{
				threadFuture.get();
			}
			catch(InterruptedException | java.util.concurrent.ExecutionException ignored)
			{
				LOG.info("Thread interrupted", ignored);
			}
		}
	}

	private void registerParsingException(final CompilerParsingThread outputParsingThread)
	{
		Throwable error = outputParsingThread == null ? null : outputParsingThread.getError();
		if(error != null)
		{
			String message = error.getMessage();
			if(error instanceof CacheCorruptedException)
			{
				myCompileContext.requestRebuildNextTime(message);
			}
			else
			{
				myCompileContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
			}
		}
	}

	private void runTransformingCompilers(final ModuleChunk chunk)
	{
		final JavaSourceTransformingCompiler[] transformers = CompilerManager.getInstance(myProject).getCompilers(JavaSourceTransformingCompiler.class);
		if(transformers.length == 0)
		{
			return;
		}
		if(LOG.isDebugEnabled())
		{
			LOG.debug("Running transforming compilers...");
		}
		final Module[] modules = chunk.getModules();
		for(final JavaSourceTransformingCompiler transformer : transformers)
		{
			final Map<VirtualFile, VirtualFile> originalToCopyFileMap = new HashMap<>();
			final Application application = ApplicationManager.getApplication();
			application.invokeAndWait(new Runnable()
			{
				@Override
				public void run()
				{
					for(final Module module : modules)
					{
						for(final VirtualFile file : chunk.getFilesToCompile(module))
						{
							final VirtualFile untransformed = chunk.getOriginalFile(file);
							if(transformer.isTransformable(untransformed))
							{
								application.runWriteAction(new Runnable()
								{
									@Override
									public void run()
									{
										try
										{
											// if untransformed != file, the file is already a (possibly transformed) copy of the original
											// 'untransformed' file.
											// If this is the case, just use already created copy and do not copy file content once again
											final VirtualFile fileCopy = untransformed.equals(file) ? createFileCopy(getTempDir(module), file) :
													file;
											originalToCopyFileMap.put(file, fileCopy);
										}
										catch(IOException e)
										{
											// skip it
										}
									}
								});
							}
						}
					}
				}
			}, myCompileContext.getProgressIndicator().getModalityState());

			// do actual transform
			for(final Module module : modules)
			{
				final List<VirtualFile> filesToCompile = chunk.getFilesToCompile(module);
				for(int j = 0; j < filesToCompile.size(); j++)
				{
					final VirtualFile file = filesToCompile.get(j);
					final VirtualFile fileCopy = originalToCopyFileMap.get(file);
					if(fileCopy != null)
					{
						final boolean ok = transformer.transform(myCompileContext, fileCopy, chunk.getOriginalFile(file));
						if(ok)
						{
							chunk.substituteWithTransformedVersion(module, j, fileCopy);
						}
					}
				}
			}
		}
	}

	private VirtualFile createFileCopy(VirtualFile tempDir, final VirtualFile file) throws IOException
	{
		final String fileName = file.getName();
		if(tempDir.findChild(fileName) != null)
		{
			int idx = 0;
			while(true)
			{
				//noinspection HardCodedStringLiteral
				final String dirName = "dir" + idx++;
				final VirtualFile dir = tempDir.findChild(dirName);
				if(dir == null)
				{
					tempDir = tempDir.createChildDirectory(this, dirName);
					break;
				}
				if(dir.findChild(fileName) == null)
				{
					tempDir = dir;
					break;
				}
			}
		}
		return VirtualFileUtil.copyFile(this, file, tempDir);
	}

	private VirtualFile getTempDir(Module module) throws IOException
	{
		VirtualFile tempDir = myModuleToTempDirMap.get(module);
		if(tempDir == null)
		{
			final String projectName = myProject.getName();
			final String moduleName = module.getName();
			File tempDirectory = Files.createTempDirectory(projectName + "_" + moduleName).toFile();
			tempDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory);
			if(tempDir == null)
			{
				LOG.error("Cannot locate temp directory " + tempDirectory.getPath());
			}
			myModuleToTempDirMap.put(module, tempDir);
		}
		return tempDir;
	}

	private void compileFinished(int exitValue, final ModuleChunk chunk, final String outputDir)
	{
		if(exitValue != 0 && !myCompileContext.getProgressIndicator().isCanceled() && myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) == 0)
		{
			myCompileContext.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("error.compiler.internal.error", exitValue), null, -1, -1);
		}

		final List<File> toRefresh = new ArrayList<>();
		final Map<String, Collection<TranslatingCompiler.OutputItem>> results = new HashMap<>();
		try
		{
			final FileTypeManager typeManager = FileTypeManager.getInstance();
			final String outputDirPath = outputDir.replace(File.separatorChar, '/');
			try
			{
				for(final Module module : chunk.getModules())
				{
					for(final VirtualFile root : chunk.getSourceRoots(module))
					{
						final String packagePrefix = myProjectFileIndex.getPackageNameByDirectory(root);
						if(LOG.isDebugEnabled())
						{
							LOG.debug("Building output items for " + root.getPresentableUrl() + "; output dir = " + outputDirPath + "; packagePrefix" +
									" = \"" + packagePrefix + "\"");
						}
						buildOutputItemsList(outputDirPath, module, root, typeManager, root, packagePrefix, toRefresh, results);
					}
				}
			}
			catch(CacheCorruptedException e)
			{
				myCompileContext.requestRebuildNextTime(CompilerBundle.message("error.compiler.caches.corrupted"));
				if(LOG.isDebugEnabled())
				{
					LOG.debug(e);
				}
			}
		}
		finally
		{
			CompilerUtil.refreshIOFiles(toRefresh);
			for(Iterator<Map.Entry<String, Collection<TranslatingCompiler.OutputItem>>> it = results.entrySet().iterator(); it.hasNext(); )
			{
				Map.Entry<String, Collection<TranslatingCompiler.OutputItem>> entry = it.next();
				mySink.add(entry.getKey(), entry.getValue(), VirtualFile.EMPTY_ARRAY);
				it.remove(); // to free memory
			}
		}
		myFileNameToSourceMap.clear(); // clear the map before the next use
	}

	private void buildOutputItemsList(final String outputDir,
									  final Module module,
									  VirtualFile from,
									  final FileTypeManager typeManager,
									  final VirtualFile sourceRoot,
									  final String packagePrefix,
									  final List<File> filesToRefresh,
									  final Map<String, Collection<TranslatingCompiler.OutputItem>> results) throws CacheCorruptedException
	{
		final Ref<CacheCorruptedException> exRef = new Ref<>(null);
		final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
		final GlobalSearchScope srcRootScope = GlobalSearchScope.moduleScope(module).intersectWith(GlobalSearchScopesCore.directoryScope(myProject,
				sourceRoot, true));

		final Collection<FileType> registeredInputTypes = CompilerManager.getInstance(myProject).getRegisteredInputTypes(myTranslatingCompiler);

		final ContentIterator contentIterator = new ContentIterator()
		{
			@Override
			public boolean processFile(final VirtualFile child)
			{
				try
				{
					if(child.isValid())
					{
						if(!child.isDirectory() && registeredInputTypes.contains(child.getFileType()))
						{
							updateOutputItemsList(outputDir, child, sourceRoot, packagePrefix, filesToRefresh, results, srcRootScope);
						}
					}
					return true;
				}
				catch(CacheCorruptedException e)
				{
					exRef.set(e);
					return false;
				}
			}
		};
		if(fileIndex.isInContent(from))
		{
			// use file index for iteration to handle 'inner modules' and excludes properly
			fileIndex.iterateContentUnderDirectory(from, contentIterator);
		}
		else
		{
			// seems to be a root for generated sources
			VirtualFileUtil.visitChildrenRecursively(from, new VirtualFileVisitor()
			{
				@Override
				public boolean visitFile(@jakarta.annotation.Nonnull VirtualFile file)
				{
					if(!file.isDirectory())
					{
						contentIterator.processFile(file);
					}
					return true;
				}
			});
		}
		final CacheCorruptedException exc = exRef.get();
		if(exc != null)
		{
			throw exc;
		}
	}

	private void putName(String sourceFileName, int classQName, String relativePathToSource, String pathToClass)
	{
		if(LOG.isDebugEnabled())
		{
			LOG.debug("Registering [sourceFileName, relativePathToSource, pathToClass] = [" + sourceFileName + "; " + relativePathToSource +
					"; " + pathToClass + "]");
		}
		Set<CompiledClass> paths = myFileNameToSourceMap.get(sourceFileName);

		if(paths == null)
		{
			paths = new HashSet<>();
			myFileNameToSourceMap.put(sourceFileName, paths);
		}
		paths.add(new CompiledClass(classQName, relativePathToSource, pathToClass));
	}

	private void updateOutputItemsList(final String outputDir,
									   final VirtualFile srcFile,
									   VirtualFile sourceRoot,
									   final String packagePrefix,
									   final List<File> filesToRefresh,
									   Map<String, Collection<TranslatingCompiler.OutputItem>> results,
									   final GlobalSearchScope srcRootScope) throws CacheCorruptedException
	{
		CompositeDependencyCache dependencyCache = myCompileContext.getDependencyCache();
		JavaDependencyCache child = dependencyCache.findChild(JavaDependencyCache.class);
		final Cache newCache = child.getNewClassesCache();
		final Set<CompiledClass> paths = myFileNameToSourceMap.get(srcFile.getName());
		if(paths == null || paths.isEmpty())
		{
			return;
		}
		final String filePath = "/" + calcPackagePath(srcFile, sourceRoot, packagePrefix);
		for(final CompiledClass cc : paths)
		{
			myCompileContext.getProgressIndicator().checkCanceled();
			if(LOG.isDebugEnabled())
			{
				LOG.debug("Checking [pathToClass; relPathToSource] = " + cc);
			}

			boolean pathsEquals = FileUtil.pathsEqual(filePath, cc.relativePathToSource);
			if(!pathsEquals)
			{
				final String qName = child.resolve(cc.qName);
				if(qName != null)
				{
					pathsEquals = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>()
					{
						@Override
						public Boolean compute()
						{
							final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
							PsiClass psiClass = facade.findClass(qName, srcRootScope);
							if(psiClass == null)
							{
								final int dollarIndex = qName.indexOf("$");
								if(dollarIndex >= 0)
								{
									final String topLevelClassName = qName.substring(0, dollarIndex);
									psiClass = facade.findClass(topLevelClassName, srcRootScope);
								}
							}
							if(psiClass != null)
							{
								final VirtualFile vFile = psiClass.getContainingFile().getVirtualFile();
								return vFile != null && vFile.equals(srcFile);
							}
							return false;
						}
					});
				}
			}

			if(pathsEquals)
			{
				final String outputPath = cc.pathToClass.replace(File.separatorChar, '/');
				final Pair<String, String> realLocation = moveToRealLocation(outputDir, outputPath, srcFile, filesToRefresh);
				if(realLocation != null)
				{
					Collection<TranslatingCompiler.OutputItem> outputs = results.get(realLocation.getFirst());
					if(outputs == null)
					{
						outputs = new ArrayList<>();
						results.put(realLocation.getFirst(), outputs);
					}
					outputs.add(new OutputItemImpl(realLocation.getSecond(), srcFile));
					if(PACKAGE_ANNOTATION_FILE_NAME.equals(srcFile.getName()))
					{
						myProcessedPackageInfos.add(srcFile);
					}
					if(CompilerManager.MAKE_ENABLED)
					{
						newCache.setPath(cc.qName, realLocation.getSecond());
					}
					if(LOG.isDebugEnabled())
					{
						LOG.debug("Added output item: [outputDir; outputPath; sourceFile]  = [" + realLocation.getFirst() + "; " +
								realLocation.getSecond() + "; " + srcFile.getPresentableUrl() + "]");
					}
				}
				else
				{
					myCompileContext.addMessage(CompilerMessageCategory.ERROR, "Failed to copy from temporary location to output directory: " +
							outputPath + " (see idea.log for details)", null, -1, -1);
					if(LOG.isDebugEnabled())
					{
						LOG.debug("Failed to move to real location: " + outputPath + "; from " + outputDir);
					}
				}
			}
		}
	}

	/**
	 * @param srcFile
	 * @param sourceRoot
	 * @param packagePrefix
	 * @return A 'package'-path to a given src file relative to a specified root. "/" slashes must be used
	 */
	protected static String calcPackagePath(VirtualFile srcFile, VirtualFile sourceRoot, String packagePrefix)
	{
		final String prefix = packagePrefix != null && packagePrefix.length() > 0 ? packagePrefix.replace('.', '/') + "/" : "";
		return prefix + VirtualFileUtil.getRelativePath(srcFile, sourceRoot, '/');
	}

	@Nullable
	private Pair<String, String> moveToRealLocation(String tempOutputDir, String pathToClass, VirtualFile sourceFile, final List<File>
			filesToRefresh)
	{
		final Module module = myCompileContext.getModuleByFile(sourceFile);
		if(module == null)
		{
			final String message = "Cannot determine module for source file: " + sourceFile.getPresentableUrl() + ";\nCorresponding output file: " +
					pathToClass;
			LOG.info(message);
			myCompileContext.addMessage(CompilerMessageCategory.WARNING, message, sourceFile.getUrl(), -1, -1);
			// do not move: looks like source file has been invalidated, need recompilation
			return Pair.create(tempOutputDir, pathToClass);
		}
		final String realOutputDir;
		if(myCompileContext.isInTestSourceContent(sourceFile))
		{
			realOutputDir = getTestsOutputDir(module);
			LOG.assertTrue(realOutputDir != null);
		}
		else
		{
			realOutputDir = getOutputDir(module);
			LOG.assertTrue(realOutputDir != null);
		}

		if(FileUtil.pathsEqual(tempOutputDir, realOutputDir))
		{ // no need to move
			filesToRefresh.add(new File(pathToClass));
			return Pair.create(realOutputDir, pathToClass);
		}

		final String realPathToClass = realOutputDir + pathToClass.substring(tempOutputDir.length());
		final File fromFile = new File(pathToClass);
		final File toFile = new File(realPathToClass);

		boolean success = fromFile.renameTo(toFile);
		if(!success)
		{
			// assuming cause of the fail: intermediate dirs do not exist
			FileUtil.createParentDirs(toFile);
			// retry after making non-existent dirs
			success = fromFile.renameTo(toFile);
		}
		if(!success)
		{ // failed to move the file: e.g. because source and destination reside on different mountpoints.
			try
			{
				FileUtil.copy(fromFile, toFile, FilePermissionCopier.BY_NIO2);
				FileUtil.delete(fromFile);
				success = true;
			}
			catch(IOException e)
			{
				LOG.info(e);
				success = false;
			}
		}
		if(success)
		{
			filesToRefresh.add(toFile);
			return Pair.create(realOutputDir, realPathToClass);
		}
		return null;
	}

	private final Map<Module, String> myModuleToTestsOutput = new HashMap<>();

	private String getTestsOutputDir(final Module module)
	{
		if(myModuleToTestsOutput.containsKey(module))
		{
			return myModuleToTestsOutput.get(module);
		}
		final VirtualFile outputDirectory = myCompileContext.getModuleOutputDirectoryForTests(module);
		final String out = outputDirectory != null ? outputDirectory.getPath() : null;
		myModuleToTestsOutput.put(module, out);
		return out;
	}

	private final Map<Module, String> myModuleToOutput = new HashMap<>();

	private String getOutputDir(final Module module)
	{
		if(myModuleToOutput.containsKey(module))
		{
			return myModuleToOutput.get(module);
		}
		final VirtualFile outputDirectory = myCompileContext.getModuleOutputDirectory(module);
		final String out = outputDirectory != null ? outputDirectory.getPath() : null;
		myModuleToOutput.put(module, out);
		return out;
	}

	private void sourceFileProcessed()
	{
		myStatistics.incFilesCount();
		updateStatistics();
	}

	private void updateStatistics()
	{
		final String msg;
		String moduleName = myModuleName;
		if(moduleName != null)
		{
			msg = CompilerBundle.message("statistics.files.classes.module", myStatistics.getFilesCount(), myStatistics.getClassesCount(),
					moduleName);
		}
		else
		{
			msg = CompilerBundle.message("statistics.files.classes", myStatistics.getFilesCount(), myStatistics.getClassesCount());
		}
		myCompileContext.getProgressIndicator().setText2(msg);
		//myCompileContext.getProgressIndicator().setFraction(1.0* myProcessedFilesCount /myTotalFilesToCompile);
	}

	public class ClassParsingHandler implements Runnable
	{
		private final BlockingQueue<FileObject> myPaths = new ArrayBlockingQueue<>(50000);
		private CacheCorruptedException myError = null;
		private final JavaDependencyCache myJavaDependencyCache;
		private final Map<File, FileObject> myParsingInfo;

		private ClassParsingHandler(Map<File, FileObject> parsingInfo)
		{
			myParsingInfo = parsingInfo;
			myJavaDependencyCache = myCompileContext.getDependencyCache().findChild(JavaDependencyCache.class);
		}

		private volatile boolean processing;

		@Override
		public void run()
		{
			processing = true;
			try
			{
				while(true)
				{
					FileObject path = myPaths.take();

					if(path == ourStopThreadToken)
					{
						break;
					}
					processPath(path);
				}
			}
			catch(InterruptedException e)
			{
				LOG.error(e);
			}
			catch(CacheCorruptedException e)
			{
				myError = e;
			}
			finally
			{
				processing = false;
			}
		}

		public void addPath(FileObject path) throws CacheCorruptedException
		{
			if(myError != null)
			{
				throw myError;
			}
			myPaths.offer(path);
		}

		public void stopParsing()
		{
			myPaths.offer(ourStopThreadToken);
		}

		private void processPath(FileObject fileObject) throws CacheCorruptedException
		{
			File file = fileObject.getFile();
			final String path = file.getPath();
			try
			{
				byte[] fileContent = fileObject.getOrLoadContent();
				// the file is assumed to exist!
				int newClassQName = myJavaDependencyCache.reparseClassFile(file, fileContent);
				final Cache newClassesCache = myJavaDependencyCache.getNewClassesCache();
				final String sourceFileName = newClassesCache.getSourceFileName(newClassQName);
				final String qName = myJavaDependencyCache.resolve(newClassQName);
				String relativePathToSource = "/" + JavaMakeUtil.createRelativePathToSource(qName, sourceFileName);
				putName(sourceFileName, newClassQName, relativePathToSource, path);

				fileObject.setClassId(newClassQName);
				myParsingInfo.put(file, fileObject);
			}
			catch(ClsFormatException e)
			{
				final String m = e.getMessage();
				String message = CompilerBundle.message("error.bad.class.file.format", StringUtil.isEmpty(m) ? path : m + "\n" + path);
				myCompileContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
				LOG.info(e);
			}
			catch(IOException e)
			{
				myCompileContext.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
				LOG.info(e);
			}
			finally
			{
				myStatistics.incClassesCount();
				updateStatistics();
			}
		}
	}

	private static final class CompileStatistics
	{
		private static final Key<CompileStatistics> KEY = Key.create("_Compile_Statistics_");
		private int myClassesCount;
		private int myFilesCount;

		public int getClassesCount()
		{
			return myClassesCount;
		}

		public int incClassesCount()
		{
			return ++myClassesCount;
		}

		public int getFilesCount()
		{
			return myFilesCount;
		}

		public int incFilesCount()
		{
			return ++myFilesCount;
		}
	}
}
