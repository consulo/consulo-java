/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInspection;

import com.intellij.java.impl.codeInsight.intention.impl.ConcatenationToMessageFormatAction;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.testFramework.LightIdeaTestCase;

import java.util.ArrayList;

public abstract class ConcatenationToMessageFormatActionTest extends LightIdeaTestCase {
  
  public void test1() throws Exception{
    String text = "\"aaa 'bbb' '\" + ((java.lang.String)ccc) + \"'\"";
    PsiExpression expression = JavaPsiFacade.getInstance(getProject()).getElementFactory().createExpressionFromText(
      text, null
    );
    StringBuilder result = new StringBuilder();
    ConcatenationToMessageFormatAction.buildMessageFormatString(expression,
                                                                result,
                                                                new ArrayList<PsiExpression>());
    assertEquals("aaa ''bbb'' ''{0}''", result.toString());
  }
  
  public void test2() throws Exception {
    String text = "1 + 2 + 3 + \"{}'\" + '\\n' + ((java.lang.String)ccc)";
    PsiExpression expression = JavaPsiFacade.getElementFactory(getProject()).createExpressionFromText(text, null);
    StringBuilder result = new StringBuilder();
    ArrayList<PsiExpression> args = new ArrayList<PsiExpression>();
    ConcatenationToMessageFormatAction.buildMessageFormatString(expression, result, args);
    assertEquals("{0}'{'}''\\n{1}", result.toString());
    assertEquals(2, args.size());
    assertEquals("1 + 2 + 3", args.get(0).getText());
    assertEquals("ccc", args.get(1).getText());
  }
}
