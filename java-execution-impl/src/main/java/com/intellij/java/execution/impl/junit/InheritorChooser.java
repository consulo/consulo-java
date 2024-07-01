/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.junit;

import com.intellij.java.execution.impl.junit2.PsiMemberParameterizedLocation;
import com.intellij.java.execution.impl.junit2.info.MethodLocation;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.util.PsiClassUtil;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressManager;
import consulo.document.Document;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.Location;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.TextEditor;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.popup.AWTPopupFactory;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.function.Condition;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InheritorChooser {

  protected void runForClasses(final List<PsiClass> classes, final PsiMethod method, final ConfigurationContext context, final Runnable performRunnable) {
    performRunnable.run();
  }

  protected void runForClass(final PsiClass aClass, final PsiMethod psiMethod, final ConfigurationContext context, final Runnable performRunnable) {
    performRunnable.run();
  }

  @RequiredUIAccess
  public boolean runMethodInAbstractClass(
    final ConfigurationContext context,
    final Runnable performRunnable,
    final PsiMethod psiMethod,
    final PsiClass containingClass
  ) {
    return runMethodInAbstractClass(context, performRunnable, psiMethod, containingClass, psiClass -> psiClass.hasModifierProperty(PsiModifier.ABSTRACT));
  }

  @RequiredUIAccess
  public boolean runMethodInAbstractClass(
    final ConfigurationContext context,
    final Runnable performRunnable,
    final PsiMethod psiMethod,
    final PsiClass containingClass,
    final Condition<PsiClass> acceptAbstractCondition
  ) {
    if (containingClass != null && acceptAbstractCondition.value(containingClass)) {
      final Location location = context.getLocation();
      if (location instanceof MethodLocation methodLocation) {
        final PsiClass aClass = methodLocation.getContainingClass();
        if (aClass != null && !aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          return false;
        }
      } else if (location instanceof PsiMemberParameterizedLocation) {
        return false;
      }

      final List<PsiClass> classes = new ArrayList<>();
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() ->
      {
        final boolean isJUnit5 = ReadAction.compute(() -> JUnitUtil.isJUnit5(containingClass));
        ClassInheritorsSearch.search(containingClass).forEach(aClass ->
        {
          if (isJUnit5 && JUnitUtil.isJUnit5TestClass(aClass, true)
            || PsiClassUtil.isRunnableClass(aClass, true, true)) {
            classes.add(aClass);
          }
          return true;
        });
      }, "Search for " + containingClass.getQualifiedName() + " inheritors", true, containingClass.getProject())) {
        return true;
      }

      if (classes.size() == 1) {
        runForClass(classes.get(0), psiMethod, context, performRunnable);
        return true;
      }
      if (classes.isEmpty()) {
        return false;
      }
      final FileEditor fileEditor = context.getDataContext().getData(FileEditor.KEY);
      if (fileEditor instanceof TextEditor textEditor) {
        final Document document = textEditor.getEditor().getDocument();
        final PsiFile containingFile = PsiDocumentManager.getInstance(context.getProject()).getPsiFile(document);
        if (containingFile instanceof PsiClassOwner classOwner) {
          final List<PsiClass> psiClasses = new ArrayList<>(Arrays.asList(classOwner.getClasses()));
          psiClasses.retainAll(classes);
          if (psiClasses.size() == 1) {
            runForClass(psiClasses.get(0), psiMethod, context, performRunnable);
            return true;
          }
        }
      }
      final int numberOfInheritors = classes.size();
      final PsiClassListCellRenderer renderer = new PsiClassListCellRenderer() {
        @Override
        protected boolean customizeNonPsiElementLeftRenderer(ColoredListCellRenderer renderer, JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value == null) {
            renderer.append("All (" + numberOfInheritors + ")");
            return true;
          }
          return super.customizeNonPsiElementLeftRenderer(renderer, list, value, index, selected, hasFocus);
        }
      };
      Collections.sort(classes, renderer.getComparator());

      //suggest to run all inherited tests
      classes.add(0, null);
      final JBList<PsiClass> list = new JBList<>(classes);
      list.setCellRenderer(renderer);
      ((AWTPopupFactory) JBPopupFactory.getInstance()).createListPopupBuilder(list)
          .setItemChoosenCallback(() ->
          {
            final Object[] values = list.getSelectedValues();
            if (values == null) {
              return;
            }
            chooseAndPerform(values, psiMethod, context, performRunnable, classes);
          })
          .setTitle("Choose executable classes to run " + (psiMethod != null ? psiMethod.getName() : containingClass.getName()))
          .setMovable(false).setResizable(false).setRequestFocus(true).createPopup().showInBestPositionFor(context.getDataContext());
      return true;
    }
    return false;
  }

  private void chooseAndPerform(Object[] values, PsiMethod psiMethod, ConfigurationContext context, Runnable performRunnable, List<PsiClass> classes) {
    classes.remove(null);
    if (values.length == 1) {
      final Object value = values[0];
      if (value instanceof PsiClass psiClass) {
        runForClass(psiClass, psiMethod, context, performRunnable);
      } else {
        runForClasses(classes, psiMethod, context, performRunnable);
      }
      return;
    }
    if (ArrayUtil.contains(null, values)) {
      runForClasses(classes, psiMethod, context, performRunnable);
    } else {
      final List<PsiClass> selectedClasses = new ArrayList<>();
      for (Object value : values) {
        if (value instanceof PsiClass) {
          selectedClasses.add((PsiClass) value);
        }
      }
      runForClasses(selectedClasses, psiMethod, context, performRunnable);
    }
  }
}
