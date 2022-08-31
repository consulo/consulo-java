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
package com.intellij.codeInsight;

import java.awt.event.MouseEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.AddAnnotationIntention;
import com.intellij.codeInsight.intention.impl.DeannotateIntentionAction;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.codeInsight.javadoc.NonCodeAnnotationGenerator;
import com.intellij.codeInspection.dataFlow.EditContractIntention;
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.language.impl.JavaIcons;
import consulo.java.codeInsight.JavaCodeInsightSettings;
import consulo.ui.image.Image;

public class ExternalAnnotationsLineMarkerProvider extends LineMarkerProviderDescriptor
{
	private static final Function<PsiElement, String> ourTooltipProvider = nameIdentifier ->
	{
		PsiModifierListOwner owner = (PsiModifierListOwner) nameIdentifier.getParent();

		return XmlStringUtil.wrapInHtml(NonCodeAnnotationGenerator.getNonCodeHeader(NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values()) + ". Full signature:<p>\n" +
				JavaDocInfoGenerator.generateSignature(owner));
	};

	@RequiredReadAction
	@Nullable
	@Override
	public LineMarkerInfo getLineMarkerInfo(@Nonnull final PsiElement element)
	{
		PsiModifierListOwner owner = getAnnotationOwner(element);
		if(owner == null)
		{
			return null;
		}

		boolean includeSourceInferred = JavaCodeInsightSettings.getInstance().SHOW_SOURCE_INFERRED_ANNOTATIONS;
		boolean hasAnnotationsToShow = ContainerUtil.exists(NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values(), a -> includeSourceInferred || !a.isInferredFromSource());
		if(!hasAnnotationsToShow)
		{
			return null;
		}

		return new LineMarkerInfo<>(element, element.getTextRange(), JavaIcons.Gutter.ExtAnnotation, Pass.LINE_MARKERS, ourTooltipProvider, MyIconGutterHandler.INSTANCE, GutterIconRenderer.Alignment
				.RIGHT);
	}

	@Nullable
	static PsiModifierListOwner getAnnotationOwner(@Nullable PsiElement element)
	{
		if(element == null)
		{
			return null;
		}

		PsiElement owner = element.getParent();
		if(!(owner instanceof PsiModifierListOwner) || !(owner instanceof PsiNameIdentifierOwner))
		{
			return null;
		}
		if(owner instanceof PsiParameter || owner instanceof PsiLocalVariable)
		{
			return null;
		}

		// support non-Java languages where getNameIdentifier may return non-physical psi with the same range
		PsiElement nameIdentifier = ((PsiNameIdentifierOwner) owner).getNameIdentifier();
		if(nameIdentifier == null || !nameIdentifier.getTextRange().equals(element.getTextRange()))
		{
			return null;
		}
		return (PsiModifierListOwner) owner;
	}


	@Nonnull
	@Override
	public String getName()
	{
		return "External annotations";
	}

	@Nullable
	@Override
	public Image getIcon()
	{
		return JavaIcons.Gutter.ExtAnnotation;
	}

	private static class MyIconGutterHandler implements GutterIconNavigationHandler<PsiElement>
	{
		static final MyIconGutterHandler INSTANCE = new MyIconGutterHandler();

		@RequiredUIAccess
		@Override
		public void navigate(MouseEvent e, PsiElement nameIdentifier)
		{
			final PsiElement listOwner = nameIdentifier.getParent();
			final PsiFile containingFile = listOwner.getContainingFile();
			final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(listOwner);

			if(virtualFile != null && containingFile != null)
			{
				final Project project = listOwner.getProject();
				final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

				if(editor != null)
				{
					editor.getCaretModel().moveToOffset(nameIdentifier.getTextOffset());
					final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

					if(file != null && virtualFile.equals(file.getVirtualFile()))
					{
						final JBPopup popup = createActionGroupPopup(containingFile, project, editor);
						if(popup != null)
						{
							popup.show(new RelativePoint(e));
						}
					}
				}
			}
		}

		@Nullable
		protected JBPopup createActionGroupPopup(PsiFile file, Project project, Editor editor)
		{
			final DefaultActionGroup group = new DefaultActionGroup();
			for(final IntentionAction action : IntentionManager.getInstance().getAvailableIntentionActions())
			{
				if(shouldShowInGutterPopup(action) && action.isAvailable(project, editor, file))
				{
					group.add(new ApplyIntentionAction(action, action.getText(), editor, file));
				}
			}

			if(group.getChildrenCount() > 0)
			{
				final DataContext context = SimpleDataContext.getProjectContext(null);
				return JBPopupFactory.getInstance().createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
			}

			return null;
		}

		private static boolean shouldShowInGutterPopup(IntentionAction action)
		{
			return action instanceof AddAnnotationIntention ||
					action instanceof DeannotateIntentionAction ||
					action instanceof EditContractIntention ||
					action instanceof ToggleSourceInferredAnnotations ||
					action instanceof MakeInferredAnnotationExplicit ||
					action instanceof MakeExternalAnnotationExplicit ||
					action instanceof IntentionActionDelegate && shouldShowInGutterPopup(((IntentionActionDelegate) action).getDelegate());
		}
	}
}
