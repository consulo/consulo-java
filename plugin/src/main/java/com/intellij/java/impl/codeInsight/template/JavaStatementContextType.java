package com.intellij.java.impl.codeInsight.template;

import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;

import javax.annotation.Nonnull;

@ExtensionImpl
public class JavaStatementContextType extends JavaCodeContextType {
  public JavaStatementContextType() {
    super("JAVA_STATEMENT", "Statement", JavaGenericContextType.class);
  }

  @Override
  protected boolean isInContext(@Nonnull PsiElement element) {
    return isStatementContext(element);
  }

  static boolean isStatementContext(PsiElement element) {
    if (isAfterExpression(element) || JavaStringContextType.isStringLiteral(element)) {
      return false;
    }

    PsiElement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiLambdaExpression.class);
    if (statement instanceof PsiLambdaExpression) {
      PsiElement body = ((PsiLambdaExpression)statement).getBody();
      if (body != null && PsiTreeUtil.isAncestor(body, element, false)) {
        statement = body;
      }
    }

    return statement != null && statement.getTextRange().getStartOffset() == element.getTextRange().getStartOffset();
  }
}
