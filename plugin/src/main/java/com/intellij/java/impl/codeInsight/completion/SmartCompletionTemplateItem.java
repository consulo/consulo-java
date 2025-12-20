package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.lookup.TypedLookupItem;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.language.editor.completion.lookup.LookupItem;
import consulo.language.editor.template.Template;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class SmartCompletionTemplateItem extends LookupItem<Template> implements TypedLookupItem {
  @NonNls private static final String PLACEHOLDER = "xxx";
  private final PsiElement myContext;

  public SmartCompletionTemplateItem(Template o, PsiElement context) {
    super(o, o.getKey());
    myContext = context;
  }


  @Override
  public PsiType getType() {
    Template template = getObject();
    String text = template.getTemplateText();
    StringBuilder resultingText = new StringBuilder(text);

    int segmentsCount = template.getSegmentsCount();

    for (int j = segmentsCount - 1; j >= 0; j--) {
      if (template.getSegmentName(j).equals(Template.END)) {
        continue;
      }

      int segmentOffset = template.getSegmentOffset(j);

      resultingText.insert(segmentOffset, PLACEHOLDER);
    }

    try {
      PsiExpression templateExpression = JavaPsiFacade.getElementFactory(myContext.getProject()).createExpressionFromText(resultingText.toString(), myContext);
      return templateExpression.getType();
    }
    catch (IncorrectOperationException e) { // can happen when text of the template does not form an expression
      return null;
    }
  }
}
