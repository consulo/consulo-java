/*
 * Copyright 2013 Consulo.org
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
package consulo.java.impl.roots;

import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author VISTALL
 * @since 19:09/05.07.13
 */
public class SpecialDirUtil {
  public static final String META_INF = "META-INF";

  @Nullable
  public static String getSpecialDirLocation(@Nonnull Module module, @Nonnull String name) {
    JavaModuleExtension extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
    if(extension == null) {
      return null;
    }

    switch(extension.getSpecialDirLocation()) {
      case MODULE_DIR:
        return module.getModuleDirPath() + File.separator + name;
      case SOURCE_DIR:
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        VirtualFile[] contentFolders = moduleRootManager.getContentFolderFiles(LanguageContentFolderScopes.all(false));
        if(contentFolders.length == 0) {
          return null;
        }

        for(VirtualFile virtualFile : contentFolders) {
          VirtualFile child = virtualFile.findChild(name);
          if(child != null) {
            return child.getPath();
          }
        }

        return contentFolders[0].getPath() + File.separator + name;
    }
    return null;
  }

  @Nonnull
  public static List<VirtualFile> collectSpecialDirs(@Nonnull Module module, @Nonnull String name) {
    JavaModuleExtension extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
    if(extension == null) {
      return Collections.emptyList();
    }

    switch(extension.getSpecialDirLocation()) {
      case MODULE_DIR:
        String specialDirLocation = getSpecialDirLocation(module, name);
        assert specialDirLocation != null;
        VirtualFile virtualFile = VcsUtil.getVirtualFile(specialDirLocation);
        if(virtualFile == null) {
          return Collections.emptyList();
        }
        return Collections.singletonList(virtualFile);
      case SOURCE_DIR:
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        VirtualFile[] sourceRoots = moduleRootManager.getContentFolderFiles(LanguageContentFolderScopes.all(false));
        if(sourceRoots.length == 0) {
          return Collections.emptyList();
        }

        List<VirtualFile> list = new ArrayList<VirtualFile>(2);
        for(VirtualFile sourceRoot : sourceRoots) {
          VirtualFile child = sourceRoot.findChild(name);
          if(child != null) {
            list.add(child);
          }
        }

        return list;
    }
    return Collections.emptyList();
  }
}
