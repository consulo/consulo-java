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
package com.intellij.testFramework;

import java.util.Arrays;
import java.util.Comparator;

import consulo.content.bundle.Sdk;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.impl.psi.impl.JavaPsiFacadeEx;

/**
 * @author mike
 */
public abstract class IdeaTestCase extends PlatformTestCase {
  protected JavaPsiFacadeEx myJavaFacade;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myJavaFacade = JavaPsiFacadeEx.getInstanceEx(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    myJavaFacade = null;
    super.tearDown();
  }

  public final JavaPsiFacadeEx getJavaFacade() {
    return myJavaFacade;
  }

  protected void setUpJdk() {

  }

  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk17();
  }

  protected static void sortClassesByName(final PsiClass[] classes) {
    Arrays.sort(classes, new Comparator<PsiClass>() {
      @Override
      public int compare(PsiClass o1, PsiClass o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
  }
}
