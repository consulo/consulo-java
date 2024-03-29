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

/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.impl.refactoring.memberPushDown.PushDownProcessor;
import consulo.ide.impl.idea.refactoring.util.DocCommentPolicy;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;

//push first method from class a.A to class b.B
public abstract class PushDownMultifileTest extends MultiFileTestCase {
  @Override
  protected String getTestRoot() {
    return "/refactoring/pushDown/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean fail) throws Exception {
    doTest(fail, "a.A", "b.B");
  }

  private void doTest(final boolean fail, final String sourceClassName, final String targetClassName) throws Exception {
    try {
      doTest(new PerformAction() {
        @Override
        public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
          final PsiClass srcClass = myJavaFacade.findClass(sourceClassName, GlobalSearchScope.allScope(myProject));
          assertTrue("Source class not found", srcClass != null);

          final PsiClass targetClass = myJavaFacade.findClass(targetClassName, GlobalSearchScope.allScope(myProject));
          assertTrue("Target class not found", targetClass != null);

          final PsiMethod[] methods = srcClass.getMethods();
          assertTrue("No methods found", methods.length > 0);
          final MemberInfo[] membersToMove = new MemberInfo[1];
          final MemberInfo memberInfo = new MemberInfo(methods[0]);
          memberInfo.setChecked(true);
          membersToMove[0] = memberInfo;

          new PushDownProcessor(getProject(), membersToMove, srcClass, new DocCommentPolicy(DocCommentPolicy.ASIS)).run();


          //LocalFileSystem.getInstance().refresh(false);
          //FileDocumentManager.getInstance().saveAllDocuments();
        }
      });
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (fail) {
        return;
      }
      else {
        throw e;
      }
    }
    if (fail) {
      fail("Conflict was not detected");
    }
  }


  public void testStaticImportsInsidePushedMethod() throws Exception {
    doTest();
  }

  public void testStaticImportOfPushedMethod() throws Exception {
    doTest();
  }

  public void testReuseOverrideMethod() throws Exception {
    doTest();
  }

  public void testFromInterface() throws Exception {
    doTest(false, "a.I", "a.I1");
  }

  public void testUsagesInXml() throws Exception {
    try {
      doTest(new PerformAction() {
        @Override
        public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
          final PsiClass srcClass = myJavaFacade.findClass("a.A", GlobalSearchScope.allScope(myProject));
          assertTrue("Source class not found", srcClass != null);

          final PsiClass targetClass = myJavaFacade.findClass("b.B", GlobalSearchScope.allScope(myProject));
          assertTrue("Target class not found", targetClass != null);

          final PsiField[] fields = srcClass.getFields();
          assertTrue("No methods found", fields.length > 0);
          final MemberInfo[] membersToMove = new MemberInfo[1];
          final MemberInfo memberInfo = new MemberInfo(fields[0]);
          memberInfo.setChecked(true);
          membersToMove[0] = memberInfo;

          new PushDownProcessor(getProject(), membersToMove, srcClass, new DocCommentPolicy(DocCommentPolicy.ASIS)).run();


          //LocalFileSystem.getInstance().refresh(false);
          //FileDocumentManager.getInstance().saveAllDocuments();
        }
      });
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(e.getMessage(), "Class <b><code>b.B</code></b> is package local and will not be accessible from file <b><code>A.form</code></b>.");
      return;
    }
    fail("Conflict was not detected");
  }
}
