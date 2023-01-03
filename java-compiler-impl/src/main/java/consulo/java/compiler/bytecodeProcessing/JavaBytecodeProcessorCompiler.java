/*
 * Copyright 2013-2016 must-be.org
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

package consulo.java.compiler.bytecodeProcessing;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.java.compiler.impl.cache.Cache;
import com.intellij.java.compiler.impl.cache.JavaDependencyCache;
import com.intellij.java.compiler.impl.javaCompiler.FileObject;
import com.intellij.java.compiler.impl.javaCompiler.JavaCompiler;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.compiler.*;
import consulo.compiler.scope.CompileScope;
import consulo.content.ContentFolderTypeProvider;
import consulo.java.compiler.JavaCompilerUtil;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.util.collection.Chunk;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * @author VISTALL
 * @since 28-Sep-16
 */
@ExtensionImpl(id = "java-bytecode-processor")
public class JavaBytecodeProcessorCompiler implements ClassInstrumentingCompiler {
  private static final Logger LOGGER = Logger.getInstance(JavaBytecodeProcessorCompiler.class);

  private static class MyProcessingItem implements ProcessingItem {
    private final File myFile;
    private final FileObject myFileObject;
    private final Module myModule;
    private ValidityState myValidityState;

    public MyProcessingItem(File file, FileObject fileObject, Module module) {
      myFile = file;
      myFileObject = fileObject;
      myModule = module;
      myValidityState = new TimestampValidityState(file.lastModified());
    }

    private void save(byte[] data) throws IOException {
      myFileObject.save(data);
      myValidityState = new TimestampValidityState(myFile.lastModified());
    }

    @Nonnull
    @Override
    public File getFile() {
      return myFile;
    }

    @Nullable
    @Override
    public ValidityState getValidityState() {
      return myValidityState;
    }
  }

  @Nonnull
  @Override
  public ProcessingItem[] getProcessingItems(CompileContext compileContext) {
    List<ProcessingItem> list = new LinkedList<>();
    Module[] affectedModules = compileContext.getCompileScope().getAffectedModules();
    for (Module affectedModule : affectedModules) {
      scanDirectory(compileContext, affectedModule, ProductionContentFolderTypeProvider.getInstance(), list);
      scanDirectory(compileContext, affectedModule, TestContentFolderTypeProvider.getInstance(), list);
    }
    return list.toArray(new ProcessingItem[list.size()]);
  }

  private static void scanDirectory(CompileContext compileContext,
                                    Module module,
                                    ContentFolderTypeProvider provider,
                                    List<ProcessingItem> list) {
    Map<File, FileObject> map = compileContext.getUserData(JavaCompiler.ourOutputFileParseInfo);
    if (map == null) {
      return;
    }

    JavaModuleExtension extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
    if (extension == null) {
      return;
    }

    String compilerOutputUrl = ModuleCompilerPathsManager.getInstance(module).getCompilerOutputUrl(provider);
    if (compilerOutputUrl == null) {
      return;
    }

    String path = VirtualFileUtil.urlToPath(compilerOutputUrl);
    File outputDir = new File(path);

    FileUtil.visitFiles(outputDir, file -> {
      FileObject fileObject = map.get(file);
      if (fileObject == null) {
        return true;
      }

      list.add(new MyProcessingItem(file, fileObject, module));
      return true;
    });
  }

  @Override
  public ProcessingItem[] process(CompileContext compileContext, ProcessingItem[] processingItems) {
    JavaDependencyCache dependencyCache = ((CompileContextEx)compileContext).getDependencyCache().findChild(JavaDependencyCache.class);

    final Cache cache;
    try {
      cache = dependencyCache.getCache();
    }
    catch (CacheCorruptedException e) {
      compileContext.addMessage(CompilerMessageCategory.ERROR, "Cache corrupted. Please rebuild project", null, -1, -1);
      return ProcessingItem.EMPTY_ARRAY;
    }

    Module[] affectedModules = compileContext.getCompileScope().getAffectedModules();
    for (Module affectedModule : affectedModules) {
      InstrumentationClassFinder classFinder = createClassFinder(compileContext, affectedModule);

      for (ProcessingItem processingItem : processingItems) {
        MyProcessingItem temp = (MyProcessingItem)processingItem;

        if (temp.myModule != affectedModule) {
          continue;
        }

        try {
          FileObject fileObject = temp.myFileObject;
          for (JavaBytecodeProcessor processor : Application.get().getExtensionList(JavaBytecodeProcessor.class)) {
            byte[] bytes = processor.processClassFile(compileContext,
                                                      affectedModule,
                                                      dependencyCache,
                                                      cache,
                                                      fileObject.getClassId(),
                                                      temp.myFile,
                                                      fileObject::getOrLoadContent,
                                                      classFinder);
            if (bytes != null) {
              temp.save(bytes);
            }
          }
        }
        catch (CacheCorruptedException e) {
          compileContext.addMessage(CompilerMessageCategory.ERROR, "Cache corrupted. Please rebuild project", null, -1, -1);
          return ProcessingItem.EMPTY_ARRAY;
        }
        catch (IOException e) {
          LOGGER.error(e);
        }
      }
    }
    return processingItems;
  }

  @Nonnull
  public static InstrumentationClassFinder createClassFinder(@Nonnull CompileContext context, @Nonnull final Module module) {
    ModuleChunk moduleChunk =
      new ModuleChunk((CompileContextEx)context, new Chunk<>(module), Collections.<Module, List<VirtualFile>>emptyMap());

    Set<VirtualFile> compilationBootClasspath = JavaCompilerUtil.getCompilationBootClasspath(context, moduleChunk);
    Set<VirtualFile> compilationClasspath = JavaCompilerUtil.getCompilationClasspath(context, moduleChunk);

    return new InstrumentationClassFinder(toUrls(compilationBootClasspath), toUrls(compilationClasspath));
  }

  @Nonnull
  private static URL[] toUrls(@Nonnull Set<VirtualFile> files) {
    List<URL> urls = new ArrayList<>(files.size());
    for (VirtualFile file : files) {
      try {
        File javaFile = VirtualFileUtil.virtualToIoFile(file);
        urls.add(javaFile.getCanonicalFile().toURI().toURL());
      }
      catch (Exception e) {
        LOGGER.error(e);
      }
    }
    return urls.toArray(new URL[urls.size()]);
  }

  @Nonnull
  @Override
  public String getDescription() {
    return "JavaBytecodeCompiler";
  }

  @Override
  public boolean validateConfiguration(CompileScope compileScope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(DataInput dataInput) throws IOException {
    return TimestampValidityState.load(dataInput);
  }
}
