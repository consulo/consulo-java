/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Sep 4, 2002
 * Time: 6:26:27 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.psi.*;
import consulo.application.progress.ProgressManager;
import consulo.application.util.function.Computable;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.ide.impl.ui.impl.PopupChooserBuilder;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.ui.PsiElementListCellRenderer;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.popup.JBPopup;
import consulo.undoRedo.CommandProcessor;

import javax.swing.*;
import java.util.*;

public class ImplementAbstractMethodHandler {
  private static final Logger LOG = Logger.getInstance(ImplementAbstractMethodHandler.class);

  private final Project myProject;
  private final Editor myEditor;
  private final PsiMethod myMethod;
  private JList myList;

  public ImplementAbstractMethodHandler(Project project, Editor editor, PsiMethod method) {
    myProject = project;
    myEditor = editor;
    myMethod = method;
  }

  public void invoke() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement[][] result = new PsiElement[1][];
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> myProject.getApplication().runReadAction(() -> {
      PsiClass psiClass = myMethod.getContainingClass();
      if (!psiClass.isValid()) return;
      if (!psiClass.isEnum()) {
        result[0] = getClassImplementations(psiClass);
      }
      else {
        List<PsiElement> enumConstants = new ArrayList<>();
        for (PsiField field : psiClass.getFields()) {
          if (field instanceof PsiEnumConstant enumConstant) {
            PsiEnumConstantInitializer initializingClass = enumConstant.getInitializingClass();
            if (initializingClass != null) {
              PsiMethod method = initializingClass.findMethodBySignature(myMethod, true);
              if (method == null || !method.getContainingClass().equals(initializingClass)) {
                enumConstants.add(initializingClass);
              }
            }
            else {
              enumConstants.add(field);
            }
          }
        }
        result[0] = PsiUtilCore.toPsiElementArray(enumConstants);
      }
    }), CodeInsightLocalize.intentionImplementAbstractMethodSearchingForDescendantsProgress().get(), true, myProject);

    if (result[0] == null) return;

    if (result[0].length == 0) {
      Messages.showMessageDialog(
        myProject,
        CodeInsightLocalize.intentionImplementAbstractMethodErrorNoClassesMessage().get(),
        CodeInsightLocalize.intentionImplementAbstractMethodErrorNoClassesTitle().get(),
        UIUtil.getInformationIcon()
      );
      return;
    }

    if (result[0].length == 1) {
      implementInClass(new Object[] {result[0][0]});
      return;
    }

    MyPsiElementListCellRenderer elementListCellRenderer = new MyPsiElementListCellRenderer();
    elementListCellRenderer.sort(result[0]);
    myList = new JBList(result[0]);
    myList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    Runnable runnable = () -> {
      int index = myList.getSelectedIndex();
      if (index < 0) return;
      implementInClass(myList.getSelectedValues());
    };
    myList.setCellRenderer(elementListCellRenderer);
    PopupChooserBuilder builder = new PopupChooserBuilder(myList);
    elementListCellRenderer.installSpeedSearch(builder);

    JBPopup popup = builder.setTitle(CodeInsightLocalize.intentionImplementAbstractMethodClassChooserTitle().get())
      .setItemChoosenCallback(runnable)
      .createPopup();

    EditorPopupHelper.getInstance().showPopupInBestPositionFor(myEditor, popup);
  }

  @RequiredUIAccess
  public void implementInClass(Object[] selection) {
    for (Object o : selection) {
      if (!((PsiElement)o).isValid()) return;
    }
    CommandProcessor.getInstance().executeCommand(
      myProject,
      () -> {
        LinkedHashSet<PsiClass> classes = new LinkedHashSet<>();
        for (Object o : selection) {
          if (o instanceof PsiEnumConstant enumConstant) {
            classes.add(myProject.getApplication().runWriteAction(new Computable<>(){
              @Override
              public PsiClass compute() {
                return enumConstant.getOrCreateInitializingClass();
              }
            }));
          }
          else {
            classes.add((PsiClass)o);
          }
        }
        if (!FileModificationService.getInstance().preparePsiElementsForWrite(classes)) return;
        myProject.getApplication().runWriteAction(() -> {
          for (PsiClass psiClass : classes) {
            try {
              OverrideImplementUtil.overrideOrImplement(psiClass, myMethod);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      },
      CodeInsightLocalize.intentionImplementAbstractMethodCommandName().get(),
      null
    );
  }

  private PsiClass[] getClassImplementations(PsiClass psiClass) {
    ArrayList<PsiClass> list = new ArrayList<>();
    for (PsiClass inheritor : ClassInheritorsSearch.search(psiClass, true)) {
      if (!inheritor.isInterface()) {
        PsiMethod method = inheritor.findMethodBySignature(myMethod, true);
        if (method == null || !method.getContainingClass().equals(psiClass)) continue;
        list.add(inheritor);
      }
    }

    return list.toArray(new PsiClass[list.size()]);
  }

  private static class MyPsiElementListCellRenderer extends PsiElementListCellRenderer<PsiElement> {
    private final PsiClassListCellRenderer myRenderer;

    public MyPsiElementListCellRenderer() {
      myRenderer = new PsiClassListCellRenderer();
    }

    void sort(PsiElement[] result) {
      Comparator<PsiClass> comparator = myRenderer.getComparator();
      Arrays.sort(result, (o1, o2) -> {
        if (o1 instanceof PsiEnumConstant enumConstant1 && o2 instanceof PsiEnumConstant enumConstant2) {
          return enumConstant1.getName().compareTo(enumConstant2.getName());
        }
        if (o1 instanceof PsiEnumConstant) return -1;
        if (o2 instanceof PsiEnumConstant) return 1;
        return comparator.compare((PsiClass)o1, (PsiClass)o2);
      });
    }

    @Override
    public String getElementText(PsiElement element) {
      return element instanceof PsiClass psiClass ? myRenderer.getElementText(psiClass) : ((PsiEnumConstant)element).getName();
    }

    @Override
    protected String getContainerText(PsiElement element, String name) {
      return element instanceof PsiClass
        ? PsiClassListCellRenderer.getContainerTextStatic(element)
        : ((PsiEnumConstant)element).getContainingClass().getQualifiedName();
    }

    @Override
    protected int getIconFlags() {
      return 0;
    }
  }
}
