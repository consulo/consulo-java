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
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.application.util.AsyncFileService;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.compiler.*;
import consulo.compiler.localize.CompilerLocalize;
import consulo.compiler.util.CompilerUtil;
import consulo.content.ContentIterator;
import consulo.java.compiler.impl.javaCompiler.BackendCompilerMonitor;
import consulo.java.compiler.impl.javaCompiler.BackendCompilerProcessBuilder;
import consulo.language.file.FileTypeManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScopesCore;
import consulo.localize.LocalizeValue;
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
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.Chunk;
import consulo.util.dataholder.Key;
import consulo.util.io.FilePermissionCopier;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.Couple;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.util.VirtualFileVisitor;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * @author Eugene Zhuravlev
 * @since Jan 24, 2003
 */
public class BackendCompilerWrapper {
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
    private static final String PACKAGE_ANNOTATION_FILE_NAME = "package-info.java";
    private static final FileObject ourStopThreadToken = new FileObject(new File(""), new byte[0]);
    public final Map<String, Set<CompiledClass>> myFileNameToSourceMap = new HashMap<>();
    private final Set<VirtualFile> myProcessedPackageInfos = new HashSet<>();
    private final CompileStatistics myStatistics;
    private volatile String myModuleName = null;
    private boolean myForceCompileTestsSeparately = false;

    public BackendCompilerWrapper(
        TranslatingCompiler translatingCompiler,
        Chunk<Module> chunk,
        Project project,
        List<VirtualFile> filesToCompile,
        CompileContextEx compileContext,
        BackendCompiler compiler,
        TranslatingCompiler.OutputSink sink
    ) {
        myTranslatingCompiler = translatingCompiler;
        myChunk = chunk;
        myProject = project;
        myCompiler = compiler;
        myCompileContext = compileContext;
        myFilesToCompile = filesToCompile;
        mySink = sink;
        myProjectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
        CompileStatistics stat = compileContext.getUserData(CompileStatistics.KEY);
        if (stat == null) {
            stat = new CompileStatistics();
            compileContext.putUserData(CompileStatistics.KEY, stat);
        }
        myStatistics = stat;
    }

    public void compile(Map<File, FileObject> parsingInfo) throws CompilerException, CacheCorruptedException {
        Application application = myProject.getApplication();
        try {
            if (!myFilesToCompile.isEmpty()) {
                if (application.isUnitTestMode()) {
                    saveTestData();
                }
                compileModules(buildModuleToFilesMap(myFilesToCompile), parsingInfo);
            }
        }
        catch (SecurityException e) {
            throw new CompilerException(CompilerLocalize.errorCompilerProcessNotStarted(e.getMessage()).get(), e);
        }
        catch (IllegalArgumentException e) {
            throw new CompilerException(e.getMessage(), e);
        }
        finally {
            for (VirtualFile file : myModuleToTempDirMap.values()) {
                if (file != null) {
                    File ioFile = new File(file.getPath());
                    getAsyncFileService().asyncDelete(ioFile);
                }
            }
            myModuleToTempDirMap.clear();
        }

        if (!myFilesToCompile.isEmpty() && myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
            // package-info.java hack
            List<TranslatingCompiler.OutputItem> outputs = new ArrayList<>();
            myProject.getApplication().runReadAction(() -> {
                for (VirtualFile file : myFilesToCompile) {
                    if (PACKAGE_ANNOTATION_FILE_NAME.equals(file.getName()) && !myProcessedPackageInfos.contains(file)) {
                        outputs.add(new OutputItemImpl(file));
                    }
                }
            });
            if (!outputs.isEmpty()) {
                mySink.add(null, outputs, VirtualFile.EMPTY_ARRAY);
            }
        }
    }

    public boolean isForceCompileTestsSeparately() {
        return myForceCompileTestsSeparately;
    }

    public void setForceCompileTestsSeparately(boolean forceCompileTestsSeparately) {
        myForceCompileTestsSeparately = forceCompileTestsSeparately;
    }

    private Map<Module, List<VirtualFile>> buildModuleToFilesMap(List<VirtualFile> filesToCompile) {
        if (myChunk.getNodes().size() == 1) {
            return Collections.singletonMap(myChunk.getNodes().iterator().next(), Collections.unmodifiableList(filesToCompile));
        }
        return CompilerUtil.buildModuleToFilesMap(myCompileContext, filesToCompile);
    }

    private AsyncFileService getAsyncFileService() {
        return Application.get().getInstance(AsyncFileService.class);
    }

    @RequiredUIAccess
    private void compileModules(Map<Module, List<VirtualFile>> moduleToFilesMap, Map<File, FileObject> parsingInfo)
        throws CompilerException {
        try {
            compileChunk(new ModuleChunk(myCompileContext, myChunk, moduleToFilesMap), parsingInfo);
        }
        catch (IOException e) {
            throw new CompilerException(e.getMessage(), e);
        }
    }

    @RequiredUIAccess
    private void compileChunk(ModuleChunk chunk, Map<File, FileObject> parsingInfo) throws IOException {
        String chunkPresentableName = getPresentableNameFor(chunk);
        myModuleName = chunkPresentableName;

        // validate encodings
        if (chunk.getModuleCount() > 1) {
            validateEncoding(chunk, chunkPresentableName);
            // todo: validation for bytecode target?
        }

        runTransformingCompilers(chunk);

        List<OutputDir> outs = new ArrayList<>();
        File fileToDelete = getOutputDirsToCompileTo(chunk, outs);

        try {
            for (OutputDir outputDir : outs) {
                chunk.setSourcesFilter(outputDir.getKind());

                doCompile(chunk, outputDir.getPath(), parsingInfo);
            }
        }
        finally {
            if (fileToDelete != null) {
                getAsyncFileService().asyncDelete(fileToDelete);
            }
        }
    }

    private void validateEncoding(ModuleChunk chunk, String chunkPresentableName) {
        CompilerEncodingService es = CompilerEncodingService.getInstance(myProject);
        Charset charset = null;
        for (Module module : chunk.getModules()) {
            Charset moduleCharset = es.getPreferredModuleEncoding(module);
            if (charset == null) {
                charset = moduleCharset;
            }
            else {
                if (!Comparing.equal(charset, moduleCharset)) {
                    // warn user
                    Charset chunkEncoding = CompilerEncodingService.getPreferredModuleEncoding(chunk);
                    StringBuilder message = new StringBuilder();
                    message.append("Modules in chunk [");
                    message.append(chunkPresentableName);
                    message.append("] configured to use different encodings.\n");
                    if (chunkEncoding != null) {
                        message.append("\"").append(chunkEncoding.name()).append("\" encoding will be used to compile the chunk");
                    }
                    else {
                        message.append("Default compiler encoding will be used to compile the chunk");
                    }
                    myCompileContext.newInfo(LocalizeValue.ofNullable(message.toString())).add();
                    break;
                }
            }
        }
    }

    private static String getPresentableNameFor(ModuleChunk chunk) {
        return Application.get().runReadAction((Supplier<String>) () -> {
            Module[] modules = chunk.getModules();
            StringBuilder moduleName = new StringBuilder(Math.min(128, modules.length * 8));
            for (int idx = 0; idx < modules.length; idx++) {
                Module module = modules[idx];
                if (idx > 0) {
                    moduleName.append(", ");
                }
                moduleName.append(module.getName());
                if (moduleName.length() > 128 && idx + 1 < modules.length /*name is already too long and seems to grow longer*/) {
                    moduleName.append("...");
                    break;
                }
            }
            return moduleName.toString();
        });
    }

    @Nullable
    private File getOutputDirsToCompileTo(ModuleChunk chunk, List<OutputDir> dirs) throws IOException {
        File fileToDelete = null;
        if (chunk.getModuleCount() == 1) { // optimization
            Module module = chunk.getModules()[0];
            myProject.getApplication().runReadAction(() -> {
                String sourcesOutputDir = getOutputDir(module);
                if (shouldCompileTestsSeparately(module)) {
                    if (sourcesOutputDir != null) {
                        dirs.add(new OutputDir(sourcesOutputDir, ModuleChunk.SOURCES));
                    }
                    String testsOutputDir = getTestsOutputDir(module);
                    if (testsOutputDir == null) {
                        LOG.error("Tests output dir is null for module \"" + module.getName() + "\"");
                    }
                    else {
                        dirs.add(new OutputDir(testsOutputDir, ModuleChunk.TEST_SOURCES));
                    }
                }
                else { // both sources and test sources go into the same output
                    if (sourcesOutputDir == null) {
                        LOG.error("Sources output dir is null for module \"" + module.getName() + "\"");
                    }
                    else {
                        dirs.add(new OutputDir(sourcesOutputDir, ModuleChunk.ALL_SOURCES));
                    }
                }
            });
        }
        else { // chunk has several modules
            File outputDir = Files.createTempDirectory("compileOutput").toFile();
            fileToDelete = outputDir;
            dirs.add(new OutputDir(outputDir.getPath(), ModuleChunk.ALL_SOURCES));
        }
        return fileToDelete;
    }


    private boolean shouldCompileTestsSeparately(Module module) {
        if (myForceCompileTestsSeparately) {
            return true;
        }
        String moduleTestOutputDirectory = getTestsOutputDir(module);
        if (moduleTestOutputDirectory == null) {
            return false;
        }
        // here we have test output specified
        String moduleOutputDirectory = getOutputDir(module);
        if (moduleOutputDirectory == null) {
            // only test output is specified, so should return true
            return true;
        }
        return !FileUtil.pathsEqual(moduleTestOutputDirectory, moduleOutputDirectory);
    }

    private void saveTestData() {
    /*ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (VirtualFile file : myFilesToCompile) {
          CompilerManagerImpl.addCompiledPath(file.getPath());
        }
      }
    }); */
    }

    private class CompilerParsingHandler extends CompilerParsingThread {
        private final ClassParsingHandler myClassParsingThread;

        private CompilerParsingHandler(
            ProcessHandler processHandler,
            CompileContext context,
            OutputParser outputParser,
            ClassParsingHandler classParsingThread,
            boolean readErrorStream,
            boolean trimLines
        ) {
            super(processHandler, outputParser, readErrorStream, trimLines, context);
            myClassParsingThread = classParsingThread;
        }

        @Override
        protected void processCompiledClass(FileObject classFileToProcess) throws CacheCorruptedException {
            myClassParsingThread.addPath(classFileToProcess);
        }
    }

    private void doCompile(ModuleChunk chunk, String outputDir, Map<File, FileObject> parsingInfo) throws IOException {
        myCompileContext.getProgressIndicator().checkCanceled();

        if (ReadAction.compute(() -> chunk.getFilesToCompile().isEmpty())) {
            return; // should not invoke javac with empty sources list
        }

        BackendCompilerProcessBuilder processBuilder = null;
        int exitValue = 0;
        try {
            ClassParsingHandler classParsingHandler = new ClassParsingHandler(parsingInfo);

            myCompileContext.putUserData(CLASS_PARSING_HANDLER_KEY, classParsingHandler);

            processBuilder = myCompiler.prepareProcess(chunk, outputDir, myCompileContext);

            BackendCompilerMonitor monitor = myCompiler.createMonitor(processBuilder);

            GeneralCommandLine commandLine = ReadAction.compute(processBuilder::buildCommandLine);

            assert commandLine != null;

            ProcessHandler process;
            try {
                process = processBuilder.createProcess(commandLine);
                if (monitor != null) {
                    monitor.handleProcessStart(process);
                }
            }
            catch (ExecutionException e) {
                if (monitor != null) {
                    monitor.disposeWithTree();
                }
                throw new IOException(e);
            }

            ExecutorService executorService = AppExecutorUtil.getAppExecutorService();
            Future<?> classParsingFuture = executorService.submit(classParsingHandler);

            OutputParser errorParser = myCompiler.createErrorParser(processBuilder, outputDir, process);
            CompilerParsingThread errorParsingThread = errorParser == null ? null
                : new CompilerParsingHandler(process, myCompileContext, errorParser, classParsingHandler, true, errorParser.isTrimLines());

            Future<?> errorParsingFuture = CompletableFuture.completedFuture(null);
            if (errorParsingThread != null) {
                errorParsingFuture = executorService.submit(errorParsingThread);
            }

            Future<?> outputParsingFuture = CompletableFuture.completedFuture(null);
            OutputParser outputParser = myCompiler.createOutputParser(processBuilder, outputDir);
            CompilerParsingThread outputParsingHandler = outputParser == null ? null
                : new CompilerParsingHandler(
                process,
                myCompileContext,
                outputParser,
                classParsingHandler,
                false,
                outputParser.isTrimLines()
            );
            if (outputParsingHandler != null) {
                outputParsingFuture = executorService.submit(outputParsingHandler);
            }

            try {
                process.startNotify();

                process.waitFor();
            }
            catch (Throwable e) {
                if (monitor != null) {
                    monitor.disposeWithTree();
                }

                throw new IOException(e);
            }
            finally {
                if (errorParsingThread != null) {
                    errorParsingThread.stopParsing();
                }

                if (outputParsingHandler != null) {
                    outputParsingHandler.stopParsing();
                }
                classParsingHandler.stopParsing();

                if (monitor != null) {
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
        finally {
            if (processBuilder != null) {
                processBuilder.clearTempFiles();
            }
            compileFinished(exitValue, chunk, outputDir);
            myModuleName = null;
        }
    }

    private static void waitABit(Future<?> threadFuture) {
        if (threadFuture != null) {
            try {
                threadFuture.get();
            }
            catch (InterruptedException | java.util.concurrent.ExecutionException ignored) {
                LOG.info("Thread interrupted", ignored);
            }
        }
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private void registerParsingException(CompilerParsingThread outputParsingThread) {
        Throwable error = outputParsingThread == null ? null : outputParsingThread.getError();
        if (error != null) {
            LocalizeValue message = LocalizeValue.ofNullable(error.getMessage());
            if (error instanceof CacheCorruptedException) {
                myCompileContext.requestRebuildNextTime(message);
            }
            else {
                myCompileContext.newError(message).add();
            }
        }
    }

    @RequiredUIAccess
    private void runTransformingCompilers(ModuleChunk chunk) {
        JavaSourceTransformingCompiler[] transformers =
            CompilerManager.getInstance(myProject).getCompilers(JavaSourceTransformingCompiler.class);
        if (transformers.length == 0) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Running transforming compilers...");
        }
        Module[] modules = chunk.getModules();
        for (JavaSourceTransformingCompiler transformer : transformers) {
            Map<VirtualFile, VirtualFile> originalToCopyFileMap = new HashMap<>();
            Application application = Application.get();
            application.invokeAndWait(
                () -> {
                    for (Module module : modules) {
                        for (VirtualFile file : chunk.getFilesToCompile(module)) {
                            VirtualFile untransformed = chunk.getOriginalFile(file);
                            if (transformer.isTransformable(untransformed)) {
                                application.runWriteAction(() -> {
                                    try {
                                        // if untransformed != file, the file is already a (possibly transformed) copy of the original
                                        // 'untransformed' file.
                                        // If this is the case, just use already created copy and do not copy file content once again
                                        VirtualFile fileCopy =
                                            untransformed.equals(file) ? createFileCopy(getTempDir(module), file) : file;
                                        originalToCopyFileMap.put(file, fileCopy);
                                    }
                                    catch (IOException e) {
                                        // skip it
                                    }
                                });
                            }
                        }
                    }
                },
                myCompileContext.getProgressIndicator().getModalityState()
            );

            // do actual transform
            for (Module module : modules) {
                List<VirtualFile> filesToCompile = chunk.getFilesToCompile(module);
                for (int j = 0; j < filesToCompile.size(); j++) {
                    VirtualFile file = filesToCompile.get(j);
                    VirtualFile fileCopy = originalToCopyFileMap.get(file);
                    if (fileCopy != null) {
                        boolean ok = transformer.transform(myCompileContext, fileCopy, chunk.getOriginalFile(file));
                        if (ok) {
                            chunk.substituteWithTransformedVersion(module, j, fileCopy);
                        }
                    }
                }
            }
        }
    }

    @RequiredWriteAction
    private VirtualFile createFileCopy(VirtualFile tempDir, VirtualFile file) throws IOException {
        String fileName = file.getName();
        if (tempDir.findChild(fileName) != null) {
            int idx = 0;
            while (true) {
                //noinspection HardCodedStringLiteral
                String dirName = "dir" + idx++;
                VirtualFile dir = tempDir.findChild(dirName);
                if (dir == null) {
                    tempDir = tempDir.createChildDirectory(this, dirName);
                    break;
                }
                if (dir.findChild(fileName) == null) {
                    tempDir = dir;
                    break;
                }
            }
        }
        return VirtualFileUtil.copyFile(this, file, tempDir);
    }

    private VirtualFile getTempDir(Module module) throws IOException {
        VirtualFile tempDir = myModuleToTempDirMap.get(module);
        if (tempDir == null) {
            String projectName = myProject.getName();
            String moduleName = module.getName();
            File tempDirectory = Files.createTempDirectory(projectName + "_" + moduleName).toFile();
            tempDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory);
            if (tempDir == null) {
                LOG.error("Cannot locate temp directory " + tempDirectory.getPath());
            }
            myModuleToTempDirMap.put(module, tempDir);
        }
        return tempDir;
    }

    private void compileFinished(int exitValue, ModuleChunk chunk, String outputDir) {
        if (exitValue != 0 && !myCompileContext.getProgressIndicator().isCanceled()
            && myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
            myCompileContext.newError(CompilerLocalize.errorCompilerInternalError(exitValue)).add();
        }

        List<File> toRefresh = new ArrayList<>();
        Map<String, Collection<TranslatingCompiler.OutputItem>> results = new HashMap<>();
        try {
            FileTypeManager typeManager = FileTypeManager.getInstance();
            String outputDirPath = outputDir.replace(File.separatorChar, '/');
            try {
                for (Module module : chunk.getModules()) {
                    for (VirtualFile root : chunk.getSourceRoots(module)) {
                        String packagePrefix = myProjectFileIndex.getPackageNameByDirectory(root);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(
                                "Building output items for ", root.getPresentableUrl(), "; output dir = ", outputDirPath,
                                "; packagePrefix = \"", packagePrefix, "\""
                            );
                        }
                        buildOutputItemsList(outputDirPath, module, root, typeManager, root, packagePrefix, toRefresh, results);
                    }
                }
            }
            catch (CacheCorruptedException e) {
                myCompileContext.requestRebuildNextTime(CompilerLocalize.errorCompilerCachesCorrupted());
                if (LOG.isDebugEnabled()) {
                    LOG.debug(e);
                }
            }
        }
        finally {
            CompilerUtil.refreshIOFiles(toRefresh);
            for (Iterator<Map.Entry<String, Collection<TranslatingCompiler.OutputItem>>> it = results.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Collection<TranslatingCompiler.OutputItem>> entry = it.next();
                mySink.add(entry.getKey(), entry.getValue(), VirtualFile.EMPTY_ARRAY);
                it.remove(); // to free memory
            }
        }
        myFileNameToSourceMap.clear(); // clear the map before the next use
    }

    private void buildOutputItemsList(
        String outputDir,
        Module module,
        VirtualFile from,
        FileTypeManager typeManager,
        VirtualFile sourceRoot,
        String packagePrefix,
        List<File> filesToRefresh,
        Map<String, Collection<TranslatingCompiler.OutputItem>> results
    ) throws CacheCorruptedException {
        SimpleReference<CacheCorruptedException> exRef = new SimpleReference<>(null);
        ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
        GlobalSearchScope srcRootScope = GlobalSearchScope.moduleScope(module)
            .intersectWith(GlobalSearchScopesCore.directoryScope(myProject, sourceRoot, true));

        Collection<FileType> registeredInputTypes = CompilerManager.getInstance(myProject).getRegisteredInputTypes(myTranslatingCompiler);

        final ContentIterator contentIterator = child -> {
            try {
                if (child.isValid()) {
                    if (!child.isDirectory() && registeredInputTypes.contains(child.getFileType())) {
                        updateOutputItemsList(outputDir, child, sourceRoot, packagePrefix, filesToRefresh, results, srcRootScope);
                    }
                }
                return true;
            }
            catch (CacheCorruptedException e) {
                exRef.set(e);
                return false;
            }
        };
        if (fileIndex.isInContent(from)) {
            // use file index for iteration to handle 'inner modules' and excludes properly
            fileIndex.iterateContentUnderDirectory(from, contentIterator);
        }
        else {
            // seems to be a root for generated sources
            VirtualFileUtil.visitChildrenRecursively(from, new VirtualFileVisitor() {
                @Override
                public boolean visitFile(VirtualFile file) {
                    if (!file.isDirectory()) {
                        contentIterator.processFile(file);
                    }
                    return true;
                }
            });
        }
        CacheCorruptedException exc = exRef.get();
        if (exc != null) {
            throw exc;
        }
    }

    private void putName(String sourceFileName, int classQName, String relativePathToSource, String pathToClass) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Registering [sourceFileName, relativePathToSource, pathToClass] = [" + sourceFileName + "; " + relativePathToSource +
                "; " + pathToClass + "]");
        }
        Set<CompiledClass> paths = myFileNameToSourceMap.get(sourceFileName);

        if (paths == null) {
            paths = new HashSet<>();
            myFileNameToSourceMap.put(sourceFileName, paths);
        }
        paths.add(new CompiledClass(classQName, relativePathToSource, pathToClass));
    }

    private void updateOutputItemsList(
        String outputDir,
        VirtualFile srcFile,
        VirtualFile sourceRoot,
        String packagePrefix,
        List<File> filesToRefresh,
        Map<String, Collection<TranslatingCompiler.OutputItem>> results,
        GlobalSearchScope srcRootScope
    ) throws CacheCorruptedException {
        CompositeDependencyCache dependencyCache = myCompileContext.getDependencyCache();
        JavaDependencyCache child = dependencyCache.findChild(JavaDependencyCache.class);
        Cache newCache = child.getNewClassesCache();
        Set<CompiledClass> paths = myFileNameToSourceMap.get(srcFile.getName());
        if (paths == null || paths.isEmpty()) {
            return;
        }
        String filePath = "/" + calcPackagePath(srcFile, sourceRoot, packagePrefix);
        for (CompiledClass cc : paths) {
            myCompileContext.getProgressIndicator().checkCanceled();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Checking [pathToClass; relPathToSource] = " + cc);
            }

            boolean pathsEquals = FileUtil.pathsEqual(filePath, cc.relativePathToSource);
            if (!pathsEquals) {
                String qName = child.resolve(cc.qName);
                if (qName != null) {
                    pathsEquals = myProject.getApplication().runReadAction((Supplier<Boolean>) () -> {
                        JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
                        PsiClass psiClass = facade.findClass(qName, srcRootScope);
                        if (psiClass == null) {
                            int dollarIndex = qName.indexOf("$");
                            if (dollarIndex >= 0) {
                                String topLevelClassName = qName.substring(0, dollarIndex);
                                psiClass = facade.findClass(topLevelClassName, srcRootScope);
                            }
                        }
                        if (psiClass != null) {
                            VirtualFile vFile = psiClass.getContainingFile().getVirtualFile();
                            return vFile != null && vFile.equals(srcFile);
                        }
                        return false;
                    });
                }
            }

            if (pathsEquals) {
                String outputPath = cc.pathToClass.replace(File.separatorChar, '/');
                Couple<String> realLocation = moveToRealLocation(outputDir, outputPath, srcFile, filesToRefresh);
                if (realLocation != null) {
                    Collection<TranslatingCompiler.OutputItem> outputs = results.get(realLocation.getFirst());
                    if (outputs == null) {
                        outputs = new ArrayList<>();
                        results.put(realLocation.getFirst(), outputs);
                    }
                    outputs.add(new OutputItemImpl(realLocation.getSecond(), srcFile));
                    if (PACKAGE_ANNOTATION_FILE_NAME.equals(srcFile.getName())) {
                        myProcessedPackageInfos.add(srcFile);
                    }
                    if (CompilerManager.MAKE_ENABLED) {
                        newCache.setPath(cc.qName, realLocation.getSecond());
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Added output item: [outputDir; outputPath; sourceFile]  = [" + realLocation.getFirst() + "; " +
                            realLocation.getSecond() + "; " + srcFile.getPresentableUrl() + "]");
                    }
                }
                else {
                    myCompileContext.newError(LocalizeValue.localizeTODO(
                        "Failed to copy from temporary location to output directory: " + outputPath + " (see idea.log for details)"
                    )).add();
                    if (LOG.isDebugEnabled()) {
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
    protected static String calcPackagePath(VirtualFile srcFile, VirtualFile sourceRoot, String packagePrefix) {
        String prefix = packagePrefix != null && packagePrefix.length() > 0 ? packagePrefix.replace('.', '/') + "/" : "";
        return prefix + VirtualFileUtil.getRelativePath(srcFile, sourceRoot, '/');
    }

    @Nullable
    private Couple<String> moveToRealLocation(
        String tempOutputDir,
        String pathToClass,
        VirtualFile sourceFile,
        List<File> filesToRefresh
    ) {
        Module module = myCompileContext.getModuleByFile(sourceFile);
        if (module == null) {
            String message = "Cannot determine module for source file: " + sourceFile.getPresentableUrl() + ";\n" +
                "Corresponding output file: " + pathToClass;
            LOG.info(message);
            myCompileContext.newWarning(LocalizeValue.localizeTODO(message)).add();
            // do not move: looks like source file has been invalidated, need recompilation
            return Couple.of(tempOutputDir, pathToClass);
        }
        String realOutputDir;
        if (myCompileContext.isInTestSourceContent(sourceFile)) {
            realOutputDir = getTestsOutputDir(module);
            LOG.assertTrue(realOutputDir != null);
        }
        else {
            realOutputDir = getOutputDir(module);
            LOG.assertTrue(realOutputDir != null);
        }

        if (FileUtil.pathsEqual(tempOutputDir, realOutputDir)) { // no need to move
            filesToRefresh.add(new File(pathToClass));
            return Couple.of(realOutputDir, pathToClass);
        }

        String realPathToClass = realOutputDir + pathToClass.substring(tempOutputDir.length());
        File fromFile = new File(pathToClass);
        File toFile = new File(realPathToClass);

        boolean success = fromFile.renameTo(toFile);
        if (!success) {
            // assuming cause of the fail: intermediate dirs do not exist
            FileUtil.createParentDirs(toFile);
            // retry after making non-existent dirs
            success = fromFile.renameTo(toFile);
        }
        if (!success) { // failed to move the file: e.g. because source and destination reside on different mount-points.
            try {
                FileUtil.copy(fromFile, toFile, FilePermissionCopier.BY_NIO2);
                FileUtil.delete(fromFile);
                success = true;
            }
            catch (IOException e) {
                LOG.info(e);
                success = false;
            }
        }
        if (success) {
            filesToRefresh.add(toFile);
            return Couple.of(realOutputDir, realPathToClass);
        }
        return null;
    }

    private final Map<Module, String> myModuleToTestsOutput = new HashMap<>();

    private String getTestsOutputDir(Module module) {
        if (myModuleToTestsOutput.containsKey(module)) {
            return myModuleToTestsOutput.get(module);
        }
        VirtualFile outputDirectory = myCompileContext.getModuleOutputDirectoryForTests(module);
        String out = outputDirectory != null ? outputDirectory.getPath() : null;
        myModuleToTestsOutput.put(module, out);
        return out;
    }

    private final Map<Module, String> myModuleToOutput = new HashMap<>();

    private String getOutputDir(Module module) {
        if (myModuleToOutput.containsKey(module)) {
            return myModuleToOutput.get(module);
        }
        VirtualFile outputDirectory = myCompileContext.getModuleOutputDirectory(module);
        String out = outputDirectory != null ? outputDirectory.getPath() : null;
        myModuleToOutput.put(module, out);
        return out;
    }

    private void sourceFileProcessed() {
        myStatistics.incFilesCount();
        updateStatistics();
    }

    private void updateStatistics() {
        String moduleName = myModuleName;
        LocalizeValue msg = moduleName != null
            ? CompilerLocalize.statisticsFilesClassesModule(myStatistics.getFilesCount(), myStatistics.getClassesCount(), moduleName)
            : CompilerLocalize.statisticsFilesClasses(myStatistics.getFilesCount(), myStatistics.getClassesCount());
        myCompileContext.getProgressIndicator().setText2(msg);
        //myCompileContext.getProgressIndicator().setFraction(1.0* myProcessedFilesCount /myTotalFilesToCompile);
    }

    public class ClassParsingHandler implements Runnable {
        private final BlockingQueue<FileObject> myPaths = new ArrayBlockingQueue<>(50000);
        private CacheCorruptedException myError = null;
        private final JavaDependencyCache myJavaDependencyCache;
        private final Map<File, FileObject> myParsingInfo;

        private ClassParsingHandler(Map<File, FileObject> parsingInfo) {
            myParsingInfo = parsingInfo;
            myJavaDependencyCache = myCompileContext.getDependencyCache().findChild(JavaDependencyCache.class);
        }

        private volatile boolean processing;

        @Override
        public void run() {
            processing = true;
            try {
                while (true) {
                    FileObject path = myPaths.take();

                    if (path == ourStopThreadToken) {
                        break;
                    }
                    processPath(path);
                }
            }
            catch (InterruptedException e) {
                LOG.error(e);
            }
            catch (CacheCorruptedException e) {
                myError = e;
            }
            finally {
                processing = false;
            }
        }

        public void addPath(FileObject path) throws CacheCorruptedException {
            if (myError != null) {
                throw myError;
            }
            myPaths.offer(path);
        }

        public void stopParsing() {
            myPaths.offer(ourStopThreadToken);
        }

        private void processPath(FileObject fileObject) throws CacheCorruptedException {
            File file = fileObject.getFile();
            String path = file.getPath();
            try {
                byte[] fileContent = fileObject.getOrLoadContent();
                // the file is assumed to exist!
                int newClassQName = myJavaDependencyCache.reparseClassFile(file, fileContent);
                Cache newClassesCache = myJavaDependencyCache.getNewClassesCache();
                String sourceFileName = newClassesCache.getSourceFileName(newClassQName);
                String qName = myJavaDependencyCache.resolve(newClassQName);
                String relativePathToSource = "/" + JavaMakeUtil.createRelativePathToSource(qName, sourceFileName);
                putName(sourceFileName, newClassQName, relativePathToSource, path);

                fileObject.setClassId(newClassQName);
                myParsingInfo.put(file, fileObject);
            }
            catch (ClsFormatException e) {
                String m = e.getMessage();
                myCompileContext.newError(CompilerLocalize.errorBadClassFileFormat(StringUtil.isEmpty(m) ? path : m + "\n" + path)).add();
                LOG.info(e);
            }
            catch (IOException e) {
                myCompileContext.newError(LocalizeValue.ofNullable(e.getMessage())).add();
                LOG.info(e);
            }
            finally {
                myStatistics.incClassesCount();
                updateStatistics();
            }
        }
    }

    private static final class CompileStatistics {
        private static final Key<CompileStatistics> KEY = Key.create("_Compile_Statistics_");
        private int myClassesCount;
        private int myFilesCount;

        public int getClassesCount() {
            return myClassesCount;
        }

        public int incClassesCount() {
            return ++myClassesCount;
        }

        public int getFilesCount() {
            return myFilesCount;
        }

        public int incFilesCount() {
            return ++myFilesCount;
        }
    }
}
