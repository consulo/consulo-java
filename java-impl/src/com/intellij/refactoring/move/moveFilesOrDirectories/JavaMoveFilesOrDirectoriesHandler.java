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
package com.intellij.refactoring.move.moveFilesOrDirectories;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaPackage;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;

public class JavaMoveFilesOrDirectoriesHandler extends MoveFilesOrDirectoriesHandler {
  @Override
  public boolean canMove(PsiElement[] elements, PsiElement targetContainer) {
    final PsiElement[] srcElements = adjustForMove(null, elements, targetContainer);
    assert srcElements != null;

    return super.canMove(srcElements, targetContainer);
  }

  @Override
  public PsiElement adjustTargetForMove(DataContext dataContext, PsiElement targetContainer) {
    if (targetContainer instanceof PsiJavaPackage) {
      final Module module = dataContext.getData(LangDataKeys.TARGET_MODULE);
      if (module != null) {
        final PsiDirectory[] directories = ((PsiJavaPackage)targetContainer).getDirectories(GlobalSearchScope.moduleScope(module));
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
        public PsiElement[] fun(final PsiElement[] elements) {
          return new WriteCommandAction<PsiElement[]>(project, "Regrouping ...") {
            @Override
            protected void run(Result<PsiElement[]> result) throws Throwable {
              final List<PsiElement> adjustedElements = new ArrayList<PsiElement>();
              for (int i = 0, length = elements.length; i < length; i++) {
                PsiElement element = elements[i];
                if (element instanceof PsiClass) {
                  final PsiClass topLevelClass = PsiUtil.getTopLevelClass(element);
                  elements[i] = topLevelClass;
                  final PsiFile containingFile = obtainContainingFile(topLevelClass, elements);
                  if (containingFile != null && !adjustedElements.contains(containingFile)) {
                    adjustedElements.add(containingFile);
                  }
                }
                else {
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
    final PsiClass[] classes = ((PsiClassOwner)element.getParent()).getClasses();
    final Set<PsiClass> nonMovedClasses = new HashSet<PsiClass>();
    for (PsiClass aClass : classes) {
      if (ArrayUtil.find(elements, aClass) < 0) {
        nonMovedClasses.add(aClass);
      }
    }
    final PsiFile containingFile = element.getContainingFile();
    if (nonMovedClasses.isEmpty()) {
      return containingFile;
    }
    else {
      final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
      if (containingDirectory != null) {
        try {
          JavaDirectoryServiceImpl.checkCreateClassOrInterface(containingDirectory, ((PsiClass)element).getName());
          final PsiElement createdClass = containingDirectory.add(element);
          element.delete();
          return createdClass.getContainingFile();
        }
        catch (IncorrectOperationException e) {
          final Iterator<PsiClass> iterator = nonMovedClasses.iterator();
          final PsiClass nonMovedClass = iterator.next();
          final PsiElement createdFile = containingDirectory.add(nonMovedClass).getContainingFile();
          nonMovedClass.delete();
          while (iterator.hasNext()) {
            final PsiClass currentClass = iterator.next();
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
