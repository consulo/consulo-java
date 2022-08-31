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
package com.intellij.java.execution.impl.junit2.info;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import consulo.logging.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import javax.annotation.Nonnull;

import java.util.Iterator;

// Author: dyoma

public class MethodLocation extends Location<PsiMethod> {
  private static final Logger LOG = Logger.getInstance(MethodLocation.class);
  private final Project myProject;
  @Nonnull
  private final PsiMethod myMethod;
  private final Location<PsiClass> myClassLocation;

  public MethodLocation(@Nonnull final Project project, @Nonnull final PsiMethod method, @Nonnull final Location<PsiClass> classLocation) {
    myProject = project;
    myMethod = method;
    myClassLocation = classLocation;
  }

  public static MethodLocation elementInClass(final PsiMethod psiElement, final PsiClass psiClass) {
    final Location<PsiClass> classLocation = PsiLocation.fromPsiElement(psiClass);
    return new MethodLocation(classLocation.getProject(), psiElement, classLocation);
  }

  @Nonnull
  public PsiMethod getPsiElement() {
    return myMethod;
  }

  @Nonnull
  public Project getProject() {
    return myProject;
  }

  @javax.annotation.Nullable
  @Override
  public Module getModule() {
    return ModuleUtil.findModuleForPsiElement(myMethod);
  }

  public PsiClass getContainingClass() {
    return myClassLocation.getPsiElement();
  }

  @Nonnull
  public <T extends PsiElement> Iterator<Location<T>> getAncestors(final Class<T> ancestorClass, final boolean strict) {
    final Iterator<Location<T>> fromClass = myClassLocation.getAncestors(ancestorClass, false);
    if (strict) return fromClass;
    return new Iterator<Location<T>>() {
      private boolean myFirstStep = ancestorClass.isInstance(myMethod);
      public boolean hasNext() {
        return myFirstStep || fromClass.hasNext();
      }

      public Location<T> next() {
        final Location<T> location = myFirstStep ? (Location<T>)(Location)MethodLocation.this : fromClass.next();
        myFirstStep = false;
        return location;
      }

      public void remove() {
        LOG.assertTrue(false);
      }
    };
  }
}
