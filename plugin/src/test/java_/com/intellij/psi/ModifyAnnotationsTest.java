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
package com.intellij.psi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.intellij.JavaTestUtil;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiClass;
import consulo.application.ApplicationManager;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.VirtualFileFilter;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import consulo.language.util.IncorrectOperationException;

/**
 * @author ven
 */
public abstract class ModifyAnnotationsTest extends PsiTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/repositoryUse/modifyAnnotations";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17("mock 1.5"));
    PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
  }

  public void testReplaceAnnotation() throws Exception {
    //be sure not to load tree
    getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, null);
    PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);
    final PsiAnnotation[] annotations = aClass.getModifierList().getAnnotations();
    assertEquals(1, annotations.length);
    assertEquals("A", annotations[0].getNameReferenceElement().getReferenceName());
    final PsiAnnotation newAnnotation = myJavaFacade.getElementFactory().createAnnotationFromText("@B", null);
    //here the tree is going to be loaded
    getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, null);
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              annotations[0].replace(newAnnotation);
            }
            catch (IncorrectOperationException e) {
              LOGGER.error(e);
            }
          }
        });
      }
    }, null, null);

    assertEquals("@B", aClass.getModifierList().getText());
  }
}
