/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.changeSignature;

import com.intellij.java.impl.refactoring.changeSignature.inCallers.JavaCallerChooser;
import com.intellij.java.impl.refactoring.ui.JavaCodeFragmentTableCellEditor;
import com.intellij.java.impl.refactoring.ui.JavaComboBoxVisibilityPanel;
import com.intellij.java.impl.refactoring.util.CanonicalTypes;
import com.intellij.java.impl.refactoring.util.RefactoringMessageUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.application.AllIcons;
import consulo.application.util.function.Computable;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorFontType;
import consulo.document.Document;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.ide.impl.idea.ui.TableColumnAnimator;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.changeSignature.CallerChooserBase;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureDialogBase;
import consulo.language.editor.refactoring.changeSignature.MethodDescriptor;
import consulo.language.editor.refactoring.changeSignature.ParameterTableModelItemBase;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.editor.refactoring.ui.CodeFragmentTableCellRenderer;
import consulo.language.editor.refactoring.ui.JBListTableWitEditors;
import consulo.language.editor.refactoring.ui.VisibilityPanelBase;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.editor.ui.awt.TextFieldCompletionProvider;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.table.*;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.util.DialogUtil;
import consulo.ui.image.ImageEffects;
import consulo.usage.UsageInfo;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static consulo.language.editor.refactoring.changeSignature.ChangeSignatureHandler.REFACTORING_NAME;

/**
 * @author Konstantin Bulenkov
 */
public class JavaChangeSignatureDialog extends ChangeSignatureDialogBase<ParameterInfoImpl, PsiMethod, String, JavaMethodDescriptor,
    ParameterTableModelItemBase<ParameterInfoImpl>, JavaParameterTableModel> {
  private ExceptionsTableModel myExceptionsModel;
  protected Set<PsiMethod> myMethodsToPropagateExceptions;
  private AnActionButton myPropExceptionsButton;
  private Tree myExceptionPropagationTree;

  public JavaChangeSignatureDialog(Project project, PsiMethod method, boolean allowDelegation, PsiElement context) {
    this(project, new JavaMethodDescriptor(method), allowDelegation, context);
  }

  protected JavaChangeSignatureDialog(Project project, JavaMethodDescriptor descriptor, boolean allowDelegation, PsiElement context) {
    super(project, descriptor, allowDelegation, context);
  }

  @Nonnull
  public static JavaChangeSignatureDialog createAndPreselectNew(
    @Nonnull Project project,
    @Nonnull PsiMethod method,
    @Nonnull List<? extends ParameterInfoImpl> parameterInfos,
    boolean allowDelegation,
    PsiReferenceExpression refExpr
  ) {
    return createAndPreselectNew(project, method, parameterInfos, allowDelegation, refExpr, null);
  }

  @Nonnull
  public static JavaChangeSignatureDialog createAndPreselectNew(
    final Project project,
    final PsiMethod method,
    final List<? extends ParameterInfoImpl> parameterInfos,
    final boolean allowDelegation,
    final PsiReferenceExpression refExpr,
    @Nullable Consumer<? super List<ParameterInfoImpl>> callback
  ) {
    return new JavaChangeSignatureDialog(project, method, allowDelegation, refExpr) {
      @Override
      protected int getSelectedIdx() {
        for (int i = 0; i < parameterInfos.size(); i++) {
          ParameterInfoImpl info = parameterInfos.get(i);
          if (info.oldParameterIndex < 0) {
            return i;
          }
        }
        return super.getSelectedIdx();
      }


      @Override
      protected BaseRefactoringProcessor createRefactoringProcessor() {
        final List<ParameterInfoImpl> parameters = getParameters();
        return new ChangeSignatureProcessor(myProject,
            myMethod.getMethod(),
            isGenerateDelegate(),
            getVisibility(),
            getMethodName(),
            getReturnType(),
            parameters.toArray(new ParameterInfoImpl[0]),
            getExceptions(),
            myMethodsToPropagateParameters,
            myMethodsToPropagateExceptions) {
          @Override
          protected void performRefactoring(@Nonnull UsageInfo[] usages) {
            super.performRefactoring(usages);
            if (callback != null) {
              callback.accept(getParameters());
            }
          }
        };
      }
    };
  }

  @Override
  protected VisibilityPanelBase<String> createVisibilityControl() {
    return new JavaComboBoxVisibilityPanel();
  }

  @Override
  protected JComponent createCenterPanel() {
    JComponent centerPanel = super.createCenterPanel();
    myPropagateParamChangesButton.setVisible(true);
    return centerPanel;
  }

  @Override
  protected void updatePropagateButtons() {
    super.updatePropagateButtons();
    myPropExceptionsButton.setEnabled(!isGenerateDelegate() && mayPropagateExceptions());
  }

  protected boolean mayPropagateExceptions() {
    ThrownExceptionInfo[] exceptions = myExceptionsModel.getThrownExceptions();
    PsiClassType[] types = myMethod.getMethod().getThrowsList().getReferencedTypes();

    if (exceptions.length <= types.length) {
      return false;
    }

    for (int i = 0; i < types.length; i++) {
      if (exceptions[i].getOldIndex() != i) {
        return false;
      }
    }

    return true;
  }

  @Override
  @Nonnull
  protected List<Pair<String, JPanel>> createAdditionalPanels() {
    final PsiMethod method = myMethod.getMethod();

    // this method is invoked before constructor body
    myExceptionsModel = new ExceptionsTableModel(method.getThrowsList());
    myExceptionsModel.setTypeInfos(method);

    final JBTable table = new JBTable(myExceptionsModel);
    table.setStriped(true);
    table.setRowHeight(20);
    table.getColumnModel().getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject, JavaFileType.INSTANCE));
    final JavaCodeFragmentTableCellEditor cellEditor = new JavaCodeFragmentTableCellEditor(myProject);
    cellEditor.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        int row = table.getSelectedRow();
        int col = table.getSelectedColumn();
        myExceptionsModel.setValueAt(cellEditor.getCellEditorValue(), row, col);
        updateSignature();
      }
    });
    table.getColumnModel().getColumn(0).setCellEditor(cellEditor);
    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().setSelectionInterval(0, 0);
    table.setSurrendersFocusOnKeystroke(true);

    myPropExceptionsButton = new AnActionButton(
      RefactoringLocalize.changesignaturePropagateExceptionsTitle().get(),
      null,
      ImageEffects.layered(AllIcons.Nodes.ExceptionClass, AllIcons.Actions.New)
    ) {
      @Override
      @RequiredUIAccess
      public void actionPerformed(@Nonnull AnActionEvent e) {
        Ref<JavaCallerChooser> chooser = new Ref<>();
        Consumer<Set<PsiMethod>> callback = psiMethods -> {
          myMethodsToPropagateExceptions = psiMethods;
          myExceptionPropagationTree = chooser.get().getTree();
        };

        chooser.set(new JavaCallerChooser(
          method,
          myProject,
          RefactoringLocalize.changesignatureExceptionCallerChooser().get(),
          myExceptionPropagationTree,
          callback
        ));
        chooser.get().show();
      }
    };
    myPropExceptionsButton.setShortcut(CustomShortcutSet.fromString("alt X"));

    JPanel panel = ToolbarDecorator.createDecorator(table).addExtraAction(myPropExceptionsButton).createPanel();
    panel.setBorder(IdeBorderFactory.createEmptyBorder());

    myExceptionsModel.addTableModelListener(getSignatureUpdater());

    ArrayList<Pair<String, JPanel>> result = new ArrayList<>();
    LocalizeValue message = RefactoringLocalize.changesignatureExceptionsPanelBorderTitle();
    result.add(Pair.create(message.get(), panel));
    return result;
  }

  // need change access modifier - due it ill throw access error, from anonym classes
  @Override
  public void updateSignature() {
    super.updateSignature();
  }

  @Override
  protected LanguageFileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  protected JavaParameterTableModel createParametersInfoModel(JavaMethodDescriptor descriptor) {
    PsiParameterList parameterList = descriptor.getMethod().getParameterList();
    return new JavaParameterTableModel(parameterList, myDefaultValueContext, this);
  }

  @Override
  protected boolean isListTableViewSupported() {
    return true;
  }

  @Override
  protected boolean isEmptyRow(ParameterTableModelItemBase<ParameterInfoImpl> row) {
    return StringUtil.isEmpty(row.parameter.getName()) && StringUtil.isEmpty(row.parameter.getTypeText());
  }

  @Override
  @RequiredUIAccess
  protected JComponent getRowPresentation(ParameterTableModelItemBase<ParameterInfoImpl> item, boolean selected, boolean focused) {
    String typeText = item.typeCodeFragment.getText();
    String separator = StringUtil.repeatSymbol(' ', getTypesMaxLength() - typeText.length() + 1);
    String text = typeText + separator + item.parameter.getName();
    String defaultValue = item.defaultValueCodeFragment.getText();
    String tail = "";
    if (StringUtil.isNotEmpty(defaultValue)) {
      tail += " default value = " + defaultValue;
    }
    if (item.parameter.isUseAnySingleVariable()) {
      if (StringUtil.isNotEmpty(defaultValue)) {
        tail += ";";
      }
      tail += " Use any var.";
    }
    if (!StringUtil.isEmpty(tail)) {
      text += " //" + tail;
    }
    return JBListTableWitEditors.createEditorTextFieldPresentation(getProject(), getFileType(), " " + text, selected, focused);
  }

  @RequiredUIAccess
  private int getTypesMaxLength() {
    int len = 0;
    for (ParameterTableModelItemBase<ParameterInfoImpl> item : myParametersTableModel.getItems()) {
      String text = item.typeCodeFragment == null ? null : item.typeCodeFragment.getText();
      len = Math.max(len, text == null ? 0 : text.length());
    }
    return len;
  }

  private int getNamesMaxLength() {
    int len = 0;
    for (ParameterTableModelItemBase<ParameterInfoImpl> item : myParametersTableModel.getItems()) {
      String text = item.parameter.getName();
      len = Math.max(len, text == null ? 0 : text.length());
    }
    return len;
  }

  @RequiredUIAccess
  private int getColumnWidth(int index) {
    int letters = getTypesMaxLength() + (index == 0 ? 1 : getNamesMaxLength() + 2);
    Font font = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
    font = new Font(font.getFontName(), font.getStyle(), 12);
    return letters * Toolkit.getDefaultToolkit().getFontMetrics(font).stringWidth("W");
  }

  @RequiredUIAccess
  private int getTypesColumnWidth() {
    return getColumnWidth(0);
  }

  @RequiredUIAccess
  private int getNamesColumnWidth() {
    return getColumnWidth(1);
  }

  @Override
  protected JBTableRowEditor getTableEditor(final JTable t, final ParameterTableModelItemBase<ParameterInfoImpl> item) {
    return new JBTableRowEditor() {
      private EditorTextField myTypeEditor;
      private EditorTextField myNameEditor;
      private EditorTextField myDefaultValueEditor;
      private JCheckBox myAnyVar;

      @Override
      public void prepareEditor(JTable table, int row) {
        setLayout(new BorderLayout());
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(item.typeCodeFragment);
        myTypeEditor = new EditorTextField(document, getProject(), getFileType());
        myTypeEditor.addDocumentListener(getSignatureUpdater());
        myTypeEditor.setPreferredWidth(t.getWidth() / 2);
        myTypeEditor.addDocumentListener(new RowEditorChangeListener(0));
        add(createLabeledPanel("Type:", myTypeEditor), BorderLayout.WEST);

        myNameEditor = new EditorTextField(item.parameter.getName(), getProject(), getFileType());
        myNameEditor.addDocumentListener(getSignatureUpdater());
        myNameEditor.addDocumentListener(new RowEditorChangeListener(1));
        add(createLabeledPanel("Name:", myNameEditor), BorderLayout.CENTER);
        new TextFieldCompletionProvider() {

          @Override
          public void addCompletionVariants(@Nonnull String text, int offset, @Nonnull String prefix,
                                               @Nonnull CompletionResultSet result) {
            PsiCodeFragment fragment = item.typeCodeFragment;
            if (fragment instanceof PsiTypeCodeFragment typeCodeFragment) {
              PsiType type;
              try {
                type = typeCodeFragment.getType();
              } catch (Exception e) {
                return;
              }
              SuggestedNameInfo info = JavaCodeStyleManager.getInstance(myProject).suggestVariableName(VariableKind.PARAMETER,
                  null, null, type);

              for (String completionVariant : info.names) {
                LookupElementBuilder element = LookupElementBuilder.create(completionVariant);
                result.addElement(element.withLookupString(completionVariant.toLowerCase()));
              }
            }
          }
        }.apply(myNameEditor, item.parameter.getName());

        if (!item.isEllipsisType() && item.parameter.getOldIndex() == -1) {
          JPanel additionalPanel = new JPanel(new BorderLayout());
          Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(item.defaultValueCodeFragment);
          myDefaultValueEditor = new EditorTextField(doc, getProject(), getFileType());
          ((PsiExpressionCodeFragment) item.defaultValueCodeFragment).setExpectedType(getRowType(item));
          myDefaultValueEditor.setPreferredWidth(t.getWidth() / 2);
          myDefaultValueEditor.addDocumentListener(new RowEditorChangeListener(2));
          additionalPanel.add(createLabeledPanel("Default value:", myDefaultValueEditor), BorderLayout.WEST);

          if (!isGenerateDelegate()) {
            myAnyVar = new JCheckBox("&Use Any Var");
            UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myAnyVar);
            DialogUtil.registerMnemonic(myAnyVar, '&');
            myAnyVar.addActionListener(e -> item.parameter.setUseAnySingleVariable(myAnyVar.isSelected()));
            JPanel anyVarPanel = new JPanel(new BorderLayout());
            anyVarPanel.add(myAnyVar, BorderLayout.SOUTH);
            UIUtil.addInsets(anyVarPanel, JBUI.insetsBottom(8));
            additionalPanel.add(anyVarPanel, BorderLayout.CENTER);
            //additionalPanel.setPreferredSize(new Dimension(t.getWidth() / 3, -1));
          }
          add(additionalPanel, BorderLayout.SOUTH);
        }
      }

      @Override
      public JBTableRow getValue() {
        return column -> {
          switch (column) {
            case 0:
              return item.typeCodeFragment;
            case 1:
              return myNameEditor.getText().trim();
            case 2:
              return item.defaultValueCodeFragment;
            case 3:
              return myAnyVar != null && myAnyVar.isSelected();
          }
          return null;
        };
      }

      @Override
      @RequiredUIAccess
      public JComponent getPreferredFocusedComponent() {
        MouseEvent me = getMouseEvent();
        if (me == null) {
          return myTypeEditor.getFocusTarget();
        }
        double x = me.getPoint().getX();
        return x <= getTypesColumnWidth() ? myTypeEditor.getFocusTarget() : myDefaultValueEditor == null || x <= getNamesColumnWidth() ?
            myNameEditor.getFocusTarget() : myDefaultValueEditor.getFocusTarget();
      }

      @Override
      public JComponent[] getFocusableComponents() {
        List<JComponent> focusable = new ArrayList<>();
        focusable.add(myTypeEditor.getFocusTarget());
        focusable.add(myNameEditor.getFocusTarget());
        if (myDefaultValueEditor != null) {
          focusable.add(myDefaultValueEditor.getFocusTarget());
        }
        if (myAnyVar != null) {
          focusable.add(myAnyVar);
        }
        return focusable.toArray(new JComponent[focusable.size()]);
      }
    };
  }

  @Nullable
  private static PsiType getRowType(ParameterTableModelItemBase<ParameterInfoImpl> item) {
    try {
      return ((PsiTypeCodeFragment) item.typeCodeFragment).getType();
    } catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      return null;
    } catch (PsiTypeCodeFragment.NoTypeException e) {
      return null;
    }
  }

  @Override
  protected void customizeParametersTable(TableView<ParameterTableModelItemBase<ParameterInfoImpl>> table) {
    final JTable t = table.getComponent();
    final TableColumn defaultValue = t.getColumnModel().getColumn(2);
    final TableColumn varArg = t.getColumnModel().getColumn(3);
    t.removeColumn(defaultValue);
    t.removeColumn(varArg);
    t.getModel().addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        if (e.getType() == TableModelEvent.INSERT) {
          t.getModel().removeTableModelListener(this);
          TableColumnAnimator animator = new TableColumnAnimator(t);
          animator.setStep(48);
          animator.addColumn(defaultValue, (t.getWidth() - 48) / 3);
          animator.addColumn(varArg, 48);
          animator.startAndDoWhenDone(() -> t.editCellAt(t.getRowCount() - 1, 0));
          animator.start();
        }
      }
    });
  }

  @Override
  @RequiredUIAccess
  protected void invokeRefactoring(BaseRefactoringProcessor processor) {
    if (myMethodsToPropagateExceptions != null && !mayPropagateExceptions()) {
      Messages.showWarningDialog(myProject, RefactoringLocalize.changesignatureExceptionsWontPropagate().get(), REFACTORING_NAME.get());
      myMethodsToPropagateExceptions = null;
    }
    super.invokeRefactoring(processor);
  }

  @Override
  protected BaseRefactoringProcessor createRefactoringProcessor() {
    List<ParameterInfoImpl> parameters = getParameters();
    return new ChangeSignatureProcessor(myProject, myMethod.getMethod(), isGenerateDelegate(), getVisibility(), getMethodName(),
        getReturnType(), parameters.toArray(new ParameterInfoImpl[parameters.size()]), getExceptions(), myMethodsToPropagateParameters,
        myMethodsToPropagateExceptions);
  }

  @Nullable
  protected CanonicalTypes.Type getReturnType() {
    if (myReturnTypeField != null) {
      try {
        PsiType type = ((PsiTypeCodeFragment) myReturnTypeCodeFragment).getType();
        return CanonicalTypes.createTypeWrapper(type);
      } catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        return null;
      } catch (PsiTypeCodeFragment.NoTypeException e) {
        return null;
      }
    }

    return null;
  }

  protected ThrownExceptionInfo[] getExceptions() {
    return myExceptionsModel.getThrownExceptions();
  }

  @Override
  protected PsiCodeFragment createReturnTypeCodeFragment() {
    String returnTypeText = StringUtil.notNullize(myMethod.getReturnTypeText());
    JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
    return factory.createTypeCodeFragment(returnTypeText, myMethod.getMethod(), true, JavaCodeFragmentFactory.ALLOW_VOID);
  }

  @Override
  protected CallerChooserBase<PsiMethod> createCallerChooser(String title, Tree treeToReuse, Consumer<Set<PsiMethod>> callback) {
    return new JavaCallerChooser(myMethod.getMethod(), myProject, title, treeToReuse, callback);
  }

  @Override
  @RequiredUIAccess
  protected String validateAndCommitData() {
    PsiManager manager = PsiManager.getInstance(myProject);
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    String name = getMethodName();
    if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(name)) {
      return RefactoringMessageUtil.getIncorrectIdentifierMessage(name);
    }

    if (myMethod.canChangeReturnType() == MethodDescriptor.ReadWriteOption.ReadWrite) {
      try {
        ((PsiTypeCodeFragment) myReturnTypeCodeFragment).getType();
      } catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        myReturnTypeField.requestFocus();
        return RefactoringLocalize.changesignatureWrongReturnType(myReturnTypeCodeFragment.getText()).get();
      } catch (PsiTypeCodeFragment.NoTypeException e) {
        myReturnTypeField.requestFocus();
        return RefactoringLocalize.changesignatureNoReturnType().get();
      }
    }

    List<ParameterTableModelItemBase<ParameterInfoImpl>> parameterInfos = myParametersTableModel.getItems();
    int newParametersNumber = parameterInfos.size();

    for (int i = 0; i < newParametersNumber; i++) {
      ParameterTableModelItemBase<ParameterInfoImpl> item = parameterInfos.get(i);

      if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(item.parameter.getName())) {
        return RefactoringMessageUtil.getIncorrectIdentifierMessage(item.parameter.getName());
      }

      PsiType type;
      try {
        type = ((PsiTypeCodeFragment) parameterInfos.get(i).typeCodeFragment).getType();
      } catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        return RefactoringLocalize.changesignatureWrongTypeForParameter(item.typeCodeFragment.getText(), item.parameter.getName()).get();
      } catch (PsiTypeCodeFragment.NoTypeException e) {
        return RefactoringLocalize.changesignatureNoTypeForParameter(item.parameter.getName()).get();
      }

      item.parameter.setType(type);

      if (type instanceof PsiEllipsisType && i != newParametersNumber - 1) {
        return RefactoringLocalize.changesignatureVarargNotLast().get();
      }

      if (item.parameter.oldParameterIndex < 0) {
        item.parameter.defaultValue = WriteCommandAction.runWriteCommandAction(myProject,
          (Computable<String>)() -> JavaCodeStyleManager.getInstance(myProject)
            .qualifyClassReferences(item.defaultValueCodeFragment).getText()
        );
        String def = item.parameter.defaultValue;
        def = def.trim();
        if (!(type instanceof PsiEllipsisType)) {
          try {
            if (!StringUtil.isEmpty(def)) {
              factory.createExpressionFromText(def, null);
            }
          } catch (IncorrectOperationException e) {
            return e.getMessage();
          }
        }
      }
    }

    ThrownExceptionInfo[] exceptionInfos = myExceptionsModel.getThrownExceptions();
    PsiTypeCodeFragment[] typeCodeFragments = myExceptionsModel.getTypeCodeFragments();
    for (int i = 0; i < exceptionInfos.length; i++) {
      ThrownExceptionInfo exceptionInfo = exceptionInfos[i];
      PsiTypeCodeFragment typeCodeFragment = typeCodeFragments[i];
      try {
        PsiType type = typeCodeFragment.getType();
        if (!(type instanceof PsiClassType)) {
          return RefactoringLocalize.changesignatureWrongTypeForException(typeCodeFragment.getText()).get();
        }

        PsiClassType throwable = JavaPsiFacade.getInstance(myProject).getElementFactory()
          .createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, type.getResolveScope());
        if (!throwable.isAssignableFrom(type)) {
          return RefactoringLocalize.changesignatureNotThrowableType(typeCodeFragment.getText()).get();
        }
        exceptionInfo.setType((PsiClassType) type);
      } catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        return RefactoringLocalize.changesignatureWrongTypeForException(typeCodeFragment.getText()).get();
      } catch (PsiTypeCodeFragment.NoTypeException e) {
        return RefactoringLocalize.changesignatureNoTypeForException().get();
      }
    }

    // warnings
    try {
      if (myMethod.canChangeReturnType() == MethodDescriptor.ReadWriteOption.ReadWrite) {
        if (!RefactoringUtil.isResolvableType(((PsiTypeCodeFragment) myReturnTypeCodeFragment).getType())) {
          if (Messages.showOkCancelDialog(
            myProject,
            RefactoringLocalize.changesignatureCannotResolveReturnType(myReturnTypeCodeFragment.getText()).get(),
            RefactoringLocalize.changesignatureRefactoringName().get(),
            UIUtil.getWarningIcon()
          ) != 0) {
            return EXIT_SILENTLY;
          }
        }
      }
      for (ParameterTableModelItemBase<ParameterInfoImpl> item : parameterInfos) {
        if (!RefactoringUtil.isResolvableType(((PsiTypeCodeFragment) item.typeCodeFragment).getType())) {
          if (Messages.showOkCancelDialog(
            myProject,
            RefactoringLocalize.changesignatureCannotResolveParameterType(item.typeCodeFragment.getText(), item.parameter.getName()).get(),
            RefactoringLocalize.changesignatureRefactoringName().get(),
            UIUtil.getWarningIcon()
          ) != 0) {
            return EXIT_SILENTLY;
          }
        }
      }
    } catch (PsiTypeCodeFragment.IncorrectTypeException ignored) {
    }
    return null;
  }

  @Override
  @RequiredUIAccess
  protected ValidationInfo doValidate() {
    if (!getTableComponent().isEditing()) {
      for (ParameterTableModelItemBase<ParameterInfoImpl> item : myParametersTableModel.getItems()) {
        if (item.parameter.oldParameterIndex < 0) {
          if (StringUtil.isEmpty(item.defaultValueCodeFragment.getText())) {
            return new ValidationInfo("Default value is missing. Method calls will contain blanks instead of the new parameter value.");
          }
        }
      }
    }
    return super.doValidate();
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @Override
  @RequiredUIAccess
  protected String calculateSignature() {
    return doCalculateSignature(myMethod.getMethod());
  }

  @RequiredUIAccess
  protected String doCalculateSignature(PsiMethod method) {
    StringBuilder buffer = new StringBuilder();
    PsiModifierList modifierList = method.getModifierList();
    String modifiers = modifierList.getText();
    String oldModifier = VisibilityUtil.getVisibilityModifier(modifierList);
    String newModifier = getVisibility();
    String newModifierStr = VisibilityUtil.getVisibilityString(newModifier);
    if (!Comparing.equal(newModifier, oldModifier)) {
      int index = modifiers.indexOf(oldModifier);
      if (index >= 0) {
        StringBuilder buf = new StringBuilder(modifiers);
        buf.replace(index, index + oldModifier.length() + (StringUtil.isEmpty(newModifierStr) ? 1 : 0), newModifierStr);
        modifiers = buf.toString();
      } else {
        if (!StringUtil.isEmpty(newModifierStr)) {
          newModifierStr += " ";
        }
        modifiers = newModifierStr + modifiers;
      }
    }

    buffer.append(modifiers);
    if (modifiers.length() > 0 &&
        !StringUtil.endsWithChar(modifiers, '\n') &&
        !StringUtil.endsWithChar(modifiers, '\r') &&
        !StringUtil.endsWithChar(modifiers, ' ')) {
      buffer.append(" ");
    }

    if (!method.isConstructor()) {
      CanonicalTypes.Type type = getReturnType();
      if (type != null) {
        buffer.append(type.getTypeText());
      }
      buffer.append(" ");
    }
    buffer.append(getMethodName());
    buffer.append("(");

    int lineBreakIdx = buffer.lastIndexOf("\n");
    String indent = StringUtil.repeatSymbol(' ', lineBreakIdx >= 0 ? buffer.length() - lineBreakIdx - 1 : buffer.length());
    List<ParameterTableModelItemBase<ParameterInfoImpl>> items = myParametersTableModel.getItems();
    int curIndent = indent.length();
    for (int i = 0; i < items.size(); i++) {
      ParameterTableModelItemBase<ParameterInfoImpl> item = items.get(i);
      if (i > 0) {
        buffer.append(",");
        buffer.append("\n");
        buffer.append(indent);
      }
      String text = item.typeCodeFragment.getText();
      buffer.append(text).append(" ");
      String name = item.parameter.getName();
      buffer.append(name);
      curIndent = indent.length() + text.length() + 1 + name.length();
    }
    //if (!items.isEmpty()) {
    //  buffer.append("\n");
    //}
    buffer.append(")");
    PsiTypeCodeFragment[] thrownExceptionsFragments = myExceptionsModel.getTypeCodeFragments();
    if (thrownExceptionsFragments.length > 0) {
      //buffer.append("\n");
      buffer.append(" throws ");
      curIndent += 9; // ") throws ".length()
      indent = StringUtil.repeatSymbol(' ', curIndent);
      for (int i = 0; i < thrownExceptionsFragments.length; i++) {
        String text = thrownExceptionsFragments[i].getText();
        if (i != 0) {
          buffer.append(indent);
        }
        buffer.append(text);
        if (i < thrownExceptionsFragments.length - 1) {
          buffer.append(",");
        }
        buffer.append("\n");
      }
    }

    return buffer.toString();
  }
}
