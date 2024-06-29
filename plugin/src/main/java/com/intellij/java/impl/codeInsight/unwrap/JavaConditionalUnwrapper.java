package com.intellij.java.impl.codeInsight.unwrap;

import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class JavaConditionalUnwrapper extends JavaUnwrapper {
  public JavaConditionalUnwrapper() {
    super(CodeInsightLocalize.unwrapConditional().get());
  }

  @Override
  public boolean isApplicableTo(PsiElement e) {
    return e.getParent() instanceof PsiConditionalExpression;
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return e.getParent();
  }
  
  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiConditionalExpression cond = (PsiConditionalExpression)element.getParent();

    PsiElement savedBlock;
    
    if (cond.getElseExpression() == element) {
      savedBlock = element;
    }
    else {
      savedBlock = cond.getThenExpression();
    }

    context.extractElement(savedBlock, cond);

    if (cond.getParent() instanceof PsiExpressionList) {
      context.delete(cond);
    }
    else {
      context.deleteExactly(cond);
    }
  }
}
