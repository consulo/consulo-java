/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import consulo.document.FileDocumentManager;
import consulo.project.Project;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.impl.refactoring.encapsulateFields.EncapsulateFieldsDescriptor;
import com.intellij.java.impl.refactoring.encapsulateFields.EncapsulateFieldsProcessor;
import com.intellij.java.impl.refactoring.encapsulateFields.FieldDescriptor;
import com.intellij.java.impl.refactoring.encapsulateFields.FieldDescriptorImpl;
import consulo.ide.impl.idea.refactoring.util.DocCommentPolicy;
import junit.framework.Assert;

public abstract class EncapsulateFieldsTest extends MultiFileTestCase{
  public void testAlreadyExist() throws Exception {
    doTest("i" , null);
  }

  public void testDiffWithReturnTypeOnly() throws Exception {
    doTest("i", "There is already method <b><code>Test setI(int)</code></b> which differs from setter <b><code>setI</code></b> by return type only");
  }

  public void testDiffWithReturnTypeOnlyInHierarchy() throws Exception {
    doTest("i", "There is already method <b><code>Super setI(int)</code></b> which differs from setter <b><code>setI</code></b> by return type only");
  }

  public void testHideOverriderMethod() throws Exception {
    doTest("i", "A", "There is already a method <b><code>B.getI()</code></b> which would hide generated getter for a.i");
  }

  public void testHideOuterclassMethod() throws Exception {
    doTest("i", "A.B", "There is already a method <b><code>A.getI()</code></b> which would be hidden by generated getter");
  }

  public void testMoveJavadocToGetter() throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final PsiClass aClass = myJavaFacade.findClass("A", GlobalSearchScope.projectScope(myProject));
        assertNotNull("Tested class not found", aClass);
        final PsiField field = aClass.findFieldByName("i", false);
        assertNotNull(field);
        doTest(aClass, field, null, true, true);
      }
    });
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/encapsulateFields/";
  }


  private void doTest(final String fieldName, final String conflicts) throws Exception {
    doTest(fieldName, "Test", conflicts);
  }

  private void doTest(final String fieldName, final String className, final String conflicts) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.projectScope(myProject));

        assertNotNull("Tested class not found", aClass);


        doTest(aClass, aClass.findFieldByName(fieldName, false), conflicts, true, true);
      }
    });
  }


  private static void doTest(final PsiClass aClass,
                             final PsiField field,
                             final String conflicts,
                             final boolean generateGetters,
                             final boolean generateSetters) {
    try {
      final Project project = aClass.getProject();
      EncapsulateFieldsProcessor processor = new EncapsulateFieldsProcessor(project, new EncapsulateFieldsDescriptor() {
        @Override
        public FieldDescriptor[] getSelectedFields() {
          return new FieldDescriptor[]{new FieldDescriptorImpl(
            field,
            PropertyUtil.suggestGetterName(field),
            PropertyUtil.suggestSetterName(field),
            isToEncapsulateGet() ? PropertyUtil.generateGetterPrototype(field) : null,
            isToEncapsulateSet() ? PropertyUtil.generateSetterPrototype(field) : null
          )};
        }

        @Override
        public boolean isToEncapsulateGet() {
          return generateGetters;
        }

        @Override
        public boolean isToEncapsulateSet() {
          return generateSetters;
        }

        @Override
        public boolean isToUseAccessorsWhenAccessible() {
          return true;
        }

        @Override
        public String getFieldsVisibility() {
          return null;
        }

        @Override
        public String getAccessorsVisibility() {
          return PsiModifier.PUBLIC;
        }

        @Override
        public int getJavadocPolicy() {
          return DocCommentPolicy.MOVE;
        }

        @Override
        public PsiClass getTargetClass() {
          return aClass;
        }
      });
      processor.run();
      LocalFileSystem.getInstance().refresh(false);
      FileDocumentManager.getInstance().saveAllDocuments();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (conflicts != null) {
        Assert.assertEquals(conflicts, e.getMessage());
        return;
      }
      else {
        e.printStackTrace();
        fail(e.getMessage());
      }
    }
    if (conflicts != null) {
      fail("Conflicts were not detected: " + conflicts);
    }
  }

}
