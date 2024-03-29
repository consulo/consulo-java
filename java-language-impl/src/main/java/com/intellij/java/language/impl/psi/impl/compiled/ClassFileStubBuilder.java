/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.psi.compiled.ClassFileDecompiler;
import com.intellij.java.language.psi.compiled.ClassFileDecompilers;
import com.intellij.java.language.util.cls.ClsFormatException;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.language.psi.stub.BinaryFileStubBuilder;
import consulo.language.psi.stub.FileContent;
import consulo.language.psi.stub.PsiFileStub;
import consulo.language.psi.stub.StubElement;
import consulo.logging.Logger;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author max
 */
@ExtensionImpl
public class ClassFileStubBuilder implements BinaryFileStubBuilder {
  private static final Logger LOG = Logger.getInstance(ClassFileStubBuilder.class);

  public static final int STUB_VERSION = 22;

  private final Application myApplication;

  @Inject
  public ClassFileStubBuilder(Application application) {
    myApplication = application;
  }

  @Nonnull
  @Override
  public FileType getFileType() {
    return JavaClassFileType.INSTANCE;
  }

  @Override
  public boolean acceptsFile(@Nonnull VirtualFile file) {
    return true;
  }

  @Override
  public StubElement buildStubTree(@Nonnull FileContent fileContent) {
    VirtualFile file = fileContent.getFile();
    byte[] content = fileContent.getContent();

    try {
      try {
        file.setPreloadedContentHint(content);
        ClassFileDecompiler decompiler = ClassFileDecompilers.find(myApplication, file);
        if (decompiler instanceof ClassFileDecompiler.Full) {
          return ((ClassFileDecompiler.Full) decompiler).getStubBuilder().buildFileStub(fileContent);
        }
      } catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(file.getPath(), e);
        } else {
          LOG.info(file.getPath() + ": " + e.getMessage());
        }
      }

      try {
        PsiFileStub<?> stub = ClsFileImpl.buildFileStub(file, content);
        if (stub == null && fileContent.getFileName().indexOf('$') < 0) {
          LOG.info("No stub built for file " + fileContent);
        }
        return stub;
      } catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(file.getPath(), e);
        } else {
          LOG.info(file.getPath() + ": " + e.getMessage());
        }
      }
    } finally {
      file.setPreloadedContentHint(null);
    }

    return null;
  }

  private static final Comparator<Object> CLASS_NAME_COMPARATOR = (o1, o2) -> o1.getClass().getName().compareTo(o2.getClass().getName());

  @Override
  public int getStubVersion() {
    int version = STUB_VERSION;

    List<ClassFileDecompiler> decompilers = new ArrayList<>(myApplication.getExtensionList(ClassFileDecompiler.class));
    Collections.sort(decompilers, CLASS_NAME_COMPARATOR);
    for (ClassFileDecompiler decompiler : decompilers) {
      if (decompiler instanceof ClassFileDecompiler.Full) {
        version = version * 31 + ((ClassFileDecompiler.Full) decompiler).getStubBuilder().getStubVersion() + decompiler.getClass().getName().hashCode();
      }
    }

    return version;
  }
}