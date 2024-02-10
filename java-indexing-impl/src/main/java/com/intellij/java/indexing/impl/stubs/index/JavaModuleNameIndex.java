// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.indexing.impl.stubs.index;

import com.intellij.java.indexing.impl.search.JavaSourceFilterScope;
import com.intellij.java.language.impl.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.java.language.psi.PsiJavaModule;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.stub.StubIndexKey;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.project.content.scope.ProjectAwareSearchScope;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ExtensionImpl
public class JavaModuleNameIndex extends StringStubIndexExtension<PsiJavaModule> {
  private static final JavaModuleNameIndex ourInstance = new JavaModuleNameIndex();

  public static JavaModuleNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  public int getVersion() {
    return 3;
  }

  @Nonnull
  @Override
  public StubIndexKey<String, PsiJavaModule> getKey() {
    return JavaStubIndexKeys.MODULE_NAMES;
  }

  @Override
  public Collection<PsiJavaModule> get(@Nonnull String name, @Nonnull Project project, @Nonnull ProjectAwareSearchScope scope) {
    Collection<PsiJavaModule> modules = StubIndex.getElements(getKey(), name, project, new JavaSourceFilterScope((GlobalSearchScope) scope, true), PsiJavaModule.class);
    if (modules.size() > 1) {
      modules = filterVersions(project, modules);
    }
    return modules;
  }

  private static Collection<PsiJavaModule> filterVersions(Project project, Collection<PsiJavaModule> modules) {
    Set<VirtualFile> filter = new HashSet<>();

    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    for (PsiJavaModule module : modules) {
      VirtualFile root = index.getClassRootForFile(module.getContainingFile().getVirtualFile());
      if (root != null) {
        List<VirtualFile> files = descriptorFiles(root, false, false);
        VirtualFile main = ContainerUtil.getFirstItem(files);
        if (main != null && !(root.equals(main.getParent()) || version(main.getParent()) >= 9)) {
          filter.add(main);
        }
        for (int i = 1; i < files.size(); i++) {
          filter.add(files.get(i));
        }
      }
    }

    if (!filter.isEmpty()) {
      modules = modules.stream().filter(m -> !filter.contains(m.getContainingFile().getVirtualFile())).collect(Collectors.toList());
    }

    return modules;
  }

  @Override
  public boolean traceKeyHashToVirtualFileMapping() {
    return true;
  }

  @Nullable
  public static VirtualFile descriptorFile(@Nonnull VirtualFile root) {
    VirtualFile result = root.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE);
    if (result == null) {
      result = ContainerUtil.getFirstItem(descriptorFiles(root, true, true));
    }
    return result;
  }

  private static List<VirtualFile> descriptorFiles(VirtualFile root, boolean checkAttribute, boolean filter) {
    List<VirtualFile> results = new ArrayList<>();

    ContainerUtil.addIfNotNull(results, root.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE));

    VirtualFile versionsDir = root.findFileByRelativePath("META-INF/versions");
    if (versionsDir != null && (!checkAttribute || isMultiReleaseJar(root))) {
      VirtualFile[] versions = versionsDir.getChildren();
      if (filter) {
        versions = Stream.of(versions).filter(d -> version(d) >= 9).toArray(VirtualFile[]::new);
      }
      Arrays.sort(versions, JavaModuleNameIndex::compareVersions);
      for (VirtualFile version : versions) {
        ContainerUtil.addIfNotNull(results, version.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE));
      }
    }

    return results;
  }

  private static boolean isMultiReleaseJar(VirtualFile root) {
    VirtualFile manifest = root.findFileByRelativePath(JarFile.MANIFEST_NAME);
    if (manifest != null) {
      try (InputStream stream = manifest.getInputStream()) {
        return Boolean.valueOf(new Manifest(stream).getMainAttributes().getValue(new Attributes.Name("Multi-Release")));
      } catch (IOException ignored) {
      }
    }
    return false;
  }

  private static int version(VirtualFile dir) {
    try {
      return Integer.valueOf(dir.getName());
    } catch (RuntimeException ignore) {
      return Integer.MIN_VALUE;
    }
  }

  private static int compareVersions(VirtualFile dir1, VirtualFile dir2) {
    int v1 = version(dir1), v2 = version(dir2);
    if (v1 < 9 && v2 < 9) {
      return 0;
    }
    if (v1 < 9) {
      return 1;
    }
    if (v2 < 9) {
      return -1;
    }
    return v1 - v2;
  }
}