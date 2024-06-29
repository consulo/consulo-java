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
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.WriteAction;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.library.Library;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.IdeaFileChooser;
import consulo.fileEditor.*;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.java.impl.JavaBundle;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.project.localize.ProjectLocalize;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.UIEvent;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListSeparator;
import consulo.ui.ex.popup.PopupStep;
import consulo.ui.ex.util.TextWithMnemonic;
import consulo.util.collection.ContainerUtil;
import consulo.util.concurrent.AsyncResult;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFilePathUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author Dmitry Avdeev
 */
@ExtensionImpl
public class AttachSourcesNotificationProvider implements EditorNotificationProvider
{
	private static final Key<AttachSourcesProvider.AttachSourcesAction> CURRENT_ACTION = Key.create(AttachSourcesProvider.AttachSourcesAction.class);

	private final Project myProject;

	@Inject
	public AttachSourcesNotificationProvider(Project project)
	{
		myProject = project;
	}

	@Nonnull
	@Override
	public String getId()
	{
		return "java-class-attach-source";
	}

	@Nullable
	@Override
	@RequiredReadAction
	public EditorNotificationBuilder buildNotification(
		@Nonnull VirtualFile file,
		@Nonnull FileEditor fileEditor,
		@Nonnull Supplier<EditorNotificationBuilder> supplier
	)
	{
		if (file.getFileType() != JavaClassFileType.INSTANCE)
		{
			return null;
		}

		EditorNotificationBuilder builder = supplier.get();

		AttachSourcesProvider.AttachSourcesAction currentAction = file.getUserData(CURRENT_ACTION);
		if (currentAction != null)
		{
			builder.withText(LocalizeValue.localizeTODO(currentAction.getBusyText()));
			return builder;
		}

		String text = JavaBundle.message("class.file.decompiled.text");
		String classInfo = getClassFileInfo(file);
		if (classInfo != null)
		{
			text += ", " + classInfo;
		}
		builder.withText(LocalizeValue.localizeTODO(text));

		final VirtualFile sourceFile = JavaEditorFileSwapper.findSourceFile(myProject, file);
		if (sourceFile == null)
		{
			final List<LibraryOrderEntry> libraries = findLibraryEntriesForFile(file);
			if (libraries != null)
			{
				List<AttachSourcesProvider.AttachSourcesAction> actions = new ArrayList<>();

				PsiFile clsFile = PsiManager.getInstance(myProject).findFile(file);
				boolean hasNonLightAction = false;
				for (AttachSourcesProvider each : myProject.getExtensionList(AttachSourcesProvider.class))
				{
					for (AttachSourcesProvider.AttachSourcesAction action : each.getActions(libraries, clsFile))
					{
						if (hasNonLightAction)
						{
							if (action instanceof AttachSourcesProvider.LightAttachSourcesAction)
							{
								continue; // Don't add LightAttachSourcesAction if non light action exists.
							}
						}
						else if (!(action instanceof AttachSourcesProvider.LightAttachSourcesAction)) {
							actions.clear(); // All previous actions is LightAttachSourcesAction and should be removed.
							hasNonLightAction = true;
						}
						actions.add(action);
					}
				}

				Collections.sort(actions, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

				AttachSourcesProvider.AttachSourcesAction defaultAction;
				defaultAction =
					findSourceFileInSameJar(file) != null ? new AttachJarAsSourcesAction(file) : new ChooseAndAttachSourcesAction(myProject);
				actions.add(defaultAction);

				for (final AttachSourcesProvider.AttachSourcesAction action : actions)
				{
					TextWithMnemonic textWithMnemonic = TextWithMnemonic.parse(action.getName());
					builder.withAction(LocalizeValue.localizeTODO(textWithMnemonic.getText()), (e) -> {
						List<LibraryOrderEntry> entries = findLibraryEntriesForFile(file);
						if (!Comparing.equal(libraries, entries))
						{
							Messages.showErrorDialog(myProject, "Can't find library for " + file.getName(), "Error");
							return;
						}

						file.putCopyableUserData(CURRENT_ACTION, action);

						EditorNotifications.getInstance(myProject).updateNotifications(file);

						action.perform(entries, e).doWhenProcessed(() ->
						{
							file.putUserData(CURRENT_ACTION, null);

							EditorNotifications.getInstance(myProject).updateNotifications(file);
						});
					});
				}
			}
		}
		else
		{
			builder.withAction(LocalizeValue.localizeTODO(JavaBundle.message("class.file.open.source.action")), (e) -> {
				OpenFileDescriptor descriptor = OpenFileDescriptorFactory.getInstance(myProject).builder(sourceFile).build();
				FileEditorManager.getInstance(myProject).openTextEditor(descriptor, true);
			});
		}

		return builder;
	}

	@Nullable
	private static String getClassFileInfo(VirtualFile file)
	{
		try
		{
			byte[] data = file.contentsToByteArray(false);
			if (data.length > 8)
			{
				try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(data)))
				{
					if (stream.readInt() == 0xCAFEBABE)
					{
						int minor = stream.readUnsignedShort();
						int major = stream.readUnsignedShort();
						StringBuilder info = new StringBuilder().append("bytecode version: ").append(major).append('.').append(minor);
						JavaSdkVersion sdkVersion = ClsParsingUtil.getJdkVersionByBytecode(major);
						if (sdkVersion != null)
						{
							info.append(" (Java ").append(sdkVersion.getDescription()).append(')');
						}
						return info.toString();
					}
				}
			}
		}
		catch (IOException ignored)
		{
		}
		return null;
	}

	@Nullable
	private List<LibraryOrderEntry> findLibraryEntriesForFile(VirtualFile file)
	{
		List<LibraryOrderEntry> entries = null;

		ProjectFileIndex index = ProjectFileIndex.getInstance(myProject);
		for (OrderEntry entry : index.getOrderEntriesForFile(file))
		{
			if (entry instanceof LibraryOrderEntry libraryOrderEntry)
			{
				if (entries == null)
				{
					entries = new ArrayList<>();
				}
				entries.add(libraryOrderEntry);
			}
		}

		return entries;
	}

	@Nullable
	private static VirtualFile findSourceFileInSameJar(VirtualFile classFile)
	{
		String name = classFile.getName();
		int i = name.indexOf('$');
		if (i != -1)
		{
			name = name.substring(0, i);
		}
		i = name.indexOf('.');
		if (i != -1)
		{
			name = name.substring(0, i);
		}
		return classFile.getParent().findChild(name + JavaFileType.DOT_DEFAULT_EXTENSION);
	}

	private static class AttachJarAsSourcesAction implements AttachSourcesProvider.AttachSourcesAction
	{
		private final VirtualFile myClassFile;

		public AttachJarAsSourcesAction(VirtualFile classFile)
		{
			myClassFile = classFile;
		}

		@Override
		public String getName()
		{
			return ProjectLocalize.moduleLibrariesAttachSourcesButton().get();
		}

		@Override
		public String getBusyText()
		{
			return ProjectLocalize.libraryAttachSourcesActionBusyText().get();
		}

		@Override
		public AsyncResult<Void> perform(List<LibraryOrderEntry> orderEntriesContainingFile, @Nonnull UIEvent<Component> e)
		{
			final List<Library.ModifiableModel> modelsToCommit = new ArrayList<>();
			for (LibraryOrderEntry orderEntry : orderEntriesContainingFile)
			{
				final Library library = orderEntry.getLibrary();
				if (library == null)
				{
					continue;
				}
				final VirtualFile root = findRoot(library);
				if (root == null)
				{
					continue;
				}
				final Library.ModifiableModel model = library.getModifiableModel();
				model.addRoot(root, SourcesOrderRootType.getInstance());
				modelsToCommit.add(model);
			}
			if (modelsToCommit.isEmpty())
			{
				return AsyncResult.rejected();
			}
			WriteAction.run(() -> {
				for (Library.ModifiableModel model : modelsToCommit)
				{
					model.commit();
				}
			});

			return AsyncResult.resolved();
		}

		@Nullable
		private VirtualFile findRoot(Library library)
		{
			for (VirtualFile classesRoot : library.getFiles(BinariesOrderRootType.getInstance()))
			{
				if (VfsUtilCore.isAncestor(classesRoot, myClassFile, true))
				{
					return classesRoot;
				}
			}
			return null;
		}
	}

	private static class ChooseAndAttachSourcesAction implements AttachSourcesProvider.AttachSourcesAction
	{
		private final Project myProject;

		public ChooseAndAttachSourcesAction(Project project)
		{
			myProject = project;
		}

		@Override
		public String getName()
		{
			return ProjectLocalize.moduleLibrariesAttachSourcesButton().get();
		}

		@Override
		public String getBusyText()
		{
			return ProjectLocalize.libraryAttachSourcesActionBusyText().get();
		}

		@Override
		@RequiredUIAccess
		public AsyncResult<Void> perform(final List<LibraryOrderEntry> libraries, UIEvent<Component> e)
		{
			FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleJavaPathDescriptor();
			descriptor.withTitleValue(ProjectLocalize.libraryAttachSourcesAction());
			descriptor.withDescriptionValue(ProjectLocalize.libraryAttachSourcesDescription());
			Library firstLibrary = libraries.get(0).getLibrary();
			VirtualFile[] roots = firstLibrary != null ? firstLibrary.getFiles(BinariesOrderRootType.getInstance()) : VirtualFile.EMPTY_ARRAY;
			VirtualFile[] candidates =
				IdeaFileChooser.chooseFiles(descriptor, myProject, roots.length == 0 ? null : VirtualFilePathUtil.getLocalFile(roots[0]));
			final VirtualFile[] files = PathUIUtils.scanAndSelectDetectedJavaSourceRoots(TargetAWT.to(e.getComponent()), candidates);
			if (files.length == 0)
			{
				return AsyncResult.rejected();
			}

			final Map<Library, LibraryOrderEntry> librariesToAppendSourcesTo = new HashMap<>();
			for (LibraryOrderEntry library : libraries)
			{
				librariesToAppendSourcesTo.put(library.getLibrary(), library);
			}
			if (librariesToAppendSourcesTo.size() == 1)
			{
				appendSources(firstLibrary, files);
			}
			else
			{
				librariesToAppendSourcesTo.put(null, null);
				String title = JavaBundle.message("library.choose.one.to.attach");
				List<LibraryOrderEntry> entries = ContainerUtil.newArrayList(librariesToAppendSourcesTo.values());
				JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<LibraryOrderEntry>(title, entries)
				{
					@Override
					public ListSeparator getSeparatorAbove(LibraryOrderEntry value)
					{
						return value == null ? new ListSeparator() : null;
					}

					@Nonnull
					@Override
					public String getTextFor(LibraryOrderEntry value)
					{
						return value == null ? "All" : value.getPresentableName() + " (" + value.getOwnerModule().getName() + ")";
					}

					@Override
					@RequiredUIAccess
					public PopupStep onChosen(LibraryOrderEntry libraryOrderEntry, boolean finalChoice)
					{
						if (libraryOrderEntry != null)
						{
							appendSources(libraryOrderEntry.getLibrary(), files);
						}
						else
						{
							for (Library libOrderEntry : librariesToAppendSourcesTo.keySet())
							{
								if (libOrderEntry != null)
								{
									appendSources(libOrderEntry, files);
								}
							}
						}
						return FINAL_CHOICE;
					}
				}).showCenteredInCurrentWindow(myProject);
			}

			return AsyncResult.resolved();
		}

		@RequiredUIAccess
		private static void appendSources(final Library library, final VirtualFile[] files)
		{
			Application.get().runWriteAction(() -> {
				Library.ModifiableModel model = library.getModifiableModel();
				for (VirtualFile virtualFile : files)
				{
					model.addRoot(virtualFile, SourcesOrderRootType.getInstance());
				}
				model.commit();
			});
		}
	}
}
