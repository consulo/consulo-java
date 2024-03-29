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
package com.intellij.java.impl.ide.macro;

import consulo.annotation.component.ExtensionImpl;
import consulo.ide.IdeBundle;
import consulo.pathMacro.Macro;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.dataContext.DataContext;

@ExtensionImpl
public class FileFQPackage extends Macro {
  public String expand(DataContext dataContext) {
    PsiJavaPackage aPackage = FilePackageMacro.getFilePackage(dataContext);
    if (aPackage == null) return null;
    return aPackage.getQualifiedName();
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.fully.qualified.package");
  }

  public String getName() {
    return "FileFQPackage";
  }
}
