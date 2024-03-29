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
 * Date: 11-Sep-2007
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import consulo.language.editor.inspection.scheme.LocalInspectionToolWrapper;
import com.intellij.java.analysis.impl.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.java.language.psi.JavaRecursiveElementVisitor;
import com.intellij.java.language.psi.PsiDocCommentOwner;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiMember;
import consulo.content.ContentIterator;
import consulo.util.lang.Comparing;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.InspectionTestCase;

public abstract class JavaAPIUsagesInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    final Java15APIUsageInspection usageInspection = new Java15APIUsageInspection();
    doTest("usage1.5/" + getTestName(true), new LocalInspectionToolWrapper(usageInspection), "java 1.5");
  }

  public void testConstructor() throws Exception {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_4, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  public void testIgnored() throws Exception {
    doTest();
  }

  @SuppressWarnings("UnusedDeclaration")
  public void _testCollectSinceApiUsages() {
    final String version = "1.7";
    final ContentIterator contentIterator = new ContentIterator() {
      @Override
      public boolean processFile(VirtualFile fileOrDir) {
        final PsiFile file = PsiManager.getInstance(getProject()).findFile(fileOrDir);
        if (file instanceof PsiJavaFile) {
          file.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
              super.visitElement(element);
              if (element instanceof PsiDocCommentOwner) {
                final PsiDocComment comment = ((PsiDocCommentOwner)element).getDocComment();
                if (comment != null) {
                  for (PsiDocTag tag : comment.getTags()) {
                    if (Comparing.strEqual(tag.getName(), "since")) {
                      final PsiDocTagValue value = tag.getValueElement();
                      if (value != null && value.getText().equals(version)) {
                        System.out.println(Java15APIUsageInspection.getSignature((PsiMember)element));
                      }
                      break;
                    }
                  }
                }
              }
            }
          });
        }
        return true;
      }
    };
    final VirtualFile srcFile = StandardFileSystems.jar().findFileByPath("c:/program files/java/jdk1.6.0_12/src.zip!/");
    assert srcFile != null;
    VfsUtilCore.iterateChildrenRecursively(srcFile, VirtualFileFilter.ALL, contentIterator);
  }
/*
  @Override
  protected void setUpJdk() {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    final Sdk sdk = JavaSdk.getInstance().createJdk("1.7", "c:/program files (x86)/java/jdk1.7.0_09/", false);
    for (Module module : modules) {
      ModuleRootModificationUtil.setModuleSdk(module, sdk);
    }
  }*/
}
