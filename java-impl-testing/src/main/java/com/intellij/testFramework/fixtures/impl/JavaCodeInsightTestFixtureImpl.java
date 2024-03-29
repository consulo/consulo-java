/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.testFramework.fixtures.impl;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import org.junit.Assert;
import com.intellij.java.language.impl.JavaFileType;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.impl.psi.impl.JavaPsiFacadeEx;
import consulo.language.impl.internal.psi.PsiModificationTrackerImpl;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;

/**
 * @author yole
 */
public class JavaCodeInsightTestFixtureImpl extends CodeInsightTestFixtureImpl implements JavaCodeInsightTestFixture {
  public JavaCodeInsightTestFixtureImpl(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirFixture) {
    super(projectFixture, tempDirFixture);
  }

  @Override
  public JavaPsiFacadeEx getJavaFacade() {
    assertInitialized();
    return JavaPsiFacadeEx.getInstanceEx(getProject());
  }

  @Override
  public PsiClass addClass(@Nonnull @NonNls final String classText) {
    assertInitialized();
    final PsiClass psiClass = addClass(getTempDirPath(), classText);
    final VirtualFile file = psiClass.getContainingFile().getVirtualFile();
    allowTreeAccessForFile(file);
    return psiClass;
  }

  private PsiClass addClass(@NonNls final String rootPath, @Nonnull @NonNls final String classText) {
    final String qName =
      ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          final PsiFileFactory factory = PsiFileFactory.getInstance(getProject());
          final PsiJavaFile javaFile = (PsiJavaFile)factory.createFileFromText("a.java", JavaFileType.INSTANCE, classText);
          return javaFile.getClasses()[0].getQualifiedName();
        }
      });
    assert qName != null;
    final PsiFile psiFile = addFileToProject(rootPath, qName.replace('.', '/') + ".java", classText);
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
            public PsiClass compute() {
              return ((PsiJavaFile)psiFile).getClasses()[0];
            }
          });
  }

  @Override
  @Nonnull
  public PsiClass findClass(@Nonnull @NonNls final String name) {
    final PsiClass aClass = getJavaFacade().findClass(name, ProjectScope.getProjectScope(getProject()));
    Assert.assertNotNull("Class " + name + " not found", aClass);
    return aClass;
  }

  @Override
  @Nonnull
  public PsiJavaPackage findPackage(@Nonnull @NonNls final String name) {
    final PsiJavaPackage aPackage = getJavaFacade().findPackage(name);
    Assert.assertNotNull("Package " + name + " not found", aPackage);
    return aPackage;
  }

  @Override
  public void tearDown() throws Exception {
    ((PsiModificationTrackerImpl)getPsiManager().getModificationTracker()).incCounter();// drop all caches
    super.tearDown();
  }
}
