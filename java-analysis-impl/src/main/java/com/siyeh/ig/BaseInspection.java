/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public abstract class BaseInspection extends BaseJavaBatchLocalInspectionTool {
  @Nonnull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Nonnull
  @Override
  public abstract LocalizeValue getDisplayName();

  @Override
  @Nonnull
  public LocalizeValue getGroupDisplayName() {
    return GroupDisplayNameUtil.getGroupDisplayName(getClass());
  }

  @Nonnull
  protected abstract String buildErrorString(Object... infos);

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return false;
  }

  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return null;
  }

  @Nonnull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    return InspectionGadgetsFix.EMPTY_ARRAY;
  }

  public abstract BaseInspectionVisitor buildVisitor();

  @Override
  @Nonnull
  public final PsiElementVisitor buildVisitorImpl(@Nonnull ProblemsHolder holder,
                                                  boolean isOnTheFly,
                                                  LocalInspectionToolSession session,
                                                  Object state) {
    final PsiFile file = holder.getFile();
    assert file.isPhysical();
    if (!shouldInspect(file)) {
      return new PsiElementVisitor() {
      };
    }
    final BaseInspectionVisitor visitor = buildVisitor();
    visitor.setProblemsHolder(holder);
    visitor.setOnTheFly(isOnTheFly);
    visitor.setInspection(this);
    return visitor;
  }

  /**
   * To check precondition(s) on the entire file, to prevent doing the check on every PsiElement visited.
   * Useful for e.g. a {@link PsiUtil#isLanguageLevel5OrHigher(PsiElement)} check
   * which will be the same for all elements in the specified file.
   * When this method returns false, {@link #buildVisitor()} will not be called.
   */
  public boolean shouldInspect(PsiFile file) {
    return true;
  }

  protected JFormattedTextField prepareNumberEditor(IntSupplier getter, IntConsumer setter) {
    final NumberFormat formatter = NumberFormat.getIntegerInstance();
    formatter.setParseIntegerOnly(true);
    final JFormattedTextField valueField = new JFormattedTextField(formatter);
    int value = getter.getAsInt();
    valueField.setValue(value);
    valueField.setColumns(2);

    // hack to work around text field becoming unusably small sometimes when using GridBagLayout
    valueField.setMinimumSize(valueField.getPreferredSize());

    UIUtil.fixFormattedField(valueField);
    final Document document = valueField.getDocument();
    document.addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent evt) {
        try {
          valueField.commitEdit();
          final Number number = (Number) valueField.getValue();
          setter.accept(number.intValue());
        } catch (ParseException e) {
          // No luck this time. Will update the field when correct value is entered.
        }
      }
    });
    return valueField;
  }

  @SafeVarargs
  public static void parseString(String string, List<String>... outs) {
    final List<String> strings = StringUtil.split(string, ",");
    for (List<String> out : outs) {
      out.clear();
    }
    final int iMax = strings.size();
    for (int i = 0; i < iMax; i += outs.length) {
      for (int j = 0; j < outs.length; j++) {
        final List<String> out = outs[j];
        if (i + j >= iMax) {
          out.add("");
        } else {
          out.add(strings.get(i + j));
        }
      }
    }
  }

  @SafeVarargs
  public static String formatString(List<String>... strings) {
    final StringBuilder buffer = new StringBuilder();
    final int size = strings[0].size();
    if (size > 0) {
      formatString(strings, 0, buffer);
      for (int i = 1; i < size; i++) {
        buffer.append(',');
        formatString(strings, i, buffer);
      }
    }
    return buffer.toString();
  }

  private static void formatString(List<String>[] strings, int index, StringBuilder out) {
    out.append(strings[0].get(index));
    for (int i = 1; i < strings.length; i++) {
      out.append(',');
      out.append(strings[i].get(index));
    }
  }
}