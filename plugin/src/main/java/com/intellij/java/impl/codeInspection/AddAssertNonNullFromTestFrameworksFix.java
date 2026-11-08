// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInspection;

import consulo.localize.LocalizeValue;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.LocalQuickFix;
import com.intellij.java.language.codeInsight.TestFrameworks;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.java.impl.JavaBundle;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiStatement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.testIntegration.TestFramework;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import org.jspecify.annotations.Nullable;


public class AddAssertNonNullFromTestFrameworksFix implements LocalQuickFix, HighPriorityAction {
  private final String myText;
  private final SmartPsiElementPointer<PsiExpression> myQualifierPointer;
  private final Variant myVariant;

  public AddAssertNonNullFromTestFrameworksFix(PsiExpression qualifier, Variant variant) {
    myText = qualifier.getText();
    myQualifierPointer = SmartPointerManager.getInstance(qualifier.getProject()).createSmartPsiElementPointer(qualifier);
    myVariant = variant;
  }

  public enum Variant {
    JUNIT_3("JUnit 3", "assertNotNull", "assertNotNull"),
    JUNIT_4("JUnit 4", "Assert.assertNotNull", "org.junit.Assert.assertNotNull"),
    JUNIT_5("JUnit 5", "Assertions.assertNotNull", "org.junit.jupiter.api.Assertions.assertNotNull"),
    TESTNG("TestNG", "Assert.assertNotNull", "org.testng.Assert.assertNotNull");

    /// Used only for presentation purposes.
    public final String name;

    /// Used only for presentation purposes.
    public final String replacement;

    /// Reference to the assertion method to generate a call to; must be shortened after the generation.
    public final String methodReference;

    Variant(String name, String replacement, String methodReference) {
      this.name = name;
      this.replacement = replacement;
      this.methodReference = methodReference;
    }
  }

  @Override
  public LocalizeValue getName() {
    return LocalizeValue.of(JavaBundle.message("inspection.testframework.assert.quickfix", myVariant.name, myVariant.replacement + "(" + myText + ")"));
  }


  @Override
  public void applyFix(Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getStartElement();
    PsiExpression qualifier = myQualifierPointer.getElement();

    PsiExpression expr = PsiTreeUtil.getNonStrictParentOfType(qualifier, PsiExpression.class);
    if (expr == null) return;
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(expr);
    if (surrounder == null) return;
    CodeBlockSurrounder.SurroundResult result = surrounder.surround();
    expr = result.getExpression();
    PsiElement anchorElement = result.getSuppressionAwareAnchor();

    String text = myVariant.methodReference + "(" + myText + ");";
    PsiStatement assertStatement = JavaPsiFacade.getElementFactory(project).createStatementFromText(text, expr);
    PsiElement added = anchorElement.getParent().addBefore(assertStatement, anchorElement);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(added);
  }

  public static @Nullable Variant isAvailable(PsiExpression expression) {
    PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
    if (containingMethod == null) return null;
    PsiClass containingClass = containingMethod.getContainingClass();
    if (containingClass == null) return null;
    TestFramework detectedTestFramework = TestFrameworks.detectFramework(containingClass);
    if (detectedTestFramework == null) return null;
    return switch (detectedTestFramework.getName()) {
      case "JUnit3" -> Variant.JUNIT_3;
      case "JUnit4" -> Variant.JUNIT_4;
      case "JUnit5" -> Variant.JUNIT_5;
      case "TestNG" -> Variant.TESTNG;
      default -> null;
    };
  }
}
