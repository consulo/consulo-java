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
import com.intellij.java.impl.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.java.impl.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.java.analysis.impl.codeInspection.deprecation.DeprecationInspection;
import com.intellij.java.impl.codeInspection.javaDoc.JavaDocLocalInspection;
import consulo.ide.impl.idea.codeInspection.reference.EntryPoint;
import consulo.language.editor.inspection.reference.RefElement;
import com.intellij.java.impl.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.java.impl.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.java.impl.codeInspection.unneededThrows.RedundantThrowsDeclaration;
import com.intellij.java.analysis.impl.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.java.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.java.language.psi.*;
import consulo.language.Language;
import com.intellij.lang.LanguageAnnotators;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.HighlightSeverity;
import com.intellij.lang.xml.XMLLanguage;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.component.extension.ExtensionPoint;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.LanguageLevel;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.testFramework.IdeaTestUtil;
import consulo.java.analysis.codeInspection.JavaExtensionPoints;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/**
 * This class is for "lightweight" tests only, i.e. those which can run inside default light project set up
 * For "heavyweight" tests use AdvHighlightingTest
 */
public abstract class LightAdvHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting";

  private UnusedSymbolLocalInspection myUnusedSymbolLocalInspection;

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_1_4);
  }

  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new SillyAssignmentInspection(),
      new AccessStaticViaInstance(),
      new DeprecationInspection(),
      new RedundantThrowsDeclaration(),
      myUnusedSymbolLocalInspection = new UnusedSymbolLocalInspection(),
      new UnusedImportLocalInspection(),
      new UncheckedWarningLocalInspection()
    };
  }

  public void testCanHaveBody() { doTest(false, false); }
  public void testInheritFinal() { doTest(false, false); }
  public void testBreakOutside() { doTest(false, false); }
  public void testLoop() { doTest(false, false); }
  public void testIllegalModifiersCombination() { doTest(false, false); }
  public void testModifierAllowed() { doTest(false, false); }
  public void testAbstractMethods() { doTest(false, false); }
  public void testInstantiateAbstract() { doTest(false, false); }
  public void testDuplicateClassMethod() { doTest(false, false); }
  public void testStringLiterals() { doTest(false, false); }
  public void testStaticInInner() { doTest(false, false); }
  public void testInvalidExpressions() { doTest(false, false); }
  public void testIllegalVoidType() { doTest(false, false); }
  public void testIllegalType() { doTest(false, false); }
  public void testOperatorApplicability() { doTest(false, false); }
  public void testIncompatibleTypes() { doTest(false, false); }
  public void testCtrCallIsFirst() { doTest(false, false); }
  public void testAccessLevelClash() { doTest(false, false); }
  public void testCasts() { doTest(false, false); }
  public void testOverrideConflicts() { doTest(false, false); }
  public void testOverriddenMethodIsFinal() { doTest(false, false); }
  public void testMissingReturn() { doTest(false, false); }
  public void testUnreachable() { doTest(false, false); }
  public void testFinalFieldInit() { doTest(false, false); }
  public void testLocalVariableInitialization() { doTest(false, false); }
  public void testVarDoubleInitialization() { doTest(false, false); }
  public void testFieldDoubleInitialization() { doTest(false, false); }
  public void testAssignToFinal() { doTest(false, false); }
  public void testUnhandledExceptionsInSuperclass() { doTest(false, false); }
  public void testNoUnhandledExceptionsMultipleInheritance() { doTest(false, false); }
  public void testFalseExceptionsMultipleInheritance() { doTest(true, false); }
  public void testAssignmentCompatible () { setLanguageLevel(LanguageLevel.JDK_1_5); doTest(false, false); }
  public void testMustBeBoolean() { doTest(false, false); }

  public void testNumericLiterals() { doTest(false, false); }
  public void testInitializerCompletion() { doTest(false, false); }

  public void testUndefinedLabel() { doTest(false, false); }
  public void testDuplicateSwitchLabels() { doTest(false, false); }
  public void testStringSwitchLabels() { doTest(false, false); }
  public void testIllegalForwardReference() { doTest(false, false); }
  public void testStaticOverride() { doTest(false, false); }
  public void testCyclicInheritance() { doTest(false, false); }
  public void testReferenceMemberBeforeCtrCalled() { doTest(false, false); }
  public void testLabels() { doTest(false, false); }
  public void testUnclosedBlockComment() { doTest(false, false); }
  public void testUnclosedComment() { doTest(false, false); }
  public void testUnclosedDecl() { doTest(false, false); }
  public void testSillyAssignment() { doTest(true, false); }
  public void testTernary() { doTest(false, false); }
  public void testDuplicateClass() { doTest(false, false); }
  public void testCatchType() { doTest(false, false); }
  public void testMustBeThrowable() { doTest(false, false); }
  public void testUnhandledMessingWithFinally() { doTest(false, false); }
  public void testSerializableStuff() { enableInspectionTool(new UnusedDeclarationInspection()); doTest(true, false); }
  public void testDeprecated() { doTest(true, false); }
  public void testJavadoc() { enableInspectionTool(new JavaDocLocalInspection()); doTest(true, false); }
  public void testExpressionsInSwitch () { doTest(false, false); }
  public void testAccessInner () { doTest(false, false); }

  public void testExceptionNeverThrown() { doTest(true, false); }
  public void testExceptionNeverThrownInTry() { doTest(false, false); }

  public void testSwitchStatement() { doTest(false, false); }
  public void testAssertExpression() { doTest(false, false); }

  public void testSynchronizedExpression() { doTest(false, false); }
  public void testExtendMultipleClasses() { doTest(false, false); }
  public void testRecursiveConstructorInvocation() { doTest(false, false); }
  public void testMethodCalls() { doTest(false, false); }
  public void testSingleTypeImportConflicts() { doTest(false, false); }
  public void testMultipleSingleTypeImports() { doTest(true, false); } //duplicate imports
  public void testNotAllowedInInterface() { doTest(false, false); }
  public void testQualifiedNew() { doTest(false, false); }
  public void testEnclosingInstance() { doTest(false, false); }

  public void testStaticViaInstance() { doTest(true, false); } // static via instance
  public void testQualifiedThisSuper() { doTest(true, false); } //illegal qualified this or super

  public void testAmbiguousMethodCall() { doTest(false, false); }

  public void testImplicitConstructor() { doTest(false, false); }
  public void testDotBeforeDecl() { doTest(false, false); }
  public void testComputeConstant() { doTest(false, false); }

  public void testAnonInAnon() { doTest(false, false); }
  public void testAnonBaseRef() { doTest(false, false); }
  public void testReturn() { doTest(false, false); }
  public void testInterface() { doTest(false, false); }
  public void testExtendsClause() { doTest(false, false); }
  public void testMustBeFinal() { doTest(false, false); }

  public void testXXX() { doTest(false, false); }
  public void testUnused() { doTest(true, false); }
  public void testQualifierBeforeClassName() { doTest(false, false); }
  public void testQualifiedSuper() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_6, getModule(), myTestRootDisposable);
    doTest(false, false);
  }

  public void testIgnoreImplicitThisReferenceBeforeSuperSinceJdk7() throws Exception {
    doTest(false, false);
  }

  public void testCastFromVoid() { doTest(false, false); }
  public void testCatchUnknownMethod() { doTest(false, false); }
  public void testIDEADEV8822() { doTest(false, false); }
  public void testIDEADEV9201() { doTest(false, false); }
  public void testIDEADEV25784() { doTest(false, false); }
  public void testIDEADEV13249() { doTest(false, false); }
  public void testIDEADEV11919() { doTest(false, false); }
  public void testIDEA67829() { doTest(false, false); }
  public void testMethodCannotBeApplied() { doTest(false, false); }
  public void testDefaultPackageClassInStaticImport() { doTest(false, false); }

  public void testUnusedParamsOfPublicMethod() { doTest(true, false); }
  public void testInnerClassesShadowing() { doTest(false, false); }

  public void testUnusedParamsOfPublicMethodDisabled() {
    myUnusedSymbolLocalInspection.REPORT_PARAMETER_FOR_PUBLIC_METHODS = false;
    doTest(true, false);
  }

  public void testUnusedNonPrivateMembers() {
    UnusedDeclarationInspection deadCodeInspection = new UnusedDeclarationInspection();
    enableInspectionTool(deadCodeInspection);
    doTest(true, false);
  }

  public void testUnusedNonPrivateMembers2() {
    ExtensionPoint<EntryPoint> point = Application.get().getExtensionPoint(JavaExtensionPoints.DEAD_CODE_EP_NAME);
    EntryPoint extension = new EntryPoint() {
      @Nonnull
      @Override
      public String getDisplayName() {
        return "duh";
      }

      @Override
      public boolean isEntryPoint(RefElement refElement, PsiElement psiElement) {
        return false;
      }

      @Override
      public boolean isEntryPoint(PsiElement psiElement) {
        return psiElement instanceof PsiMethod && ((PsiMethod)psiElement).getName().equals("myTestMethod");
      }

      @Override
      public boolean isSelected() {
        return false;
      }

      @Override
      public void setSelected(boolean selected) { }

      @Override
      public void readExternal(Element element) { }

      @Override
      public void writeExternal(Element element) { }
    };

    //point.registerExtension(extension);

    try {
      UnusedDeclarationInspection deadCodeInspection = new UnusedDeclarationInspection();
      enableInspectionTool(deadCodeInspection);

      doTest(true, false);
    }
    finally {
      //point.unregisterExtension(extension);
    }
  }
  public void testUnusedNonPrivateMembersReferencedFromText() {
    UnusedDeclarationInspection deadCodeInspection = new UnusedDeclarationInspection();
    enableInspectionTool(deadCodeInspection);

    doTest(true, false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        PsiDirectory directory = myFile.getParent();
        assertNotNull(myFile.toString(), directory);
        PsiFile txt = directory.createFile("x.txt");
        VirtualFile vFile = txt.getVirtualFile();
        assertNotNull(txt.toString(), vFile);
        try {
          consulo.ide.impl.idea.openapi.vfs.VfsUtil.saveText(vFile, "XXX");
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });

    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);
  }

  public void testNamesHighlighting() {
    //LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    doTestFile(BASE_PATH + "/" + getTestName(false) + ".java").checkSymbolNames().test();
  }

  public void testMultiFieldDeclNames() {
    doTestFile(BASE_PATH + "/" + getTestName(false) + ".java").checkSymbolNames().test();
  }

  public static class MyAnnotator implements Annotator {
    @Override
    public void annotate(@Nonnull PsiElement psiElement, @Nonnull final AnnotationHolder holder) {
      psiElement.accept(new XmlElementVisitor() {
        @Override public void visitXmlTag(XmlTag tag) {
          XmlAttribute attribute = tag.getAttribute("aaa", "");
          if (attribute != null) {
            holder.createWarningAnnotation(attribute, "AAATTR");
          }
        }

        @Override public void visitXmlToken(XmlToken token) {
          if (token.getTokenType() == XmlTokenType.XML_ENTITY_REF_TOKEN) {
            holder.createWarningAnnotation(token, "ENTITY");
          }
        }
      });
    }
  }

  public void testInjectedAnnotator() {
    Annotator annotator = new MyAnnotator();
    Language xml = XMLLanguage.INSTANCE;
    //LanguageAnnotators.INSTANCE.addExplicitExtension(xml, annotator);
    try {
      List<Annotator> list = LanguageAnnotators.INSTANCE.allForLanguage(xml);
      assertTrue(list.toString(), list.contains(annotator));
      doTest(BASE_PATH + "/" + getTestName(false) + ".xml",true,false);
    }
    finally {
      //LanguageAnnotators.INSTANCE.removeExplicitExtension(xml, annotator);
    }

    List<Annotator> list = LanguageAnnotators.INSTANCE.allForLanguage(xml);
    assertFalse(list.toString(), list.contains(annotator));
  }

  public void testSOEForTypeOfHugeBinaryExpression() throws IOException {
    configureFromFileText("a.java", "class A { String s = \"\"; }");
    assertEmpty(highlightErrors());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    final StringBuilder sb = new StringBuilder("\"-\"");
    for (int i = 0; i < 10000; i++) sb.append("+\"b\"");
    final String hugeExpr = sb.toString();
    final int pos = getEditor().getDocument().getText().indexOf("\"\"");

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        getEditor().getDocument().replaceString(pos, pos + 2, hugeExpr);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
    });

    final PsiField field = ((PsiJavaFile)getFile()).getClasses()[0].getFields()[0];
    final PsiExpression expression = field.getInitializer();
    assert expression != null;
    final PsiType type = expression.getType();
    assert type != null;
    assertEquals("PsiType:String", type.toString());
  }

  public void testSOEForCyclicInheritance() throws IOException {
    configureFromFileText("a.java", "class A extends B { String s = \"\"; void f() {}} class B extends A { void f() {} } ");
    doHighlighting();
  }

  public void testClassicRethrow() { doTest(false, false); }
  public void testRegexp() { doTest(false, false); }
  public void testUnsupportedFeatures() { doTest(false, false); }
  public void testThisBeforeSuper() { doTest(false, false); }
  public void testExplicitConstructorInvocation() { doTest(false, false); }
  public void testThisInInterface() { doTest(false, false); }
  public void testInnerClassConstantReference() { doTest(false, false); }
  public void testIDEA60875() { doTest(false, false); }
  public void testIDEA71645() { doTest(false, false); }
  public void testNewExpressionClass() { doTest(false, false); }

  public void testNoEnclosingInstanceWhenStaticNestedInheritsFromContainingClass() throws Exception {
    doTest(false, false);
  }

  public void testStaticMethodCalls() {
    doTestFile(BASE_PATH + "/" + getTestName(false) + ".java").checkSymbolNames().test();
  }
  public void testInsane() throws IOException {
    configureFromFileText("x.java", "class X { \nxxxx\n }");
    List<HighlightInfo> infos = highlightErrors();
    assertTrue(infos.size() != 0);
  }
}
