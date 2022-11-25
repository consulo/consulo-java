/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.impl.codeEditor.JavaEditorFileSwapper;
import com.intellij.java.impl.codeInsight.AttachSourcesProvider;
import com.intellij.java.impl.openapi.roots.ui.configuration.PathUIUtils;
import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.psi.impl.compiled.ClsParsingUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.library.Library;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.IdeaFileChooser;
import consulo.fileEditor.EditorNotificationProvider;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ide.impl.idea.ui.EditorNotificationPanel;
import consulo.java.impl.JavaBundle;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.navigation.OpenFileDescriptor;
import consulo.project.Project;
import consulo.project.ProjectBundle;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.internal.GuiUtils;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListSeparator;
import consulo.ui.ex.popup.PopupStep;
import consulo.util.collection.ContainerUtil;
import consulo.util.concurrent.ActionCallback;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFilePathUtil;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class AttachSourcesNotificationProvider implements EditorNotificationProvider<EditorNotificationPanel> {
  private final Project myProject;

  @Inject
  public AttachSourcesNotificationProvider(Project project) {
    myProject = project;
  }

  @RequiredUIAccess
  @Override
  public EditorNotificationPanel createNotificationPanel(@Nonnull final VirtualFile file, @Nonnull FileEditor fileEditor) {
    if (file.getFileType() != JavaClassFileType.INSTANCE) {
      return null;
    }

    final EditorNotificationPanel panel = new EditorNotificationPanel();

    String text = JavaBundle.message("class.file.decompiled.text");
    String classInfo = getClassFileInfo(file);
    if (classInfo != null) {
      text += ", " + classInfo;
    }
    panel.setText(text);

    final VirtualFile sourceFile = JavaEditorFileSwapper.findSourceFile(myProject, file);
    if (sourceFile == null) {
      final List<LibraryOrderEntry> libraries = findLibraryEntriesForFile(file);
      if (libraries != null) {
        List<AttachSourcesProvider.AttachSourcesAction> actions = new ArrayList<>();

        PsiFile clsFile = PsiManager.getInstance(myProject).findFile(file);
        boolean hasNonLightAction = false;
        for (AttachSourcesProvider each : AttachSourcesProvider.EP_NAME.getExtensionList()) {
          for (AttachSourcesProvider.AttachSourcesAction action : each.getActions(libraries, clsFile)) {
            if (hasNonLightAction) {
              if (action instanceof AttachSourcesProvider.LightAttachSourcesAction) {
                continue; // Don't add LightAttachSourcesAction if non light action exists.
              }
            } else {
              if (!(action instanceof AttachSourcesProvider.LightAttachSourcesAction)) {
                actions.clear(); // All previous actions is LightAttachSourcesAction and should be removed.
                hasNonLightAction = true;
              }
            }
            actions.add(action);
          }
        }

        Collections.sort(actions, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

        AttachSourcesProvider.AttachSourcesAction defaultAction;
        if (findSourceFileInSameJar(file) != null) {
          defaultAction = new AttachJarAsSourcesAction(file);
        } else {
          defaultAction = new ChooseAndAttachSourcesAction(myProject, panel);
        }
        actions.add(defaultAction);

        String originalText = text;

        for (final AttachSourcesProvider.AttachSourcesAction action : actions) {
          panel.createActionLabel(GuiUtils.getTextWithoutMnemonicEscaping(action.getName()), () -> {
            List<LibraryOrderEntry> entries = findLibraryEntriesForFile(file);
            if (!Comparing.equal(libraries, entries)) {
              Messages.showErrorDialog(myProject, "Can't find library for " + file.getName(), "Error");
              return;
            }

            panel.setText(action.getBusyText());

            action.perform(entries).doWhenProcessed(() -> panel.setText(originalText));
          });
        }
      }
    } else {
      panel.createActionLabel(JavaBundle.message("class.file.open.source.action"), () -> {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, sourceFile);
        FileEditorManager.getInstance(myProject).openTextEditor(descriptor, true);
      });
    }

    return panel;
  }

  @Nullable
  private static String getClassFileInfo(VirtualFile file) {
    try {
      byte[] data = file.contentsToByteArray(false);
      if (data.length > 8) {
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(data))) {
          if (stream.readInt() == 0xCAFEBABE) {
            int minor = stream.readUnsignedShort();
            int major = stream.readUnsignedShort();
            StringBuilder info = new StringBuilder().append("bytecode version: ").append(major).append('.').append(minor);
            JavaSdkVersion sdkVersion = ClsParsingUtil.getJdkVersionByBytecode(major);
            if (sdkVersion != null) {
              info.append(" (Java ").append(sdkVersion.getDescription()).append(')');
            }
            return info.toString();
          }
        }
      }
    } catch (IOException ignored) {
    }
    return null;
  }

  @Nullable
  private List<LibraryOrderEntry> findLibraryEntriesForFile(VirtualFile file) {
    List<LibraryOrderEntry> entries = null;

    ProjectFileIndex index = ProjectFileIndex.getInstance(myProject);
    for (OrderEntry entry : index.getOrderEntriesForFile(file)) {
      if (entry instanceof LibraryOrderEntry) {
        if (entries == null) {
          entries = new ArrayList<>();
        }
        entries.add((LibraryOrderEntry) entry);
      }
    }

    return entries;
  }

  @Nullable
  private static VirtualFile findSourceFileInSameJar(VirtualFile classFile) {
    String name = classFile.getName();
    int i = name.indexOf('$');
    if (i != -1) {
      name = name.substring(0, i);
    }
    i = name.indexOf('.');
    if (i != -1) {
      name = name.substring(0, i);
    }
    return classFile.getParent().findChild(name + JavaFileType.DOT_DEFAULT_EXTENSION);
  }

  private static class AttachJarAsSourcesAction implements AttachSourcesProvider.AttachSourcesAction {
    private final VirtualFile myClassFile;

    public AttachJarAsSourcesAction(VirtualFile classFile) {
      myClassFile = classFile;
    }

    @Override
    public String getName() {
      return ProjectBundle.message("module.libraries.attach.sources.button");
    }

    @Override
    public String getBusyText() {
      return ProjectBundle.message("library.attach.sources.action.busy.text");
    }

    @Override
    public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {
      final List<Library.ModifiableModel> modelsToCommit = new ArrayList<>();
      for (LibraryOrderEntry orderEntry : orderEntriesContainingFile) {
        final Library library = orderEntry.getLibrary();
        if (library == null) {
          continue;
        }
        final VirtualFile root = findRoot(library);
        if (root == null) {
          continue;
        }
        final Library.ModifiableModel model = library.getModifiableModel();
        model.addRoot(root, SourcesOrderRootType.getInstance());
        modelsToCommit.add(model);
      }
      if (modelsToCommit.isEmpty()) {
        return ActionCallback.REJECTED;
      }
      WriteAction.run(() -> {
        for (Library.ModifiableModel model : modelsToCommit) {
          model.commit();
        }
      });

      return ActionCallback.DONE;
    }

    @Nullable
    private VirtualFile findRoot(Library library) {
      for (VirtualFile classesRoot : library.getFiles(BinariesOrderRootType.getInstance())) {
        if (VfsUtilCore.isAncestor(classesRoot, myClassFile, true)) {
          return classesRoot;
        }
      }
      return null;
    }
  }

  private static class ChooseAndAttachSourcesAction implements AttachSourcesProvider.AttachSourcesAction {
    private final Project myProject;
    private final JComponent myParentComponent;

    public ChooseAndAttachSourcesAction(Project project, JComponent parentComponent) {
      myProject = project;
      myParentComponent = parentComponent;
    }

    @Override
    public String getName() {
      return ProjectBundle.message("module.libraries.attach.sources.button");
    }

    @Override
    public String getBusyText() {
      return ProjectBundle.message("library.attach.sources.action.busy.text");
    }

    @Override
    @RequiredUIAccess
    public ActionCallback perform(final List<LibraryOrderEntry> libraries) {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleJavaPathDescriptor();
      descriptor.setTitle(ProjectBundle.message("library.attach.sources.action"));
      descriptor.setDescription(ProjectBundle.message("library.attach.sources.description"));
      Library firstLibrary = libraries.get(0).getLibrary();
      VirtualFile[] roots = firstLibrary != null ? firstLibrary.getFiles(BinariesOrderRootType.getInstance()) : VirtualFile.EMPTY_ARRAY;
      VirtualFile[] candidates = IdeaFileChooser.chooseFiles(descriptor, myProject, roots.length == 0 ? null : VirtualFilePathUtil.getLocalFile(roots[0]));
      final VirtualFile[] files = PathUIUtils.scanAndSelectDetectedJavaSourceRoots(myParentComponent, candidates);
      if (files.length == 0) {
        return ActionCallback.REJECTED;
      }

      final Map<Library, LibraryOrderEntry> librariesToAppendSourcesTo = new HashMap<>();
      for (LibraryOrderEntry library : libraries) {
        librariesToAppendSourcesTo.put(library.getLibrary(), library);
      }
      if (librariesToAppendSourcesTo.size() == 1) {
        appendSources(firstLibrary, files);
      } else {
        librariesToAppendSourcesTo.put(null, null);
        String title = JavaBundle.message("library.choose.one.to.attach");
        List<LibraryOrderEntry> entries = ContainerUtil.newArrayList(librariesToAppendSourcesTo.values());
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<LibraryOrderEntry>(title, entries) {
          @Override
          public ListSeparator getSeparatorAbove(LibraryOrderEntry value) {
            return value == null ? new ListSeparator() : null;
          }

          @Nonnull
          @Override
          public String getTextFor(LibraryOrderEntry value) {
            return value == null ? "All" : value.getPresentableName() + " (" + value.getOwnerModule().getName() + ")";
          }

          @Override
          @RequiredUIAccess
          public PopupStep onChosen(LibraryOrderEntry libraryOrderEntry, boolean finalChoice) {
            if (libraryOrderEntry != null) {
              appendSources(libraryOrderEntry.getLibrary(), files);
            } else {
              for (Library libOrderEntry : librariesToAppendSourcesTo.keySet()) {
                if (libOrderEntry != null) {
                  appendSources(libOrderEntry, files);
                }
              }
            }
            return FINAL_CHOICE;
          }
        }).showCenteredInCurrentWindow(myProject);
      }

      return ActionCallback.DONE;
    }

    @RequiredUIAccess
    private static void appendSources(final Library library, final VirtualFile[] files) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          Library.ModifiableModel model = library.getModifiableModel();
          for (VirtualFile virtualFile : files) {
            model.addRoot(virtualFile, SourcesOrderRootType.getInstance());
          }
          model.commit();
        }
      });
    }
  }
}
