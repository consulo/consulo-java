/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 08.07.2006
 * Time: 0:07:45
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import consulo.language.editor.inspection.scheme.LocalInspectionToolWrapper;
import com.intellij.java.impl.codeInspection.nullable.NullableStuffInspection;
import consulo.application.ApplicationManager;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import consulo.content.OrderRootType;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.testFramework.InspectionTestCase;

public abstract class NullableStuffInspectionTest extends InspectionTestCase {
  private final NullableStuffInspection myInspection = new NullableStuffInspection();
  {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = false;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("nullableProblems/" + getTestName(true), new LocalInspectionToolWrapper(myInspection),"java 1.5");
  }
  private void doTest14() throws Exception {
    myExcludeAnnotations = true;
    try {
      doTest("nullableProblems/" + getTestName(true), new LocalInspectionToolWrapper(myInspection),"java 1.4");
    }
    finally {
      myExcludeAnnotations = false;
    }
  }

  public void testProblems() throws Exception{ doTest(); }
  public void testProblems2() throws Exception{ doTest(); }
  public void testNullableFieldNotnullParam() throws Exception{ doTest(); }
  public void testJdk14() throws Exception{ doTest14(); }

  public void testGetterSetterProblems() throws Exception{ doTest(); }
  public void testOverriddenMethods() throws Exception{
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }

  private boolean myExcludeAnnotations = false;

  @Override
  protected void setupRootModel(String testDir, VirtualFile[] sourceDir, String sdkName) {
    super.setupRootModel(testDir, sourceDir, sdkName);

    if (myExcludeAnnotations) {
      final Sdk sdk = JavaTestUtil.getSdk(myModule);
      assert sdk != null;
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          SdkModificator sdkMod = sdk.getSdkModificator();
          for (VirtualFile file : sdkMod.getRoots(OrderRootType.CLASSES)) {
            if ("annotations.jar".equals(file.getName())) {
              sdkMod.removeRoot(file, OrderRootType.CLASSES);
              break;
            }
          }
          sdkMod.commitChanges();
        }
      });
    }
  }
}