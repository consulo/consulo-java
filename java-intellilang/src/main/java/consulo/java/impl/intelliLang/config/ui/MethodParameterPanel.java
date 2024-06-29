/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.impl.intelliLang.config.ui;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.AllIcons;
import consulo.application.Application;
import consulo.application.util.function.Computable;
import consulo.dataContext.DataSink;
import consulo.dataContext.TypeSafeDataProvider;
import consulo.document.Document;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.ide.impl.idea.ui.dualView.TreeTableView;
import consulo.ide.impl.idea.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import consulo.ide.impl.idea.ui.treeStructure.treetable.TreeColumnInfo;
import consulo.ide.impl.idea.util.containers.Convertor;
import consulo.ide.impl.intelliLang.inject.config.ui.AbstractInjectionPanel;
import consulo.ide.impl.intelliLang.inject.config.ui.AdvancedPanel;
import consulo.ide.impl.intelliLang.inject.config.ui.LanguagePanel;
import consulo.java.impl.intelliLang.config.MethodParameterInjection;
import consulo.java.impl.intelliLang.util.PsiUtilEx;
import consulo.language.editor.ui.awt.ReferenceEditorWithBrowseButton;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.awt.BooleanTableCellRenderer;
import consulo.ui.ex.awt.ColumnInfo;
import consulo.ui.ex.awt.speedSearch.TreeTableSpeedSearch;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.*;

public class MethodParameterPanel extends AbstractInjectionPanel<MethodParameterInjection> {

  LanguagePanel myLanguagePanel;  // read by reflection
  AdvancedPanel myAdvancedPanel;

  private JPanel myRoot;
  private JPanel myClassPanel;

  private TreeTableView myParamsTable;

  private final ReferenceEditorWithBrowseButton myClassField;
  private DefaultMutableTreeNode myRootNode;

  private final Map<PsiMethod, MethodParameterInjection.MethodInfo> myData = new HashMap<>();

  @RequiredReadAction
  public MethodParameterPanel(MethodParameterInjection injection, final Project project) {
    super(injection, project);
    $$$setupUI$$$();

    myClassField = new ReferenceEditorWithBrowseButton(
      new BrowseClassListener(project),
      project,
      s -> {
        final Document document = PsiUtilEx.createDocument(s, project);
        document.addDocumentListener(new DocumentAdapter() {
          @Override
          public void documentChanged(final DocumentEvent e) {
            updateParamTree();
            updateTree();
          }
        });
        return document;
      },
      ""
    );
    myClassPanel.add(myClassField, BorderLayout.CENTER);
    myParamsTable.getTree().setShowsRootHandles(true);
    myParamsTable.getTree().setCellRenderer(new ColoredTreeCellRenderer() {

      public void customizeCellRenderer(
        @Nonnull final JTree tree,
        final Object value,
        final boolean selected,
        final boolean expanded,
        final boolean leaf,
        final int row,
        final boolean hasFocus
      ) {
        final Object o = ((DefaultMutableTreeNode)value).getUserObject();
        setIcon(o instanceof PsiMethod ? AllIcons.Nodes.Method : o instanceof PsiParameter ? AllIcons.Nodes.Parameter : null);
        final String name;
        if (o instanceof PsiMethod method) {
          name = PsiFormatUtil.formatMethod(
            method,
            PsiSubstitutor.EMPTY,
            PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
            PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE
          );
        }
        else if (o instanceof PsiParameter parameter) {
          name = PsiFormatUtil.formatVariable(parameter, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE, PsiSubstitutor.EMPTY);
        }
        else name = null;
        final boolean missing = o instanceof PsiElement element && !element.isPhysical();
        if (name != null) {
          append(name, missing ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }

    });
    init(injection.copy());
    new TreeTableSpeedSearch(myParamsTable, new Convertor<>() {
      @Nullable
      @RequiredReadAction
      public String convert(final TreePath o) {
        final Object userObject = ((DefaultMutableTreeNode)o.getLastPathComponent()).getUserObject();
        return userObject instanceof PsiNamedElement namedElement ? namedElement.getName() : null;
      }
    });
    new AnAction("Toggle") {
      @Override
      @RequiredUIAccess
      public void actionPerformed(@Nonnull final AnActionEvent e) {
        performToggleAction();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), myParamsTable);
  }

  private void performToggleAction() {
    final Collection<DefaultMutableTreeNode> selectedInjections = myParamsTable.getSelection();
    boolean enabledExists = false;
    boolean disabledExists = false;
    for (DefaultMutableTreeNode node : selectedInjections) {
      final Boolean nodeSelected = isNodeSelected(node);
      if (Boolean.TRUE == nodeSelected) enabledExists = true;
      else if (Boolean.FALSE == nodeSelected) disabledExists = true;
      if (enabledExists && disabledExists) break;
    }
    boolean allEnabled = !enabledExists && disabledExists;
    for (DefaultMutableTreeNode node : selectedInjections) {
      setNodeSelected(node, allEnabled);
    }
    myParamsTable.updateUI();
  }

  @Nullable
  private PsiType getClassType() {
    final Document document = myClassField.getEditorTextField().getDocument();
    final PsiDocumentManager dm = PsiDocumentManager.getInstance(getProject());
    dm.commitDocument(document);
    final PsiFile psiFile = dm.getPsiFile(document);
    if (psiFile == null) return null;
    try {
      return ((PsiTypeCodeFragment)psiFile).getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e1) {
      return null;
    }
    catch (PsiTypeCodeFragment.NoTypeException e1) {
      return null;
    }
  }

  private void setPsiClass(String name) {
    myClassField.setText(name);
  }

  private void updateParamTree() {
    rebuildTreeModel();
    refreshTreeStructure();
  }

  private void rebuildTreeModel() {
    myData.clear();
    Application.get().runReadAction(() -> {
      final PsiType classType = getClassType();
      final PsiClass[] classes = classType instanceof PsiClassType ? JavaPsiFacade.getInstance(getProject()).
        findClasses(classType.getCanonicalText(), GlobalSearchScope.allScope(getProject())) : PsiClass.EMPTY_ARRAY;
      if (classes.length == 0) return;
      final Set<String> visitedSignatures = new HashSet<>();
      for (PsiClass psiClass : classes) {
        for (PsiMethod method : psiClass.getMethods()) {
          final PsiModifierList modifiers = method.getModifierList();
          if (modifiers.hasModifierProperty(PsiModifier.PRIVATE) || modifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) continue;
          if (MethodParameterInjection.isInjectable(
            method.getReturnType(),
            method.getProject()) || ContainerUtil.find(method.getParameterList().getParameters(),
            p -> MethodParameterInjection.isInjectable(p.getType(), p.getProject())
          ) != null) {
            final MethodParameterInjection.MethodInfo info = MethodParameterInjection.createMethodInfo(method);
            if (!visitedSignatures.add(info.getMethodSignature())) continue;
            myData.put(method, info);
          }
        }
      }
    });
  }

  private void refreshTreeStructure() {
    myRootNode.removeAllChildren();
    final ArrayList<PsiMethod> methods = new ArrayList<>(myData.keySet());
    Collections.sort(methods, (o1, o2) -> {
      final int names = o1.getName().compareTo(o2.getName());
      if (names != 0) return names;
      return o1.getParameterList().getParametersCount() - o2.getParameterList().getParametersCount();
    });
    for (PsiMethod method : methods) {
      final PsiParameter[] params = method.getParameterList().getParameters();
      final DefaultMutableTreeNode methodNode = new DefaultMutableTreeNode(method, true);
      myRootNode.add(methodNode);
      for (final PsiParameter parameter : params) {
        methodNode.add(new DefaultMutableTreeNode(parameter, false));
      }
    }
    final ListTreeTableModelOnColumns tableModel = (ListTreeTableModelOnColumns)myParamsTable.getTableModel();
    tableModel.reload();
    TreeUtil.expandAll(myParamsTable.getTree());
    myParamsTable.revalidate();
  }

  private String getClassName() {
    final PsiType type = getClassType();
    if (type == null) {
      return myClassField.getText();
    }
    return type.getCanonicalText();
  }


  protected void apply(final MethodParameterInjection other) {
    final boolean applyMethods = Application.get().runReadAction((Computable<Boolean>)() -> {
      other.setClassName(getClassName());
      return getClassType() != null;
    });
    if (applyMethods) {
      other.setMethodInfos(ContainerUtil.findAll(myData.values(), MethodParameterInjection.MethodInfo::isEnabled));
    }
  }

  protected void resetImpl() {
    setPsiClass(getOrigInjection().getClassName());

    rebuildTreeModel();
    final Map<String, MethodParameterInjection.MethodInfo> map = new HashMap<>();
    for (PsiMethod method : myData.keySet()) {
      final MethodParameterInjection.MethodInfo methodInfo = myData.get(method);
      map.put(methodInfo.getMethodSignature(), methodInfo);
    }
    for (MethodParameterInjection.MethodInfo info : getOrigInjection().getMethodInfos()) {
      final MethodParameterInjection.MethodInfo curInfo = map.get(info.getMethodSignature());
      if (curInfo != null) {
        System.arraycopy(info.getParamFlags(), 0, curInfo.getParamFlags(), 0, Math.min(info.getParamFlags().length, curInfo.getParamFlags().length));
        curInfo.setReturnFlag(info.isReturnFlag());
      }
      else {
        final PsiMethod missingMethod = MethodParameterInjection.makeMethod(getProject(), info.getMethodSignature());
        myData.put(missingMethod, info.copy());
      }
    }
    refreshTreeStructure();
    final Enumeration enumeration = myRootNode.children();
    while (enumeration.hasMoreElements()) {
      PsiMethod method = (PsiMethod)((DefaultMutableTreeNode)enumeration.nextElement()).getUserObject();
      assert myData.containsKey(method);
    }
  }

  public JPanel getComponent() {
    return myRoot;
  }

  private void createUIComponents() {
    myLanguagePanel = new LanguagePanel(getProject(), getOrigInjection());
    myRootNode = new DefaultMutableTreeNode(null, true);
    myParamsTable = new MyView(new ListTreeTableModelOnColumns(myRootNode, createColumnInfos()));
    myAdvancedPanel = new AdvancedPanel(getProject(), getOrigInjection());    
  }

  @Nullable
  private Boolean isNodeSelected(final DefaultMutableTreeNode o) {
    final Object userObject = o.getUserObject();
    if (userObject instanceof PsiMethod method) {
      return MethodParameterInjection.isInjectable(method.getReturnType(), method.getProject()) ? myData.get(method).isReturnFlag() : null;
    }
    else if (userObject instanceof PsiParameter parameter) {
      final PsiMethod method = getMethodByNode(o);
      final int index = method.getParameterList().getParameterIndex(parameter);
      return MethodParameterInjection.isInjectable(parameter.getType(), method.getProject())
        ? myData.get(method).getParamFlags()[index] : null;
    }
    return null;
  }

  private void setNodeSelected(final DefaultMutableTreeNode o, final boolean value) {
    final Object userObject = o.getUserObject();
    if (userObject instanceof PsiMethod method) {
      myData.get(method).setReturnFlag(value);
    }
    else if (userObject instanceof PsiParameter parameter) {
      final PsiMethod method = getMethodByNode(o);
      final int index = method.getParameterList().getParameterIndex(parameter);
      myData.get(method).getParamFlags()[index] = value;
    }
  }

  private static PsiMethod getMethodByNode(final DefaultMutableTreeNode o) {
    final Object userObject = o.getUserObject();
    if (userObject instanceof PsiMethod method) {
      return method;
    }
    return (PsiMethod)((DefaultMutableTreeNode)o.getParent()).getUserObject();
  }

  private ColumnInfo[] createColumnInfos() {
    return new ColumnInfo[]{
        new ColumnInfo<DefaultMutableTreeNode, Boolean>(" ") { // "" for the first column's name isn't a good idea
          final BooleanTableCellRenderer myRenderer = new BooleanTableCellRenderer();

          public Boolean valueOf(DefaultMutableTreeNode o) {
            return isNodeSelected(o);
          }

          public int getWidth(JTable table) {
            return myRenderer.getPreferredSize().width;
          }

          public TableCellEditor getEditor(DefaultMutableTreeNode o) {
            return new DefaultCellEditor(new JCheckBox());
          }

          public TableCellRenderer getRenderer(DefaultMutableTreeNode o) {
            myRenderer.setEnabled(isCellEditable(o));
            return myRenderer;
          }

          public void setValue(DefaultMutableTreeNode o, Boolean value) {
            setNodeSelected(o, Boolean.TRUE.equals(value));
          }

          public Class<Boolean> getColumnClass() {
            return Boolean.class;
          }

          public boolean isCellEditable(DefaultMutableTreeNode o) {
            return valueOf(o) != null;
          }

        }, new TreeColumnInfo("Method/Parameters")
    };
  }

  private class BrowseClassListener implements ActionListener {
    private final Project myProject;

    public BrowseClassListener(Project project) {
      myProject = project;
    }

    public void actionPerformed(ActionEvent e) {
      final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);
      final TreeClassChooser chooser = factory.createAllProjectScopeChooser("Select Class");
      chooser.showDialog();
      final PsiClass psiClass = chooser.getSelected();
      if (psiClass != null) {
        setPsiClass(psiClass.getQualifiedName());
        updateParamTree();
        updateTree();
      }
    }
  }

  private class MyView extends TreeTableView implements TypeSafeDataProvider {
    public MyView(ListTreeTableModelOnColumns treeTableModel) {
      super(treeTableModel);
    }

    public void calcData(final Key<?> key, final DataSink sink) {
      if (PsiElement.KEY == key) {
        final Collection selection = getSelection();
        if (!selection.isEmpty()) {
          final Object o = ((DefaultMutableTreeNode)selection.iterator().next()).getUserObject();
          if (o instanceof PsiElement element) {
            sink.put(PsiElement.KEY, element);
          }
        }
      }
    }
  }

  private void $$$setupUI$$$() {
  }

}
