// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.indexing.impl.stubs.index;

import com.intellij.java.language.impl.psi.impl.light.LightJavaModule;
import consulo.annotation.component.ExtensionImpl;
import consulo.index.io.DataIndexer;
import consulo.index.io.EnumeratorStringDescriptor;
import consulo.index.io.ID;
import consulo.index.io.KeyDescriptor;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.FileContent;
import consulo.language.psi.stub.ScalarIndexExtension;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;

import java.util.Collection;
import java.util.jar.JarFile;

import static java.util.Collections.singletonMap;

@ExtensionImpl
public class JavaAutoModuleNameIndex extends ScalarIndexExtension<String> {
  static final ID<String, Void> NAME = ID.create("java.auto.module.name");

  private final FileBasedIndex.InputFilter myFilter =
      (project, file) -> file.isDirectory() &&
                         file.getParent() == null &&
                         "jar".equalsIgnoreCase(file.getExtension()) &&
                         file.findFileByRelativePath(JarFile.MANIFEST_NAME) == null;

  private final DataIndexer<String, Void, FileContent> myIndexer =
      data -> singletonMap(LightJavaModule.moduleName(FileUtil.getNameWithoutExtension(data.getFileName()).toString()), null);

  @Override
  public boolean indexDirectories() {
    return true;
  }

  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @Override
  public int getVersion() {
    return 7;
  }

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myFilter;
  }

  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myIndexer;
  }

  public static Collection<VirtualFile> getFilesByKey(String moduleName, GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, moduleName, new JavaAutoModuleFilterScope(scope));
  }

  public static Collection<String> getAllKeys(Project project) {
    return FileBasedIndex.getInstance().getAllKeys(NAME, project);
  }
}
