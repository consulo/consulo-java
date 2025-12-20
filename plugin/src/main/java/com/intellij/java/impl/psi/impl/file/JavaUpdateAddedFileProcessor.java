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
package com.intellij.java.impl.psi.impl.file;

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.UpdateAddedFileProcessor;
import consulo.language.template.TemplateLanguageFileViewProvider;
import consulo.language.util.IncorrectOperationException;

/**
 * @author Maxim.Mossienko
 * Date: Sep 18, 2008
 * Time: 3:33:07 PM
 */
@ExtensionImpl
public class JavaUpdateAddedFileProcessor extends UpdateAddedFileProcessor {
  @Override
  public boolean canProcessElement(PsiFile file) {
    return file instanceof PsiClassOwner;
  }

  @Override
  public void update(PsiFile element, PsiFile originalElement) throws IncorrectOperationException {
    if (element.getViewProvider() instanceof TemplateLanguageFileViewProvider) return;

    PsiDirectory dir = element.getContainingDirectory();
    if (dir == null) return;
    PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(dir);
    if (aPackage == null) return;
    String packageName = aPackage.getQualifiedName();

    ((PsiClassOwner) element).setPackageName(packageName);
  }
}
