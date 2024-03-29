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
package com.intellij.codeInsight.daemon;

import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.impl.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.java.impl.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.java.impl.codeInspection.defUse.DefUseInspection;
import com.intellij.java.analysis.impl.codeInspection.redundantCast.RedundantCastInspection;
import consulo.ide.impl.idea.codeInspection.reference.EntryPoint;
import consulo.language.editor.inspection.reference.RefElement;
import com.intellij.java.impl.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.java.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.application.Application;
import consulo.component.extension.ExtensionPoint;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.LanguageLevel;
import consulo.language.psi.PsiElement;
import com.intellij.testFramework.IdeaTestUtil;
import consulo.java.analysis.codeInspection.JavaExtensionPoints;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * This class is for "lightweight" tests only, i.e. those which can run inside default light project set up
 * For "heavyweight" tests use AdvHighlightingTest
 */
public abstract class LightAdvHighlightingJdk7Test extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting7";

  private void doTest(boolean checkWarnings, boolean checkInfos, Class<?>... classes) {
    setLanguageLevel(LanguageLevel.JDK_1_7);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_7, getModule(), myTestRootDisposable);
    enableInspectionTools(classes);
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  private void doTest(boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos, Class<?>... classes) {
    enableInspectionTools(classes);
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkWeakWarnings, checkInfos);
  }

  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new UnusedSymbolLocalInspection(),
      new UncheckedWarningLocalInspection(),
      new JavacQuirksInspection(),
      new RedundantCastInspection()
    };
  }

  public void testAllJava15Features() { doTest(false, false); }
  public void testEnumSyntheticMethods() { doTest(false, false); }
  public void testDuplicateAnnotations() { doTest(false, false); }
  public void testSwitchByString() { doTest(false, false); }
  public void testSwitchByInaccessibleEnum() { doTest(false, false); }
  public void testDiamondPos1() { doTest(false, false); }
  public void testDiamondPos2() { doTest(false, false); }
  public void testDiamondPos3() { doTest(false, false); }
  public void testDiamondPos4() { doTest(false, false); }
  public void testDiamondPos5() { doTest(false, false); }
  public void testDiamondPos6() { doTest(false, false); }
  public void testDiamondPos7() { doTest(false, false); }
  public void testDiamondNeg15() { doTest(false, false); }
  public void testDiamondPos9() { doTest(false, false); }
  public void testDiamondNeg1() { doTest(false, false); }
  public void testDiamondNeg2() { doTest(false, false); }
  public void testDiamondNeg3() { doTest(false, false); }
  public void testDiamondNeg4() { doTest(false, false); }
  public void testDiamondNeg5() { doTest(false, false); }
  public void testDiamondNeg6() { doTest(false, false); }
  public void testDiamondNeg7() { doTest(false, false); }
  public void testDiamondNeg8() { doTest(false, false); }
  public void testDiamondNeg9() { doTest(false, false); }
  public void testDiamondNeg10() { doTest(false, false); }
  public void testDiamondNeg11() { doTest(false, false); }
  public void testDiamondNeg12() { doTest(false, false); }
  public void testDiamondNeg13() { doTest(false, false); }
  public void testDiamondNeg14() { doTest(false, false); }
  public void testDiamondMisc() { doTest(false, false); }
  public void testHighlightInaccessibleFromClassModifierList() { doTest(false, false); }
  public void testInnerInTypeArguments() { doTest(false, false); }

  public void testIncompleteDiamonds() throws Exception {
    doTest(false, false);
  }

  public void testDynamicallyAddIgnoredAnnotations() throws Exception {
    ExtensionPoint<EntryPoint> point = Application.get().getExtensionPoint(JavaExtensionPoints.DEAD_CODE_EP_NAME);
    EntryPoint extension = new EntryPoint() {
      @Nonnull
	  @Override public String getDisplayName() { return "duh"; }
      @Override public boolean isEntryPoint(RefElement refElement, PsiElement psiElement) { return false; }
      @Override public boolean isEntryPoint(PsiElement psiElement) { return false; }
      @Override public boolean isSelected() { return false; }
      @Override public void setSelected(boolean selected) { }
      @Override public void readExternal(Element element) { }
      @Override public void writeExternal(Element element) { }
      @Override public String[] getIgnoreAnnotations() { return new String[]{"MyAnno"}; }
    };

    UnusedDeclarationInspection deadCodeInspection = new UnusedDeclarationInspection();
    enableInspectionTool(deadCodeInspection);

    doTest(true, false);
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEquals(2, infos.size()); // unused class and unused method

    try {
      //point.registerExtension(extension);

      infos = doHighlighting(HighlightSeverity.WARNING);
      HighlightInfo info = assertOneElement(infos);
      assertEquals("Class 'WithMain' is never used", info.getDescription());
    }
    finally {
      //point.unregisterExtension(extension);
    }
  }

  public void testNumericLiterals() { doTest(false, false); }
  public void testMultiCatch() { doTest(false, false); }
  public void testTryWithResources() { doTest(false, false); }
  public void testTryWithResourcesWarn() { doTest(true, false, DefUseInspection.class); }
  public void testSafeVarargsApplicability() { doTest(true, false); }
  public void testUncheckedGenericsArrayCreation() { doTest(true, false); }
  public void testGenericsArrayCreation() { doTest(false, false); }
  public void testPreciseRethrow() { doTest(false, false); }
  public void testImprovedCatchAnalysis() { doTest(true, false); }
  public void testPolymorphicTypeCast() { doTest(true, false); }
  public void testErasureClashConfusion() { doTest(true, false, UnusedDeclarationInspection.class); }
  public void testUnused() { doTest(true, false, UnusedDeclarationInspection.class); }
  public void testSuperBound() { doTest(false, false); }
  public void testExtendsBound() { doTest(false, false); }
  public void testIDEA84533() { doTest(false, false); }
  public void testClassLiteral() { doTest(false, false); }
  public void testMethodReferences() { doTest(false, true, false); }
  public void testUsedMethodsByMethodReferences() { doTest(true, true, false); }
  public void testLambdaExpressions() { doTest(false, true, false); }
  public void testUncheckedWarning() { doTest(true, false); }
  public void testUncheckedWarningIDEA59290() { doTest(true, false); }
  public void testUncheckedWarningIDEA70620() { doTest(true, false); }
  public void testUncheckedWarningIDEA60166() { doTest(true, false); }
  public void testUncheckedWarningIDEA21432() { doTest(true, false); }
  public void testUncheckedWarningIDEA99357() { doTest(true, false); }
  public void testUncheckedWarningIDEA26738() { doTest(true, false); }
  public void testUncheckedWarningIDEA99536() { doTest(true, false); }
  public void testEnclosingInstance() { doTest(false, false); }
  public void testWrongArgsAndUnknownTypeParams() { doTest(false, false); }
  public void testAmbiguousMethodCallIDEA97983() { doTest(false, false); }
  public void testAmbiguousMethodCallIDEA100314() { doTest(false, false); }
  public void testAmbiguousMethodCallIDEA67668() { doTest(false, false); }
  public void testAmbiguousMethodCallIDEA67671() { doTest(false, false); }
  public void testAmbiguousMethodCallIDEA67669() { doTest(false, false); }
  public void testInstanceMemberNotAccessibleInStaticContext() { doTest(false, false); }
  public void testRejectedTypeParamsForConstructor() { doTest(false, false); }
  public void testAnnotationArgs() throws Exception { doTest(false, false);}
  public void testIDEA70890() { doTest(false, false); }
  public void testIDEA63731() { doTest(false, false); }
  public void testIDEA62056() { doTest(false, false); }
  public void testIDEA78916() { doTest(false, false); }
  public void testIDEA111420() { doTest(false, false); }
  public void testIDEA111450() { doTest(true, false); }
}
