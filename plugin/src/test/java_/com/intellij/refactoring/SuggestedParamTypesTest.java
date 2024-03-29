/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 25-May-2010
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import consulo.language.psi.PsiFile;
import com.intellij.java.impl.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManager;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.java.impl.refactoring.util.ParameterTablePanel;
import com.intellij.java.analysis.impl.refactoring.util.VariableData;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;

import javax.swing.*;

public abstract class SuggestedParamTypesTest extends LightCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/refactoring/suggestedTypes/";

  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testPostfixExprUsedAsOutput() throws Exception {
    doTest("byte");
  }

  public void testPostfixExprUnusedAfter() throws Exception {
    doTest("byte", "short", "int", "long", "float", "double");
  }

  public void testLtLtByte() throws Exception {
    doTest("byte", "short", "int", "long");
  }

  public void testLtLtInt() throws Exception {
    doTest("int", "long");
  }

  public void testAssignmentWithConcatenation() throws Exception {
    doTest("String");
  }

  public void testAssignmentWithoutConcatenation() throws Exception {
    doTest("String", "Object", "Serializable", "Comparable<String>", "CharSequence");
  }

  public void testCastInside() throws Exception {
    doTest("A", "Object");
  }

  public void testMultipleCasts() throws Exception {
    doTest("A", "Object");
  }

  public void testCastNoCast() throws Exception {
    doTest("Object");
  }

  public void testNoCastWhenWrapped() throws Exception {
    doTest("Object");
  }

  private void doTest(String... types) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    final Editor editor = getEditor();
    final PsiFile file = getFile();
    final Project project = getProject();

    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();
    PsiElement[] elements;
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (expr != null) {
      elements = new PsiElement[]{expr};
    }
    else {
      elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    }
    assertTrue(elements.length > 0);

    final ExtractMethodProcessor processor =
      new ExtractMethodProcessor(project, editor, elements, null, "Extract Method", "newMethod", null);

    processor.prepare();

    for (final VariableData data : processor.getInputVariables().getInputVariables()) {
      final PsiExpression[] occurrences = ParameterTablePanel.findVariableOccurrences(elements, data.variable);
      final TypeSelectorManager manager = new TypeSelectorManagerImpl(project, data.type, occurrences, true) {
        @Override
        protected boolean isUsedAfter() {
          return processor.isOutputVariable(data.variable);
        }
      };
      final JComponent component = manager.getTypeSelector().getComponent();
      if (types.length > 1) {
        assertTrue("One type suggested", component instanceof JComboBox);
        final DefaultComboBoxModel model = (DefaultComboBoxModel)((JComboBox)component).getModel();
        assertEquals(types.length, model.getSize());
        for (int i = 0, typesLength = types.length; i < typesLength; i++) {
          String type = types[i];
          assertEquals(type, model.getElementAt(i).toString());
        }
      }
      else if (types.length == 1) {
        assertTrue("Multiple types suggested", component instanceof JLabel);
        assertEquals(types[0], ((JLabel)component).getText());
      }
    }
  }


}