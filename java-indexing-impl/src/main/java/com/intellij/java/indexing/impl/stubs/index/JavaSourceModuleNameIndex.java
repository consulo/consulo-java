// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.indexing.impl.stubs.index;

import com.intellij.java.language.impl.psi.impl.light.LightJavaModule;
import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.psi.PsiJavaModule;
import consulo.annotation.component.ExtensionImpl;
import consulo.index.io.DataIndexer;
import consulo.index.io.EnumeratorStringDescriptor;
import consulo.index.io.ID;
import consulo.index.io.KeyDescriptor;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.DefaultFileTypeSpecificInputFilter;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.FileContent;
import consulo.language.psi.stub.ScalarIndexExtension;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;

import org.jspecify.annotations.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

@ExtensionImpl
public class JavaSourceModuleNameIndex extends ScalarIndexExtension<String> {
  private static final ID<String, Void> NAME = ID.create("java.source.module.name");

  private final FileType myManifestFileType = FileTypeRegistry.getInstance().getFileTypeByExtension("MF");
  private final FileBasedIndex.InputFilter myFilter = new DefaultFileTypeSpecificInputFilter(myManifestFileType);

  static final String META_INF_DIR_NAME = JarFile.MANIFEST_NAME.substring(0, JarFile.MANIFEST_NAME.indexOf('/'));
  static final String MANIFEST_FILE_NAME = JarFile.MANIFEST_NAME.substring(JarFile.MANIFEST_NAME.indexOf('/') + 1);

  private final DataIndexer<String, Void, FileContent> myIndexer = data -> {
    try {
      String name = new Manifest(new ByteArrayInputStream(data.getContent()))
                        .getMainAttributes().getValue(PsiJavaModule.AUTO_MODULE_NAME);
      if (name != null) return singletonMap(name, null);
      // fallback: derive from JAR filename, but only for an actual JAR root (not a source root's META-INF)
      VirtualFile metaInf = data.getFile().getParent();
      if (metaInf == null || !META_INF_DIR_NAME.equalsIgnoreCase(metaInf.getName())) return emptyMap();
      VirtualFile jarRoot = metaInf.getParent();
      if (jarRoot == null || jarRoot.getParent() != null || !"jar".equalsIgnoreCase(jarRoot.getExtension())) return emptyMap();
      return singletonMap(LightJavaModule.moduleName(jarRoot.getNameWithoutExtension()), null);
    }
    catch (IOException ignored) {
      return emptyMap();
    }
  };

  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @Override
  public int getVersion() {
    return 6;
  }

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myFilter;
  }

  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myIndexer;
  }

  @Override
  public Collection<FileType> getFileTypesWithSizeLimitNotApplicable() {
    return Collections.singleton(JavaClassFileType.INSTANCE);
  }

  public static Collection<VirtualFile> getFilesByKey(String moduleName, GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, moduleName, new JavaAutoModuleFilterScope(scope));
  }

  public static Collection<String> getAllKeys(Project project) {
    return FileBasedIndex.getInstance().getAllKeys(NAME, project);
  }
}