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
package consulo.java.impl.spi;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.language.Language;
import com.intellij.java.language.spi.SPILanguage;
import consulo.component.extension.ExtensionPointName;
import consulo.module.Module;
import consulo.language.util.ModuleUtilCore;
import consulo.project.Project;
import consulo.util.lang.function.Condition;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.LanguageSubstitutor;
import consulo.java.impl.roots.SpecialDirUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * @author VISTALL
 * @since 19:49/05.07.13
 */
@ExtensionImpl
public class SPILanguageSubstitutor extends LanguageSubstitutor {
  @Nullable
  @Override
  public Language getLanguage(@Nonnull VirtualFile file, @Nonnull Project project) {
    final Module moduleForPsiElement = ModuleUtilCore.findModuleForFile(file, project);
    if (moduleForPsiElement == null) {
      return null;
    }

    final VirtualFile parent = file.getParent();
    if (parent != null && "services".equals(parent.getName())) {
      final VirtualFile gParent = parent.getParent();
      if (gParent != null && SpecialDirUtil.META_INF.equals(gParent.getName())) {
        final List<VirtualFile> virtualFiles = SpecialDirUtil.collectSpecialDirs(moduleForPsiElement, SpecialDirUtil.META_INF);
        if(!virtualFiles.contains(gParent)) {
          return null;
        }

        for (VetoSPICondition condition : Application.get().getExtensionList(VetoSPICondition.class)) {
          if (condition.isVetoed(file)) {
            return null;
          }
        }
        return SPILanguage.INSTANCE;
      }
    }
    return null;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
