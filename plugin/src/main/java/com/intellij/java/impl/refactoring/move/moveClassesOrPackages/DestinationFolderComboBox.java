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
package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.PackageWrapper;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.ide.impl.idea.ide.util.DirectoryChooser;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.language.editor.ui.awt.EditorComboBox;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.function.Consumer;

/**
 * User: anna
 * Date: 9/13/11
 */
public abstract class DestinationFolderComboBox extends ComboboxWithBrowseButton {
  private static final String LEAVE_IN_SAME_SOURCE_ROOT = "Leave in same source root";
  private static final DirectoryChooser.ItemWrapper NULL_WRAPPER = new DirectoryChooser.ItemWrapper(null, null);
  private PsiDirectory myInitialTargetDirectory;
  private VirtualFile[] mySourceRoots;

  public DestinationFolderComboBox() {
    super(new ComboBoxWithWidePopup());
  }

  public abstract String getTargetPackage();

  protected boolean reportBaseInTestSelectionInSource() {
    return false;
  }

  protected boolean reportBaseInSourceSelectionInTest() {
    return false;
  }

  public void setData(final Project project,
                      final PsiDirectory initialTargetDirectory,
                      final EditorComboBox editorComboBox) {
    setData(project, initialTargetDirectory, new Consumer<String>() {
      @Override
      public void accept(String s) {
      }
    }, editorComboBox);
  }

  public void setData(final Project project,
                      final PsiDirectory initialTargetDirectory,
                      final Consumer<String> errorMessageUpdater, final EditorComboBox editorComboBox) {
    myInitialTargetDirectory = initialTargetDirectory;
    mySourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
    new ComboboxSpeedSearch(getComboBox()) {
      @Override
      protected String getElementText(Object element) {
        if (element == NULL_WRAPPER) return LEAVE_IN_SAME_SOURCE_ROOT;
        if (element instanceof DirectoryChooser.ItemWrapper) {
          final VirtualFile virtualFile = ((DirectoryChooser.ItemWrapper) element).getDirectory().getVirtualFile();
          final Module module = ModuleUtil.findModuleForFile(virtualFile, project);
          if (module != null) {
            return module.getName();
          }
        }
        return super.getElementText(element);
      }
    };
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    getComboBox().setRenderer(new ListCellRendererWrapper<DirectoryChooser.ItemWrapper>() {
      @Override
      public void customize(JList list,
                            DirectoryChooser.ItemWrapper itemWrapper,
                            int index,
                            boolean selected,
                            boolean hasFocus) {
        if (itemWrapper != NULL_WRAPPER && itemWrapper != null) {
          setIcon(TargetAWT.to(itemWrapper.getIcon(fileIndex)));

          setText(itemWrapper.getRelativeToProjectPath());
        } else {
          setText(LEAVE_IN_SAME_SOURCE_ROOT);
        }
      }
    });
    final VirtualFile initialSourceRoot =
        initialTargetDirectory != null ? fileIndex.getSourceRootForFile(initialTargetDirectory.getVirtualFile()) : null;
    final VirtualFile[] selection = new VirtualFile[]{initialSourceRoot};
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        VirtualFile root = MoveClassesOrPackagesUtil
            .chooseSourceRoot(new PackageWrapper(PsiManager.getInstance(project), getTargetPackage()), mySourceRoots, initialTargetDirectory);
        if (root == null) return;
        final ComboBoxModel model = getComboBox().getModel();
        for (int i = 0; i < model.getSize(); i++) {
          DirectoryChooser.ItemWrapper item = (DirectoryChooser.ItemWrapper) model.getElementAt(i);
          if (item != NULL_WRAPPER && Comparing.equal(fileIndex.getSourceRootForFile(item.getDirectory().getVirtualFile()), root)) {
            getComboBox().setSelectedItem(item);
            getComboBox().repaint();
            return;
          }
        }
        setComboboxModel(getComboBox(), root, root, fileIndex, mySourceRoots, project, true, errorMessageUpdater);
      }
    });

    editorComboBox.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        JComboBox comboBox = getComboBox();
        DirectoryChooser.ItemWrapper selectedItem = (DirectoryChooser.ItemWrapper) comboBox.getSelectedItem();
        setComboboxModel(comboBox, selectedItem != null && selectedItem != NULL_WRAPPER ? fileIndex.getSourceRootForFile(selectedItem.getDirectory().getVirtualFile()) : initialSourceRoot, selection[0], fileIndex, mySourceRoots, project, false, errorMessageUpdater);
      }
    });
    setComboboxModel(getComboBox(), initialSourceRoot, selection[0], fileIndex, mySourceRoots, project, false, errorMessageUpdater);
    getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object selectedItem = getComboBox().getSelectedItem();
        updateErrorMessage(errorMessageUpdater, fileIndex, selectedItem);
        if (selectedItem instanceof DirectoryChooser.ItemWrapper && selectedItem != NULL_WRAPPER) {
          PsiDirectory directory = ((DirectoryChooser.ItemWrapper) selectedItem).getDirectory();
          if (directory != null) {
            selection[0] = fileIndex.getSourceRootForFile(directory.getVirtualFile());
          }
        }
      }
    });
  }

  @Nullable
  public MoveDestination selectDirectory(final PackageWrapper targetPackage, final boolean showChooserWhenDefault) {
    final DirectoryChooser.ItemWrapper selectedItem = (DirectoryChooser.ItemWrapper) getComboBox().getSelectedItem();
    if (selectedItem == null || selectedItem == NULL_WRAPPER) {
      return new MultipleRootsMoveDestination(targetPackage);
    }
    final PsiDirectory selectedPsiDirectory = selectedItem.getDirectory();
    VirtualFile selectedDestination = selectedPsiDirectory.getVirtualFile();
    if (showChooserWhenDefault &&
        myInitialTargetDirectory != null && Comparing.equal(selectedDestination, myInitialTargetDirectory.getVirtualFile()) &&
        mySourceRoots.length > 1) {
      selectedDestination = MoveClassesOrPackagesUtil.chooseSourceRoot(targetPackage, mySourceRoots, myInitialTargetDirectory);
    }
    if (selectedDestination == null) return null;
    return new AutocreatingSingleSourceRootMoveDestination(targetPackage, selectedDestination);
  }

  private void updateErrorMessage(Consumer<String> updateErrorMessage, ProjectFileIndex fileIndex, Object selectedItem) {
    updateErrorMessage.accept(null);
    if (myInitialTargetDirectory != null && selectedItem instanceof DirectoryChooser.ItemWrapper && selectedItem != NULL_WRAPPER) {
      final PsiDirectory directory = ((DirectoryChooser.ItemWrapper) selectedItem).getDirectory();
      final boolean isSelectionInTestSourceContent = fileIndex.isInTestSourceContent(directory.getVirtualFile());
      final boolean inTestSourceContent = fileIndex.isInTestSourceContent(myInitialTargetDirectory.getVirtualFile());
      if (isSelectionInTestSourceContent != inTestSourceContent) {
        if (inTestSourceContent && reportBaseInTestSelectionInSource()) {
          updateErrorMessage.accept("Source root is selected while the test root is expected");
        }

        if (isSelectionInTestSourceContent && reportBaseInSourceSelectionInTest()) {
          updateErrorMessage.accept("Test root is selected while the source root is expected");
        }
      }
    }
  }

  private void setComboboxModel(final JComboBox comboBox,
                                final VirtualFile initialTargetDirectorySourceRoot,
                                final VirtualFile oldSelection,
                                final ProjectFileIndex fileIndex,
                                final VirtualFile[] sourceRoots,
                                final Project project,
                                final boolean forceIncludeAll,
                                final Consumer<String> updateErrorMessage) {
    final LinkedHashSet<PsiDirectory> targetDirectories = new LinkedHashSet<PsiDirectory>();
    final HashMap<PsiDirectory, String> pathsToCreate = new HashMap<PsiDirectory, String>();
    MoveClassesOrPackagesUtil
        .buildDirectoryList(new PackageWrapper(PsiManager.getInstance(project), getTargetPackage()), sourceRoots, targetDirectories, pathsToCreate);
    if (!forceIncludeAll && targetDirectories.size() > pathsToCreate.size()) {
      targetDirectories.removeAll(pathsToCreate.keySet());
    }
    final ArrayList<DirectoryChooser.ItemWrapper> items = new ArrayList<DirectoryChooser.ItemWrapper>();
    DirectoryChooser.ItemWrapper initial = null;
    DirectoryChooser.ItemWrapper oldOne = null;
    for (PsiDirectory targetDirectory : targetDirectories) {
      DirectoryChooser.ItemWrapper itemWrapper = new DirectoryChooser.ItemWrapper(targetDirectory, pathsToCreate.get(targetDirectory));
      items.add(itemWrapper);
      final VirtualFile sourceRootForFile = fileIndex.getSourceRootForFile(targetDirectory.getVirtualFile());
      if (Comparing.equal(sourceRootForFile, initialTargetDirectorySourceRoot)) {
        initial = itemWrapper;
      } else if (Comparing.equal(sourceRootForFile, oldSelection)) {
        oldOne = itemWrapper;
      }
    }
    items.add(NULL_WRAPPER);
    final DirectoryChooser.ItemWrapper selection = chooseSelection(initialTargetDirectorySourceRoot, fileIndex, items, initial, oldOne);
    final ComboBoxModel model = comboBox.getModel();
    if (model instanceof CollectionComboBoxModel) {
      boolean sameModel = model.getSize() == items.size();
      if (sameModel) {
        for (int i = 0; i < items.size(); i++) {
          final DirectoryChooser.ItemWrapper oldItem = (DirectoryChooser.ItemWrapper) model.getElementAt(i);
          final DirectoryChooser.ItemWrapper itemWrapper = items.get(i);
          if (!areItemsEquivalent(oldItem, itemWrapper)) {
            sameModel = false;
            break;
          }
        }
      }
      if (sameModel) {
        if (areItemsEquivalent((DirectoryChooser.ItemWrapper) comboBox.getSelectedItem(), selection)) {
          return;
        }
      }
    }
    updateErrorMessage(updateErrorMessage, fileIndex, selection);
    Collections.sort(items, new Comparator<DirectoryChooser.ItemWrapper>() {
      @Override
      public int compare(DirectoryChooser.ItemWrapper o1, DirectoryChooser.ItemWrapper o2) {
        if (o1 == NULL_WRAPPER) return -1;
        if (o2 == NULL_WRAPPER) return 1;
        return o1.getRelativeToProjectPath().compareToIgnoreCase(o2.getRelativeToProjectPath());
      }
    });
    comboBox.setModel(new CollectionComboBoxModel(items, selection));

    final Component root = SwingUtilities.getRoot(comboBox);
    if (root instanceof Window) {
      final Dimension preferredSize = root.getPreferredSize();
      if (preferredSize.getWidth() > root.getSize().getWidth()) {
        root.setSize(preferredSize);
      }
    }
  }

  @Nullable
  private static DirectoryChooser.ItemWrapper chooseSelection(final VirtualFile initialTargetDirectorySourceRoot,
                                                              final ProjectFileIndex fileIndex,
                                                              final ArrayList<DirectoryChooser.ItemWrapper> items,
                                                              final DirectoryChooser.ItemWrapper initial,
                                                              final DirectoryChooser.ItemWrapper oldOne) {
    if (initial != null || ((initialTargetDirectorySourceRoot == null || items.size() > 2) && items.contains(NULL_WRAPPER)) || items.isEmpty()) {
      return initial;
    } else {
      if (oldOne != null) {
        return oldOne;
      } else if (initialTargetDirectorySourceRoot != null) {
        final boolean inTest = fileIndex.isInTestSourceContent(initialTargetDirectorySourceRoot);
        for (DirectoryChooser.ItemWrapper item : items) {
          PsiDirectory directory = item.getDirectory();
          if (directory != null) {
            final VirtualFile virtualFile = directory.getVirtualFile();
            if (fileIndex.isInTestSourceContent(virtualFile) == inTest) {
              return item;
            }
          }
        }
      }
    }
    return items.get(0);
  }

  private static boolean areItemsEquivalent(DirectoryChooser.ItemWrapper oItem, DirectoryChooser.ItemWrapper itemWrapper) {
    if (oItem == NULL_WRAPPER || itemWrapper == NULL_WRAPPER) {
      if (oItem != itemWrapper) {
        return false;
      }
      return true;
    }
    if (oItem == null) return itemWrapper == null;
    if (itemWrapper == null) return false;
    if (oItem.getDirectory() != itemWrapper.getDirectory()) {
      return false;
    }
    return true;
  }

  public static boolean isAccessible(final Project project,
                                     final VirtualFile virtualFile,
                                     final VirtualFile targetVirtualFile) {
    final boolean inTestSourceContent = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(virtualFile);
    final Module module = ModuleUtil.findModuleForFile(virtualFile, project);
    if (targetVirtualFile != null &&
        module != null &&
        !GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, inTestSourceContent).contains(targetVirtualFile)) {
      return false;
    }
    return true;
  }
}
