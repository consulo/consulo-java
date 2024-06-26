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

package com.intellij.java.compiler.impl.javaCompiler;

import com.intellij.java.compiler.impl.CompilerException;
import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.JavaFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.*;
import consulo.compiler.localize.CompilerLocalize;
import consulo.compiler.scope.CompileScope;
import consulo.content.ContentFolderTypeProvider;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.content.ProductionResourceContentFolderTypeProvider;
import consulo.language.content.TestResourceContentFolderTypeProvider;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.util.collection.Chunk;
import consulo.util.collection.Maps;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.ExceptionUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/*
 * @author: Eugene Zhuravlev
 * Date: Jan 17, 2003
 * Time: 3:22:59 PM
 */
@ExtensionImpl(id = "java-compiler")
public class JavaCompiler implements TranslatingCompiler {
  private static final Logger LOGGER = Logger.getInstance(JavaCompiler.class);

  public static final Key<Map<File, FileObject>> ourOutputFileParseInfo = Key.create("ourOutputFileParseInfo");

  private final Project myProject;

  @Inject
  public JavaCompiler(Project project) {
    myProject = project;
  }

  @Override
  @Nonnull
  public String getDescription() {
    return CompilerLocalize.javaCompilerDescription().get();
  }

  @Override
  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    return file.getFileType() == JavaFileType.INSTANCE;
  }

  @Override
  public void compile(CompileContext context, Chunk<Module> moduleChunk, VirtualFile[] files, OutputSink sink) {
    boolean found = false;
    for (Module module : moduleChunk.getNodes()) {
      JavaModuleExtension extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
      if (extension != null) {
        found = true;
        break;
      }
    }

    if (!found) {
      return;
    }

    Map<File, FileObject> parsingInfo = Maps.newHashMap(FileUtil.FILE_HASHING_STRATEGY);
    context.putUserData(ourOutputFileParseInfo, parsingInfo);

    final BackendCompiler backEndCompiler = getBackEndCompiler();
    final BackendCompilerWrapper wrapper = new BackendCompilerWrapper(
      this,
      moduleChunk,
      myProject,
      filterResourceFiles(context, files),
      (CompileContextEx)context,
      backEndCompiler,
      sink
    );
    try {
      wrapper.compile(parsingInfo);
    }
    catch (CompilerException e) {
      context.addMessage(CompilerMessageCategory.ERROR, ExceptionUtil.getThrowableText(e), null, -1, -1);
      LOGGER.info(e);
    }
    catch (CacheCorruptedException e) {
      LOGGER.info(e);
      context.requestRebuildNextTime(e.getMessage());
    }
  }

  @Nonnull
  private static List<VirtualFile> filterResourceFiles(CompileContext compileContext, VirtualFile[] virtualFiles) {
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(compileContext.getProject());

    List<VirtualFile> list = new ArrayList<>(virtualFiles.length);
    for (VirtualFile file : virtualFiles) {
      ContentFolderTypeProvider provider = fileIndex.getContentFolderTypeForFile(file);
      if (provider == ProductionResourceContentFolderTypeProvider.getInstance() || provider == TestResourceContentFolderTypeProvider.getInstance()) {
        continue;
      }
      list.add(file);
    }
    return list;
  }

  @Nonnull
  @Override
  public FileType[] getInputFileTypes() {
    return new FileType[]{JavaFileType.INSTANCE};
  }

  @Nonnull
  @Override
  public FileType[] getOutputFileTypes() {
    return new FileType[]{JavaClassFileType.INSTANCE};
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return getBackEndCompiler().checkCompiler(scope);
  }

  @Override
  public void registerCompilableFileTypes(@Nonnull Consumer<FileType> fileTypeConsumer) {
    fileTypeConsumer.accept(JavaFileType.INSTANCE);
  }

  private BackendCompiler getBackEndCompiler() {
    return JavaCompilerConfiguration.getInstance(myProject).getActiveCompiler();
  }
}
