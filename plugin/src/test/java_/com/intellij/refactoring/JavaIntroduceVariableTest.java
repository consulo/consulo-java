/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring;

import consulo.util.lang.StringUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JavaIntroduceVariableTest extends LightCodeInsightTestCase {
  public void testIntroduceBasedOnLiterals() throws Exception {
    doTest("getA(\"simple\")", "simple");
    doTest("getA(\"SimpleName\")", "simpleName", "name");
    doTest("getA(\"simpleName\")", "simpleName", "name");
    doTest("getA(\"simpleClass\")", "simpleClass", "aClass");
    doTest("getA(\"short\")", "aShort");
    doTest("getA(\"boolean\")", "aBoolean");
    doTest("getA().getB(1, \"name\")", "name");
    doTest("getA(\"NAME\")", "name");
    doTest("getA(\"name\")", VariableKind.STATIC_FINAL_FIELD, "NAME");
    doTest("getA(\"SimpleName\")", VariableKind.STATIC_FINAL_FIELD, "SIMPLE_NAME");
    doTest("get(getB().getA(\"SimpleName\").getC())", "simpleName", "name");
  }

  protected static void doTest(String expression, VariableKind kind, PsiType type, String...results) throws Exception {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    final PsiExpression expr = factory.createExpressionFromText(expression, null);
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(getProject());
    final SuggestedNameInfo info = codeStyleManager.suggestVariableName(kind, null, expr, type);
    assert info.names.length >= results.length : msg("Can't find some variants", info.names, results);
    for (int i = 0; i < results.length; i++) {
      if (!results[i].equals(info.names[i])) {
        throw new Exception(msg("", info.names, results));
      }
    }
  }

  private static String msg(String s, String[] names, String[] results) {
    return s + ". Expected at first positions: [" + StringUtil.join(results, ",") + "] Found: [" + StringUtil.join(names, ",") + "]";
  }

  protected static void doTest(String expression, String...results) throws Exception {
    doTest(expression, VariableKind.LOCAL_VARIABLE, results);
  }

  protected static void doTest(String expression, VariableKind kind, String...results) throws Exception {
    doTest(expression, kind, PsiType.getJavaLangString(getPsiManager(), GlobalSearchScope.allScope(getProject())), results);
  }
}
