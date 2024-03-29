/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 11, 2002
 * Time: 6:50:50 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import consulo.language.editor.inspection.scheme.LocalInspectionToolWrapper;
import com.intellij.java.impl.codeInspection.magicConstant.MagicConstantInspection;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import com.intellij.java.language.projectRoots.roots.AnnotationOrderRootType;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.testFramework.PlatformTestUtil;

public abstract class MagicConstantInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  @Override
  protected Sdk getTestProjectSdk() {
    // add JDK annotations
    Sdk sdk = super.getTestProjectSdk();
    SdkModificator sdkModificator = sdk.getSdkModificator();
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(
      FileUtil.toSystemIndependentName(PlatformTestUtil.getCommunityPath()) + "/java/jdkAnnotations");
    if (root != null) {
      sdkModificator.addRoot(root, AnnotationOrderRootType.getInstance());
      sdkModificator.commitChanges();
    }

    return sdk;
  }

  private void doTest() throws Exception {
    doTest("magic/" + getTestName(true), new LocalInspectionToolWrapper(new MagicConstantInspection()), "jdk 1.7");
  }

  public void testSimple() throws Exception { doTest(); }
}
