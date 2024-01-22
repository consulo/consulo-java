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
package com.intellij.java.impl.ide.fileTemplates;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiClass;
import consulo.util.lang.StringUtil;
import consulo.ide.impl.idea.ide.actions.SaveFileAsTemplateHandler;

import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class SaveJavaAsTemplateHandler implements SaveFileAsTemplateHandler {
  @Nullable
  public String getTemplateText(final PsiFile psiFile, String fileText, String nameWithoutExtension) {
    if(psiFile instanceof PsiJavaFile){
      PsiJavaFile javaFile = (PsiJavaFile)psiFile;
      String packageName = javaFile.getPackageName();
      if (packageName.length() > 0){
        fileText = StringUtil.replace(fileText, packageName, "${PACKAGE_NAME}");
      }
      PsiClass[] classes = javaFile.getClasses();
      PsiClass psiClass = null;
      if((classes.length > 0)){
        for (PsiClass aClass : classes) {
          if (nameWithoutExtension.equals(aClass.getName())) {
            psiClass = aClass;
            break;
          }
        }
      }
      if(psiClass != null){
        //todo[myakovlev] ? PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
        return StringUtil.replace(fileText, nameWithoutExtension,"${NAME}");
      }
    }
    return null;
  }
}
