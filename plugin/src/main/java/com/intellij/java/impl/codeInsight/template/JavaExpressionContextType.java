package com.intellij.java.impl.codeInsight.template;

import com.intellij.java.impl.codeInsight.completion.JavaKeywordCompletion;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiTypeElement;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import static consulo.language.pattern.PlatformPatterns.psiElement;

@ExtensionImpl
public class JavaExpressionContextType extends JavaCodeContextType implements JavaLikeExpressionContextType {
  public JavaExpressionContextType() {
    super("JAVA_EXPRESSION", LocalizeValue.localizeTODO("Expression"), JavaGenericContextType.class);
  }

  @Override
  protected boolean isInContext(@Nonnull PsiElement element) {
    return isExpressionContext(element);
  }

  static boolean isExpressionContext(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) {
      return false;
    }
    if (((PsiJavaCodeReferenceElement)parent).isQualified()) {
      return false;
    }
    if (parent.getParent() instanceof PsiMethodCallExpression) {
      return false;
    }

    if (psiElement().withParents(PsiTypeElement.class, PsiMember.class).accepts(parent)) {
      return false;
    }

    if (JavaKeywordCompletion.isInsideParameterList(element)) {
      return false;
    }

    return !isAfterExpression(element);
  }
}
