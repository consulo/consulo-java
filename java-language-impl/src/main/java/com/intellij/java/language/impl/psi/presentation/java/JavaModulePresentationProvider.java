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
package com.intellij.java.language.impl.psi.presentation.java;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.PsiJavaModule;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.impl.JavaIcons;
import consulo.language.content.FileIndexFacade;
import consulo.module.Module;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ExtensionImpl
public class JavaModulePresentationProvider implements ItemPresentationProvider<PsiJavaModule> {
  private static final Pattern JAR_NAME = Pattern.compile(".+/([^/]+\\.jar)!/.*");

  @Nonnull
  @Override
  public Class<PsiJavaModule> getItemClass() {
    return PsiJavaModule.class;
  }

  @Override
  public ItemPresentation getPresentation(@Nonnull final PsiJavaModule item) {
    return new ItemPresentation() {
      @Nullable
      @Override
      public String getPresentableText() {
        return item.getName();
      }

      @Nullable
      @Override
      public String getLocationString() {
        VirtualFile file = PsiImplUtil.getModuleVirtualFile(item);
        FileIndexFacade index = FileIndexFacade.getInstance(item.getProject());
        if (index.isInLibraryClasses(file)) {
          Matcher matcher = JAR_NAME.matcher(file.getPath());
          if (matcher.find()) {
            return matcher.group(1);
          }
        } else if (index.isInSource(file)) {
          Module module = index.getModuleForFile(file);
          if (module != null) {
            return '[' + module.getName() + ']';
          }
        }
        return null;
      }

      @Nullable
      @Override
      public Image getIcon() {
        return JavaIcons.Nodes.JavaModule;
      }
    };
  }
}