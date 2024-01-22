// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi;

import com.intellij.java.language.psi.javadoc.*;
import consulo.language.psi.PsiElementVisitor;
import jakarta.annotation.Nonnull;

public abstract class JavaElementVisitor extends PsiElementVisitor {
  public void visitAnnotation(@Nonnull PsiAnnotation annotation) {
    visitElement(annotation);
  }

  public void visitAnnotationArrayInitializer(@Nonnull PsiArrayInitializerMemberValue initializer) {
    visitElement(initializer);
  }

  public void visitAnnotationMethod(@Nonnull PsiAnnotationMethod method) {
    visitMethod(method);
  }

  public void visitAnnotationParameterList(@Nonnull PsiAnnotationParameterList list) {
    visitElement(list);
  }

  public void visitAnonymousClass(@Nonnull PsiAnonymousClass aClass) {
    visitClass(aClass);
  }

  public void visitArrayAccessExpression(@Nonnull PsiArrayAccessExpression expression) {
    visitExpression(expression);
  }

  public void visitArrayInitializerExpression(@Nonnull PsiArrayInitializerExpression expression) {
    visitExpression(expression);
  }

  public void visitAssertStatement(@Nonnull PsiAssertStatement statement) {
    visitStatement(statement);
  }

  public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression expression) {
    visitExpression(expression);
  }

  public void visitBinaryExpression(@Nonnull PsiBinaryExpression expression) {
    visitPolyadicExpression(expression);
  }

  public void visitBlockStatement(@Nonnull PsiBlockStatement statement) {
    visitStatement(statement);
  }

  public void visitBreakStatement(@Nonnull PsiBreakStatement statement) {
    visitStatement(statement);
  }

  public void visitCallExpression(@Nonnull PsiCallExpression callExpression) {
    visitExpression(callExpression);
  }

  public void visitCaseLabelElementList(@Nonnull PsiCaseLabelElementList list) {
    visitElement(list);
  }

  public void visitCatchSection(@Nonnull PsiCatchSection section) {
    visitElement(section);
  }

  public void visitClass(@Nonnull PsiClass aClass) {
    visitElement(aClass);
  }

  public void visitClassInitializer(@Nonnull PsiClassInitializer initializer) {
    visitElement(initializer);
  }

  public void visitClassObjectAccessExpression(@Nonnull PsiClassObjectAccessExpression expression) {
    visitExpression(expression);
  }

  public void visitCodeBlock(@Nonnull PsiCodeBlock block) {
    visitElement(block);
  }

  public void visitCodeFragment(@Nonnull JavaCodeFragment codeFragment) {
    visitFile(codeFragment);
  }

  public void visitConditionalExpression(@Nonnull PsiConditionalExpression expression) {
    visitExpression(expression);
  }

  public void visitContinueStatement(@Nonnull PsiContinueStatement statement) {
    visitStatement(statement);
  }

  public void visitDeclarationStatement(@Nonnull PsiDeclarationStatement statement) {
    visitStatement(statement);
  }

  public void visitDeconstructionList(@Nonnull PsiDeconstructionList deconstructionList) {
    visitElement(deconstructionList);
  }

  public void visitDeconstructionPattern(@Nonnull PsiDeconstructionPattern deconstructionPattern) {
    visitPattern(deconstructionPattern);
  }

  public void visitDefaultCaseLabelElement(@Nonnull PsiDefaultCaseLabelElement element) {
    visitElement(element);
  }

  public void visitDocComment(@Nonnull PsiDocComment comment) {
    visitComment(comment);
  }

  public void visitDocTag(@Nonnull PsiDocTag tag) {
    visitElement(tag);
  }

  public void visitDocTagValue(@Nonnull PsiDocTagValue value) {
    visitElement(value);
  }

  public void visitDocToken(@Nonnull PsiDocToken token) {
    visitElement(token);
  }

  public void visitDoWhileStatement(@Nonnull PsiDoWhileStatement statement) {
    visitStatement(statement);
  }

  public void visitEmptyStatement(@Nonnull PsiEmptyStatement statement) {
    visitStatement(statement);
  }

  public void visitEnumConstant(@Nonnull PsiEnumConstant enumConstant) {
    visitField(enumConstant);
  }

  public void visitEnumConstantInitializer(@Nonnull PsiEnumConstantInitializer enumConstantInitializer) {
    visitAnonymousClass(enumConstantInitializer);
  }

  public void visitExpression(@Nonnull PsiExpression expression) {
    visitElement(expression);
  }

  public void visitExpressionList(@Nonnull PsiExpressionList list) {
    visitElement(list);
  }

  public void visitExpressionListStatement(@Nonnull PsiExpressionListStatement statement) {
    visitStatement(statement);
  }

  public void visitExpressionStatement(@Nonnull PsiExpressionStatement statement) {
    visitStatement(statement);
  }

  public void visitField(@Nonnull PsiField field) {
    visitVariable(field);
  }

  public void visitForeachPatternStatement(@Nonnull PsiForeachPatternStatement statement) {
    visitForeachStatementBase(statement);
  }

  public void visitForeachStatement(@Nonnull PsiForeachStatement statement) {
    visitForeachStatementBase(statement);
  }

  public void visitForeachStatementBase(@Nonnull PsiForeachStatementBase statement) {
    visitStatement(statement);
  }

  public void visitForStatement(@Nonnull PsiForStatement statement) {
    visitStatement(statement);
  }

  public void visitFragment(@Nonnull PsiFragment fragment) {
    visitElement(fragment);
  }

  public void visitIdentifier(@Nonnull PsiIdentifier identifier) {
    visitJavaToken(identifier);
  }

  public void visitIfStatement(@Nonnull PsiIfStatement statement) {
    visitStatement(statement);
  }

  public void visitImplicitClass(@Nonnull PsiImplicitClass aClass) {
    visitClass(aClass);
  }

  public void visitImplicitVariable(@Nonnull ImplicitVariable variable) {
    visitLocalVariable(variable);
  }

  public void visitImportList(@Nonnull PsiImportList list) {
    visitElement(list);
  }

  public void visitImportStatement(@Nonnull PsiImportStatement statement) {
    visitElement(statement);
  }

  public void visitImportStaticReferenceElement(@Nonnull PsiImportStaticReferenceElement reference) {
    visitReferenceElement(reference);
  }

  public void visitImportStaticStatement(@Nonnull PsiImportStaticStatement statement) {
    visitElement(statement);
  }

  public void visitInlineDocTag(@Nonnull PsiInlineDocTag tag) {
    visitDocTag(tag);
  }

  public void visitInstanceOfExpression(@Nonnull PsiInstanceOfExpression expression) {
    visitExpression(expression);
  }

  public void visitJavaFile(@Nonnull PsiJavaFile file) {
    visitFile(file);
  }

  public void visitJavaToken(@Nonnull PsiJavaToken token) {
    visitElement(token);
  }

  public void visitKeyword(@Nonnull PsiKeyword keyword) {
    visitJavaToken(keyword);
  }

  public void visitLabeledStatement(@Nonnull PsiLabeledStatement statement) {
    visitStatement(statement);
  }

  public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
    visitExpression(expression);
  }

  public void visitLiteralExpression(@Nonnull PsiLiteralExpression expression) {
    visitExpression(expression);
  }

  public void visitLocalVariable(@Nonnull PsiLocalVariable variable) {
    visitVariable(variable);
  }

  public void visitMethod(@Nonnull PsiMethod method) {
    visitElement(method);
  }

  public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
    visitCallExpression(expression);
  }

  public void visitMethodReferenceExpression(@Nonnull PsiMethodReferenceExpression expression) {
    visitReferenceExpression(expression);
  }

  public void visitModifierList(@Nonnull PsiModifierList list) {
    visitElement(list);
  }

  public void visitModule(@Nonnull PsiJavaModule module) {
    visitElement(module);
  }

  public void visitModuleReferenceElement(@Nonnull PsiJavaModuleReferenceElement refElement) {
    visitElement(refElement);
  }

  public void visitModuleStatement(@Nonnull PsiStatement statement) {
    visitStatement(statement);
  }

  public void visitNameValuePair(@Nonnull PsiNameValuePair pair) {
    visitElement(pair);
  }

  public void visitNewExpression(@Nonnull PsiNewExpression expression) {
    visitCallExpression(expression);
  }

  public void visitPackage(@Nonnull PsiJavaPackage aPackage) {
    visitElement(aPackage);
  }

  public void visitPackageAccessibilityStatement(@Nonnull PsiPackageAccessibilityStatement statement) {
    visitModuleStatement(statement);
  }

  public void visitPackageStatement(@Nonnull PsiPackageStatement statement) {
    visitElement(statement);
  }

  public void visitParameter(@Nonnull PsiParameter parameter) {
    visitVariable(parameter);
  }

  public void visitParameterList(@Nonnull PsiParameterList list) {
    visitElement(list);
  }

  public void visitParenthesizedExpression(@Nonnull PsiParenthesizedExpression expression) {
    visitExpression(expression);
  }

  public void visitParenthesizedPattern(@Nonnull PsiParenthesizedPattern pattern) {
    visitPattern(pattern);
  }

  public void visitPattern(@Nonnull PsiPattern pattern) {
    visitElement(pattern);
  }

  public void visitPatternVariable(@Nonnull PsiPatternVariable variable) {
    visitParameter(variable);
  }

  public void visitPolyadicExpression(@Nonnull PsiPolyadicExpression expression) {
    visitExpression(expression);
  }

  public void visitPostfixExpression(@Nonnull PsiPostfixExpression expression) {
    visitUnaryExpression(expression);
  }

  public void visitPrefixExpression(@Nonnull PsiPrefixExpression expression) {
    visitUnaryExpression(expression);
  }

  public void visitProvidesStatement(@Nonnull PsiProvidesStatement statement) {
    visitModuleStatement(statement);
  }

  public void visitReceiverParameter(@Nonnull PsiReceiverParameter parameter) {
    visitVariable(parameter);
  }

  public void visitRecordComponent(@Nonnull PsiRecordComponent recordComponent) {
    visitVariable(recordComponent);
  }

  public void visitRecordHeader(@Nonnull PsiRecordHeader recordHeader) {
    visitElement(recordHeader);
  }

  public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference) {
    visitElement(reference);
  }

  /**
   * PsiReferenceExpression is PsiReferenceElement and PsiExpression at the same time.
   * If we'd call both visitReferenceElement and visitExpression in default implementation
   * of this method we can easily stuck with exponential algorithm if the derived visitor
   * extends visitElement() and accepts children there.
   * {@link JavaRecursiveElementVisitor} knows that and implements this method accordingly.
   * All other visitor must decide themselves what implementation (visitReferenceElement() or visitExpression() or none or LOG.error())
   * is appropriate for them.
   */
  public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
  }

  public void visitReferenceList(@Nonnull PsiReferenceList list) {
    visitElement(list);
  }

  public void visitReferenceParameterList(@Nonnull PsiReferenceParameterList list) {
    visitElement(list);
  }

  public void visitRequiresStatement(@Nonnull PsiRequiresStatement statement) {
    visitModuleStatement(statement);
  }

  public void visitResourceExpression(@Nonnull PsiResourceExpression expression) {
    visitElement(expression);
  }

  public void visitResourceList(@Nonnull PsiResourceList resourceList) {
    visitElement(resourceList);
  }

  public void visitResourceVariable(@Nonnull PsiResourceVariable variable) {
    visitLocalVariable(variable);
  }

  public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
    visitStatement(statement);
  }

  public void visitSnippetAttribute(@Nonnull PsiSnippetAttribute attribute) {
    visitElement(attribute);
  }

  public void visitSnippetAttributeList(@Nonnull PsiSnippetAttributeList attributeList) {
    visitElement(attributeList);
  }

  public void visitSnippetAttributeValue(@Nonnull PsiSnippetAttributeValue attributeValue) {
    visitElement(attributeValue);
  }

  public void visitSnippetDocTagBody(@Nonnull PsiSnippetDocTagBody body) {
    visitElement(body);
  }

  public void visitSnippetDocTagValue(@Nonnull PsiSnippetDocTagValue value) {
    visitElement(value);
  }

  public void visitSnippetTag(@Nonnull PsiSnippetDocTag snippetDocTag) {
    visitInlineDocTag(snippetDocTag);
  }

  public void visitStatement(@Nonnull PsiStatement statement) {
    visitElement(statement);
  }

  public void visitSuperExpression(@Nonnull PsiSuperExpression expression) {
    visitExpression(expression);
  }

  public void visitSwitchExpression(@Nonnull PsiSwitchExpression expression) {
    visitExpression(expression);
  }

  public void visitSwitchLabeledRuleStatement(@Nonnull PsiSwitchLabeledRuleStatement statement) {
    visitStatement(statement);
  }

  public void visitSwitchLabelStatement(@Nonnull PsiSwitchLabelStatement statement) {
    visitStatement(statement);
  }

  public void visitSwitchStatement(@Nonnull PsiSwitchStatement statement) {
    visitStatement(statement);
  }

  public void visitSynchronizedStatement(@Nonnull PsiSynchronizedStatement statement) {
    visitStatement(statement);
  }

  public void visitTemplate(@Nonnull PsiTemplate template) {
    visitElement(template);
  }

  public void visitTemplateExpression(@Nonnull PsiTemplateExpression expression) {
    visitExpression(expression);
  }

  public void visitThisExpression(@Nonnull PsiThisExpression expression) {
    visitExpression(expression);
  }

  public void visitThrowStatement(@Nonnull PsiThrowStatement statement) {
    visitStatement(statement);
  }

  public void visitTryStatement(@Nonnull PsiTryStatement statement) {
    visitStatement(statement);
  }

  public void visitTypeCastExpression(@Nonnull PsiTypeCastExpression expression) {
    visitExpression(expression);
  }

  public void visitTypeElement(@Nonnull PsiTypeElement type) {
    visitElement(type);
  }

  public void visitTypeParameter(@Nonnull PsiTypeParameter classParameter) {
    visitClass(classParameter);
  }

  public void visitTypeParameterList(@Nonnull PsiTypeParameterList list) {
    visitElement(list);
  }

  public void visitTypeTestPattern(@Nonnull PsiTypeTestPattern pattern) {
    visitPattern(pattern);
  }

  public void visitUnaryExpression(@Nonnull PsiUnaryExpression expression) {
    visitExpression(expression);
  }

  public void visitUnnamedPattern(@Nonnull PsiUnnamedPattern pattern) {
    visitPattern(pattern);
  }

  public void visitUsesStatement(@Nonnull PsiUsesStatement statement) {
    visitModuleStatement(statement);
  }

  public void visitVariable(@Nonnull PsiVariable variable) {
    visitElement(variable);
  }

  public void visitWhileStatement(@Nonnull PsiWhileStatement statement) {
    visitStatement(statement);
  }

  public void visitYieldStatement(@Nonnull PsiYieldStatement statement) {
    visitStatement(statement);
  }
}