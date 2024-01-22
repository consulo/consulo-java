
package com.intellij.codeInsight.daemon.quickFix;

import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.impl.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.java.analysis.impl.codeInspection.deprecation.DeprecationInspection;
import com.intellij.java.impl.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.java.impl.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.java.impl.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.java.impl.codeInspection.unneededThrows.RedundantThrowsDeclaration;
import com.intellij.java.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import jakarta.annotation.Nonnull;


public abstract class SuppressNonInspectionsTest extends LightQuickFixTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    //LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_3);
  }

  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RedundantThrowsDeclaration(),
      new SillyAssignmentInspection(),
      new AccessStaticViaInstance(),
      new DeprecationInspection(),
      new JavaDocReferenceInspection(),
      new UnusedSymbolLocalInspection(),
      new UncheckedWarningLocalInspection()
    };
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/suppressNonInspections";
  }

}

