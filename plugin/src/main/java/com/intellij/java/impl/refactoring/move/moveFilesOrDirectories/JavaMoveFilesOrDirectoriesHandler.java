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
package com.intellij.java.impl.refactoring.move.moveFilesOrDirectories;

import com.intellij.java.impl.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Result;
import consulo.dataContext.DataContext;
import consulo.language.editor.LangDataKeys;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.move.fileOrDirectory.MoveFilesOrDirectoriesHandler;
import consulo.language.editor.refactoring.move.fileOrDirectory.MoveFilesOrDirectoriesUtil;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;

import jakarta.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

@ExtensionImpl(id = "moveJavaFileOrDir")
public class JavaMoveFilesOrDirectoriesHandler extends MoveFilesOrDirectoriesHandler {
  @Override
  public boolean canMove(PsiElement[] elements, PsiElement targetContainer) {
    PsiElement[] srcElements = adjustForMove(null, elements, targetContainer);
    assert srcElements != null;

    return super.canMove(srcElements, targetContainer);
  }

  @Override
  public PsiElement adjustTargetForMove(DataContext dataContext, PsiElement targetContainer) {
    if (targetContainer instanceof PsiJavaPackage) {
      Module module = dataContext.getData(LangDataKeys.TARGET_MODULE);
      if (module != null) {
        PsiDirectory[] directories = ((PsiJavaPackage) targetContainer).getDirectories(GlobalSearchScope.moduleScope(module));
        if (directories.length == 1) {
          return directories[0];
        }
      }
    }
    return super.adjustTargetForMove(dataContext, targetContainer);
  }

  @Override
  public PsiElement[] adjustForMove(Project project, PsiElement[] sourceElements, PsiElement targetElement) {
    Set<PsiElement> result = new LinkedHashSet<PsiElement>();
    for (PsiElement sourceElement : sourceElements) {
      result.add(sourceElement instanceof PsiClass ? sourceElement.getContainingFile() : sourceElement);
    }
    return PsiUtilBase.toPsiElementArray(result);
  }

  @Override
  public void doMove(final Project project, PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {

    MoveFilesOrDirectoriesUtil
        .doMove(project, elements, new PsiElement[]{targetContainer}, callback, new Function<PsiElement[], PsiElement[]>() {
          @Override
          public PsiElement[] apply(final PsiElement[] elements) {
            return new WriteCommandAction<PsiElement[]>(project, "Regrouping ...") {
              @Override
              protected void run(Result<PsiElement[]> result) throws Throwable {
                List<PsiElement> adjustedElements = new ArrayList<PsiElement>();
                for (int i = 0, length = elements.length; i < length; i++) {
                  PsiElement element = elements[i];
                  if (element instanceof PsiClass) {
                    PsiClass topLevelClass = PsiUtil.getTopLevelClass(element);
                    elements[i] = topLevelClass;
                    PsiFile containingFile = obtainContainingFile(topLevelClass, elements);
                    if (containingFile != null && !adjustedElements.contains(containingFile)) {
                      adjustedElements.add(containingFile);
                    }
                  } else {
                    adjustedElements.add(element);
                  }
                }
                result.setResult(PsiUtilBase.toPsiElementArray(adjustedElements));
              }
            }.execute().getResultObject();
          }
        });
  }

  @Nullable
  private static PsiFile obtainContainingFile(PsiElement element, PsiElement[] elements) {
    PsiClass[] classes = ((PsiClassOwner) element.getParent()).getClasses();
    Set<PsiClass> nonMovedClasses = new HashSet<PsiClass>();
    for (PsiClass aClass : classes) {
      if (ArrayUtil.find(elements, aClass) < 0) {
        nonMovedClasses.add(aClass);
      }
    }
    PsiFile containingFile = element.getContainingFile();
    if (nonMovedClasses.isEmpty()) {
      return containingFile;
    } else {
      PsiDirectory containingDirectory = containingFile.getContainingDirectory();
      if (containingDirectory != null) {
        try {
          JavaDirectoryServiceImpl.checkCreateClassOrInterface(containingDirectory, ((PsiClass) element).getName());
          PsiElement createdClass = containingDirectory.add(element);
          element.delete();
          return createdClass.getContainingFile();
        } catch (IncorrectOperationException e) {
          Iterator<PsiClass> iterator = nonMovedClasses.iterator();
          PsiClass nonMovedClass = iterator.next();
          PsiElement createdFile = containingDirectory.add(nonMovedClass).getContainingFile();
          nonMovedClass.delete();
          while (iterator.hasNext()) {
            PsiClass currentClass = iterator.next();
            createdFile.add(currentClass);
            currentClass.delete();
          }
          return containingFile;
        }
      }
    }
    return null;
  }
}
