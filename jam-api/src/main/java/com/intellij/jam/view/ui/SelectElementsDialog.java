/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.jam.view.ui;

import com.intellij.jam.JamMessages;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.ui.EnableDisableAction;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import consulo.awt.TargetAWT;
import consulo.ide.IconDescriptorUpdaters;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.*;

public class SelectElementsDialog extends DialogWrapper {
  private final TableView<PsiElement> myTable;
  private final Set<PsiElement> mySelectedElements = new HashSet<PsiElement>();

  public SelectElementsDialog(Project project, List<PsiElement> elements, String title, String elementsTitle) {
    super(project, true);
    ColumnInfo[] columnInfos = createColumnInfos();
    columnInfos[1].setName(elementsTitle);
    myTable = new TableView<PsiElement>(new ListTableModel<PsiElement>(columnInfos));
    myTable.setTableHeader(null);
    final ListTableModel<PsiElement> model = myTable.getListTableModel();
    final ArrayList<PsiElement> arrayList = new ArrayList<PsiElement>(elements);
    Collections.sort(arrayList, new Comparator<PsiElement>() {
      public int compare(final PsiElement o1, final PsiElement o2) {
        final int filesResult = Comparing.compare(o1.getContainingFile().getName(), o2.getContainingFile().getName());
        if (filesResult != 0) return filesResult;
        return getPresentableText(o1).compareTo(getPresentableText(o2));
      }
    });
    model.setItems(arrayList);
    model.setSortable(false);
    new TableSpeedSearch(myTable);
    new EnableDisableAction() {
      protected JTable getTable() {
        return myTable;
      }

      protected boolean isRowChecked(int row) {
        return mySelectedElements.contains(model.getItems().get(row));
      }

      protected void applyValue(int[] rows, boolean valueToBeSet) {
        for (int row : rows) {
          if (valueToBeSet) {
            mySelectedElements.add(model.getItems().get(row));
          }
          else {
            mySelectedElements.remove(model.getItems().get(row));
          }
        }
        int[] selection = myTable.getSelectedRows();
        model.fireTableDataChanged();
        TableUtil.selectRows(myTable, selection);
        onSelectionChanged();
      }
    }.register();

    setTitle(title);
    init();
    onSelectionChanged();
  }

  protected JComponent createCenterPanel() {
    return ScrollPaneFactory.createScrollPane(myTable.getComponent());
  }

  public Collection<PsiElement> getSelectedItems() {
    return mySelectedElements;
  }

  protected void onSelectionChanged() {
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  @SuppressWarnings("StaticFieldReferencedViaSubclass")
  private static String getPresentableText(final PsiElement psiElement) {
    if (psiElement instanceof PsiFile) {
      return ((PsiFile)psiElement).getName();
    }
    else if (psiElement instanceof PsiClass) {
      return PsiFormatUtil.formatClass((PsiClass)psiElement, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_FQ_NAME);
    }
    else if (psiElement instanceof PsiMethod) {
      return PsiFormatUtil.formatMethod((PsiMethod)psiElement, PsiSubstitutor.EMPTY,
                                        PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS | PsiFormatUtil.SHOW_CONTAINING_CLASS, 0);
    }
    else if (psiElement instanceof PsiField) {
      return PsiFormatUtil.formatVariable((PsiField)psiElement,
                                          PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                          PsiSubstitutor.EMPTY);
    }
    else if (psiElement instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)psiElement;
      return xmlTag.getContainingFile().getName() + ": <" + xmlTag.getName() + ">";
    }
    else if (psiElement instanceof PsiAnnotation) {
      final PsiAnnotation annotation = (PsiAnnotation)psiElement;
      final PsiMember member = PsiTreeUtil.getParentOfType(annotation, PsiMember.class, true);
      if (member != null) {
        return getPresentableText(member) + ": @" + annotation.getQualifiedName();
      }
      else {
        return annotation.getContainingFile().getName() + ": @" + annotation.getQualifiedName();
      }
    }
    else if (psiElement instanceof NavigationItem) {
      final NavigationItem navigationItem = (NavigationItem)psiElement;
      final ItemPresentation presentation = navigationItem.getPresentation();
      if (presentation != null) {
        return StringUtil.notNullize(presentation.getPresentableText(), JamMessages.message("unnamed.element.presentable.name"));
      }
    }
    return psiElement.toString();
  }

  private ColumnInfo[] createColumnInfos() {
    final DefaultCellEditor editor = new DefaultCellEditor(new JCheckBox());
    editor.setClickCountToStart(1);
    final BooleanTableCellRenderer renderer = new BooleanTableCellRenderer();
    return new ColumnInfo[]{new ColumnInfo<PsiElement, Boolean>("") {
      public Boolean valueOf(PsiElement psiElement) {
        return Boolean.valueOf(mySelectedElements.contains(psiElement));
      }

      public boolean isCellEditable(PsiElement psiElement) {
        return true;
      }

      public Class getColumnClass() {
        return boolean.class;
      }

      public TableCellRenderer getRenderer(final PsiElement psiElement) {
        return renderer;
      }

      public int getWidth(JTable table) {
        return new JCheckBox().getPreferredSize().width + 2;
      }

      public TableCellEditor getEditor(PsiElement psiElement) {
        return editor;
      }

      public void setValue(PsiElement psiElement, Boolean aBoolean) {
        if (aBoolean.booleanValue()) {
          mySelectedElements.add(psiElement);
        }
        else {
          mySelectedElements.remove(psiElement);
        }
        onSelectionChanged();
      }
    }, new ColumnInfo<PsiElement, String>("") {
      public String valueOf(PsiElement psiElement) {
        return getPresentableText(psiElement);
      }

      public TableCellRenderer getRenderer(final PsiElement psiElement) {
        return new ColoredTableCellRenderer() {
          protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
            append((String)value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            if (!psiElement.isPhysical()) {
              append("  [" + JamMessages.message("postfix.not.physical.element") + "]", SimpleTextAttributes.GRAY_ATTRIBUTES);
            }
            setIcon(IconDescriptorUpdaters.getIcon(psiElement, 0));
            setOpaque(false);
            setIconOpaque(false);
          }
        };
        //return PeerFactory.getInstance().getUIHelper().createPsiElementRenderer(psiElement, myProject);
      }
    }};
  }
}
