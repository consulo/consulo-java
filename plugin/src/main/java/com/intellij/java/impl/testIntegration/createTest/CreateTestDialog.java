/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.testIntegration.createTest;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.java.impl.refactoring.ui.MemberSelectionTable;
import com.intellij.java.impl.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.java.impl.refactoring.util.RefactoringMessageUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.testIntegration.TestIntegrationUtils;
import com.intellij.java.impl.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.java.impl.codeInsight.PackageUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.testIntegration.TestFramework;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.AllIcons;
import consulo.application.HelpManager;
import consulo.application.ReadAction;
import consulo.application.Result;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ContentFolder;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.RecentsManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.awt.*;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;

public class CreateTestDialog extends DialogWrapper {
  private static final String RECENTS_KEY = "CreateTestDialog.RecentsKey";
  private static final String RECENT_SUPERS_KEY = "CreateTestDialog.Recents.Supers";
  private static final String DEFAULT_LIBRARY_NAME_PROPERTY = CreateTestDialog.class.getName() + ".defaultLibrary";
  private static final String SHOW_INHERITED_MEMBERS_PROPERTY = CreateTestDialog.class.getName() + ".includeInheritedMembers";

  private final Project myProject;
  private final PsiClass myTargetClass;
  private final Module myTargetModule;

  private PsiDirectory myTargetDirectory;
  private TestFramework mySelectedFramework;

  private final List<JRadioButton> myLibraryButtons = new ArrayList<>();
  private EditorTextField myTargetClassNameField;
  private ReferenceEditorComboWithBrowseButton mySuperClassField;
  private ReferenceEditorComboWithBrowseButton myTargetPackageField;
  private JCheckBox myGenerateBeforeBox;
  private JCheckBox myGenerateAfterBox;
  private JCheckBox myShowInheritedMethodsBox;
  private MemberSelectionTable myMethodsTable;
  private JButton myFixLibraryButton;
  private JPanel myFixLibraryPanel;
  private JLabel myFixLibraryLabel;

  private JRadioButton myDefaultLibraryButton;

  @RequiredUIAccess
  public CreateTestDialog(
    @Nonnull Project project,
    @Nonnull String title,
    PsiClass targetClass,
    PsiJavaPackage targetPackage,
    Module targetModule
  ) {
    super(project, true);
    myProject = project;

    myTargetClass = targetClass;
    myTargetModule = targetModule;

    initControls(targetClass, targetPackage);
    setTitle(title);
    init();

    myDefaultLibraryButton.doClick();
  }

  @RequiredUIAccess
  private void initControls(PsiClass targetClass, PsiJavaPackage targetPackage) {
    ButtonGroup group = new ButtonGroup();

    Map<String, JRadioButton> nameToButtonMap = new HashMap<>();
    List<Pair<String, JRadioButton>> attachedLibraries = new ArrayList<>();

    for (final TestFramework descriptor : TestFramework.EXTENSION_NAME.getExtensionList()) {
      final JRadioButton b = new JRadioButton(descriptor.getName());
      myLibraryButtons.add(b);
      group.add(b);

      nameToButtonMap.put(descriptor.getName(), b);
      if (descriptor.isLibraryAttached(myTargetModule)) {
        attachedLibraries.add(Pair.create(descriptor.getName(), b));
      }

      b.addActionListener(e -> {
        if (b.isSelected()) {
          onLibrarySelected(descriptor);
        }
      });
    }

    String defaultLibrary = getDefaultLibraryName();
    if (attachedLibraries.isEmpty()) {
      if (defaultLibrary != null) {
        myDefaultLibraryButton = nameToButtonMap.get(defaultLibrary);
      }
    } else {
      if (defaultLibrary != null) {
        for (Pair<String, JRadioButton> each : attachedLibraries) {
          if (each.first.equals(defaultLibrary)) {
            myDefaultLibraryButton = each.second;
          }
        }
      }
      if (myDefaultLibraryButton == null) {
        myDefaultLibraryButton = attachedLibraries.get(0).second;
      }
    }
    if (myDefaultLibraryButton == null) {
      myDefaultLibraryButton = myLibraryButtons.get(0);
    }

    myFixLibraryButton = new JButton(CodeInsightLocalize.intentionCreateTestDialogFixLibrary().get());
    myFixLibraryButton.addActionListener(e -> {
      myProject.getApplication()
        .runWriteAction(() -> OrderEntryFix.addJarToRoots(mySelectedFramework.getLibraryPath(), myTargetModule, null));
      myFixLibraryPanel.setVisible(false);
    });

    myTargetClassNameField = new EditorTextField(targetClass.getName() + "Test");
    myTargetClassNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        getOKAction().setEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(getClassName()));
      }
    });

    mySuperClassField = new ReferenceEditorComboWithBrowseButton(new MyChooseSuperClassAction(), null, myProject, true,
        JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE, RECENT_SUPERS_KEY);
    mySuperClassField.setMinimumSize(mySuperClassField.getPreferredSize());

    String targetPackageName = targetPackage != null ? targetPackage.getQualifiedName() : "";
    myTargetPackageField = new PackageNameReferenceEditorCombo(
      targetPackageName,
      myProject,
      RECENTS_KEY,
      CodeInsightLocalize.dialogCreateClassPackageChooserTitle().get()
    );

    new AnAction() {
      public void actionPerformed(@Nonnull AnActionEvent e) {
        myTargetPackageField.getButton().doClick();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)),
        myTargetPackageField.getChildComponent());

    myGenerateBeforeBox = new JCheckBox("setUp/@Before");
    myGenerateAfterBox = new JCheckBox("tearDown/@After");

    myShowInheritedMethodsBox = new JCheckBox(CodeInsightLocalize.intentionCreateTestDialogShowInherited().get());
    myShowInheritedMethodsBox.addActionListener(e -> updateMethodsTable());
    restoreShowInheritedMembersStatus();
    myMethodsTable = new MemberSelectionTable(Collections.<MemberInfo>emptyList(), null);
    updateMethodsTable();
  }

  private void onLibrarySelected(TestFramework descriptor) {
    LocalizeValue text = CodeInsightLocalize.intentionCreateTestDialogLibraryNotFound(descriptor.getName());
    myFixLibraryLabel.setText(text.get());
    myFixLibraryPanel.setVisible(!descriptor.isLibraryAttached(myTargetModule));

    String superClass = descriptor.getDefaultSuperClass();
    mySuperClassField.appendItem(superClass == null ? "" : superClass);
    mySelectedFramework = descriptor;
  }

  private void updateMethodsTable() {
    List<MemberInfo> methods = TestIntegrationUtils.extractClassMethods(
        myTargetClass, myShowInheritedMethodsBox.isSelected());

    Set<PsiMember> selectedMethods = new HashSet<>();
    for (MemberInfo each : myMethodsTable.getSelectedMemberInfos()) {
      selectedMethods.add(each.getMember());
    }
    for (MemberInfo each : methods) {
      each.setChecked(selectedMethods.contains(each.getMember()));
    }

    myMethodsTable.setMemberInfos(methods);
  }

  private String getDefaultLibraryName() {
    return getProperties().getValue(DEFAULT_LIBRARY_NAME_PROPERTY);
  }

  private void saveDefaultLibraryName() {
    getProperties().setValue(DEFAULT_LIBRARY_NAME_PROPERTY, mySelectedFramework.getName());
  }

  private void restoreShowInheritedMembersStatus() {
    String v = getProperties().getValue(SHOW_INHERITED_MEMBERS_PROPERTY);
    myShowInheritedMethodsBox.setSelected(v != null && v.equals("true"));
  }

  private void saveShowInheritedMembersStatus() {
    boolean v = myShowInheritedMethodsBox.isSelected();
    getProperties().setValue(SHOW_INHERITED_MEMBERS_PROPERTY, Boolean.toString(v));
  }

  private PropertiesComponent getProperties() {
    return PropertiesComponent.getInstance(myProject);
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Nonnull
  protected Action[] createActions() {
    return new Action[]{
        getOKAction(),
        getCancelAction(),
        getHelpAction()
    };
  }

  public JComponent getPreferredFocusedComponent() {
    return myTargetClassNameField;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints constr = new GridBagConstraints();

    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;

    JPanel librariesPanel = new JPanel();
    BoxLayout l = new BoxLayout(librariesPanel, BoxLayout.X_AXIS);
    librariesPanel.setLayout(l);

    for (JRadioButton b : myLibraryButtons) {
      librariesPanel.add(b);
    }


    int gridy = 1;

    constr.insets = insets(4);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightLocalize.intentionCreateTestDialogTestingLibrary().get()), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    constr.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(librariesPanel, constr);

    myFixLibraryPanel = new JPanel(new BorderLayout());
    myFixLibraryLabel = new JBLabel(AllIcons.Actions.IntentionBulb);
    myFixLibraryPanel.add(myFixLibraryLabel, BorderLayout.CENTER);
    myFixLibraryPanel.add(myFixLibraryButton, BorderLayout.EAST);

    constr.insets = insets(1);
    constr.gridy = gridy++;
    constr.gridx = 0;
    panel.add(myFixLibraryPanel, constr);

    constr.gridheight = 1;

    constr.insets = insets(6);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    constr.gridwidth = 1;
    panel.add(new JLabel(CodeInsightLocalize.intentionCreateTestDialogClassName().get()), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    panel.add(myTargetClassNameField, constr);

    constr.insets = insets(1);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightLocalize.intentionCreateTestDialogSuperClass().get()), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    panel.add(mySuperClassField, constr);

    constr.insets = insets(1);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightLocalize.dialogCreateClassDestinationPackageLabel().get()), constr);

    constr.gridx = 1;
    constr.weightx = 1;

    JPanel targetPackagePanel = new JPanel(new BorderLayout());
    targetPackagePanel.add(myTargetPackageField, BorderLayout.CENTER);
    panel.add(targetPackagePanel, constr);

    constr.insets = insets(6);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightLocalize.intentionCreateTestDialogGenerate().get()), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    panel.add(myGenerateBeforeBox, constr);

    constr.insets = insets(1);
    constr.gridy = gridy++;
    panel.add(myGenerateAfterBox, constr);

    constr.insets = insets(6);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightLocalize.intentionCreateTestDialogSelectMethods().get()), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    panel.add(myShowInheritedMethodsBox, constr);

    constr.insets = insets(1, 8);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.gridwidth = GridBagConstraints.REMAINDER;
    constr.fill = GridBagConstraints.BOTH;
    constr.weighty = 1;
    panel.add(ScrollPaneFactory.createScrollPane(myMethodsTable), constr);

    return panel;
  }

  private static Insets insets(int top) {
    return insets(top, 0);
  }

  private static Insets insets(int top, int bottom) {
    return JBUI.insets(top, 8, bottom, 8);
  }

  public String getClassName() {
    return myTargetClassNameField.getText();
  }

  @Nullable
  public String getSuperClassName() {
    String result = mySuperClassField.getText().trim();
    if (result.length() == 0) {
      return null;
    }
    return result;
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  public Collection<MemberInfo> getSelectedMethods() {
    return myMethodsTable.getSelectedMemberInfos();
  }

  public boolean shouldGeneratedAfter() {
    return myGenerateAfterBox.isSelected();
  }

  public boolean shouldGeneratedBefore() {
    return myGenerateBeforeBox.isSelected();
  }

  public TestFramework getSelectedTestFrameworkDescriptor() {
    return mySelectedFramework;
  }

  protected void doOKAction() {
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, myTargetPackageField.getText());
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENT_SUPERS_KEY, mySuperClassField.getText());

    String errorMessage;
    try {
      myTargetDirectory = selectTargetDirectory();
      if (myTargetDirectory == null) {
        return;
      }
      errorMessage = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, getClassName());
    } catch (IncorrectOperationException e) {
      errorMessage = e.getMessage();
    }

    if (errorMessage != null) {
      Messages.showMessageDialog(myProject, errorMessage, CommonLocalize.titleError().get(), UIUtil.getErrorIcon());
    }

    saveDefaultLibraryName();
    saveShowInheritedMembersStatus();
    super.doOKAction();
  }

  @Nullable
  private PsiDirectory selectTargetDirectory() throws IncorrectOperationException {
    final String packageName = getPackageName();
    final PackageWrapper targetPackage = new PackageWrapper(PsiManager.getInstance(myProject), packageName);

    final VirtualFile selectedRoot = ReadAction.compute(() -> {
      final HashSet<VirtualFile> testFolders = new HashSet<>();
      CreateTestAction.checkForTestRoots(myTargetModule, testFolders);
      VirtualFile[] roots;
      if (testFolders.isEmpty()) {
        roots = ModuleRootManager.getInstance(myTargetModule).getSourceRoots();
        if (roots.length == 0) {
          return null;
        }
      } else {
        roots = testFolders.toArray(new VirtualFile[testFolders.size()]);
      }

      if (roots.length == 1) {
        return roots[0];
      } else {
        PsiDirectory defaultDir = chooseDefaultDirectory(packageName);
        return MoveClassesOrPackagesUtil.chooseSourceRoot(targetPackage, roots, defaultDir);
      }
    });

    if (selectedRoot == null) {
      return null;
    }

    return new WriteCommandAction<PsiDirectory>(myProject, CodeInsightLocalize.createDirectoryCommand().get()) {
      protected void run(Result<PsiDirectory> result) throws Throwable {
        result.setResult(RefactoringUtil.createPackageDirectoryInSourceRoot(targetPackage, selectedRoot));
      }
    }.execute().getResultObject();
  }

  @Nullable
  @RequiredReadAction
  private PsiDirectory chooseDefaultDirectory(String packageName) {
    List<PsiDirectory> dirs = new ArrayList<>();
    for (ContentEntry e : ModuleRootManager.getInstance(myTargetModule).getContentEntries()) {
      for (ContentFolder f : e.getFolders(LanguageContentFolderScopes.of(TestContentFolderTypeProvider.getInstance()))) {
        final VirtualFile file = f.getFile();
        if (file != null) {
          final PsiDirectory dir = PsiManager.getInstance(myProject).findDirectory(file);
          if (dir != null) {
            dirs.add(dir);
          }
        }
      }
    }
    if (!dirs.isEmpty()) {
      for (PsiDirectory dir : dirs) {
        final String dirName = dir.getVirtualFile().getPath();
        if (dirName.contains("generated")) {
          continue;
        }
        return dir;
      }
      return dirs.get(0);
    }
    return PackageUtil.findPossiblePackageDirectoryInModule(myTargetModule, packageName);
  }

  private String getPackageName() {
    String name = myTargetPackageField.getText();
    return name != null ? name.trim() : "";
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.dialogs.createTest");
  }

  private class MyChooseSuperClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooserFactory f = TreeClassChooserFactory.getInstance(myProject);
      TreeClassChooser dialog =
          f.createAllProjectScopeChooser(CodeInsightLocalize.intentionCreateTestDialogChooseSuperClass().get());
      dialog.showDialog();
      PsiClass aClass = dialog.getSelected();
      if (aClass != null) {
        mySuperClassField.setText(aClass.getQualifiedName());
      }
    }
  }
}
