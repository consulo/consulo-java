package com.intellij.codeInspection;

import com.intellij.java.impl.codeInspection.RedundantSuppressInspection;
import com.intellij.java.impl.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.codeInspection.ex.*;
import com.intellij.java.impl.codeInspection.i18n.I18nInspection;
import consulo.language.psi.PsiElement;
import com.intellij.testFramework.InspectionTestCase;
import jakarta.annotation.Nonnull;

public abstract class RedundantSuppressTest extends InspectionTestCase {
  private GlobalInspectionToolWrapper myWrapper;
  private InspectionToolWrapper[] myInspectionToolWrappers;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    InspectionToolRegistrar.getInstance().ensureInitialized();
    myInspectionToolWrappers = new InspectionToolWrapper[]{new LocalInspectionToolWrapper(new I18nInspection()),
      new GlobalInspectionToolWrapper(new EmptyMethodInspection())};

    myWrapper = new GlobalInspectionToolWrapper(new RedundantSuppressInspection() {
      @Override
      protected InspectionToolWrapper[] getInspectionTools(PsiElement psiElement, @Nonnull InspectionManager manager) {
        return myInspectionToolWrappers;
      }
    });
  }

  public void testDefaultFile() throws Exception {
    doTest();
  }

  public void testSuppressAll() throws Exception {
    try {
      ((RedundantSuppressInspection)myWrapper.getTool()).IGNORE_ALL = true;
      doTest();
    }
    finally {
      ((RedundantSuppressInspection)myWrapper.getTool()).IGNORE_ALL = false;
    }
  }

  private void doTest() throws Exception {
    doTest("redundantSuppress/" + getTestName(true), myWrapper,"java 1.5",true);
  }
}
