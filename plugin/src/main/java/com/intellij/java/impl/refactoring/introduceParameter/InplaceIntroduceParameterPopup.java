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
package com.intellij.java.impl.refactoring.introduceParameter;

import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import consulo.undoRedo.CommandProcessor;
import consulo.codeEditor.Editor;
import consulo.codeEditor.DocumentMarkupModel;
import consulo.colorScheme.EffectType;
import consulo.codeEditor.markup.HighlighterTargetArea;
import consulo.codeEditor.markup.MarkupModel;
import consulo.colorScheme.TextAttributes;
import consulo.project.Project;
import consulo.application.util.function.Computable;
import consulo.document.util.TextRange;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.psi.*;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.content.scope.SearchScope;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManagerImpl;
import consulo.usage.UsageInfo;
import consulo.logging.Logger;
import consulo.ui.style.StandardColors;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 2/25/11
 */
public class InplaceIntroduceParameterPopup extends AbstractJavaInplaceIntroducer
{
	private static final Logger LOG = Logger.getInstance(InplaceIntroduceParameterPopup.class);

	private final PsiMethod myMethod;
	private final PsiMethod myMethodToSearchFor;
	private final boolean myMustBeFinal;

	private int myParameterIndex = -1;
	private final InplaceIntroduceParameterUI myPanel;


	InplaceIntroduceParameterPopup(final Project project,
								   final Editor editor,
								   final List<UsageInfo> classMemberRefs,
								   final TypeSelectorManagerImpl typeSelectorManager,
								   final PsiExpression expr,
								   final PsiLocalVariable localVar,
								   final PsiMethod method,
								   final PsiMethod methodToSearchFor,
								   final PsiExpression[] occurrences,
								   final IntList parametersToRemove,
								   final boolean mustBeFinal)
	{
		super(project, editor, expr, localVar, occurrences, typeSelectorManager, IntroduceParameterHandler.REFACTORING_NAME
		);
		myMethod = method;
		myMethodToSearchFor = methodToSearchFor;
		myMustBeFinal = mustBeFinal;

		myWholePanel.add(getPreviewComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
				new Insets(0, 5, 0, 5), 0, 0));
		myPanel = new InplaceIntroduceParameterUI(project, localVar, expr, method, parametersToRemove, typeSelectorManager,
				myOccurrences)
		{
			@Override
			protected PsiParameter getParameter()
			{
				return InplaceIntroduceParameterPopup.this.getParameter();
			}

			@Override
			protected void updateControls(JCheckBox[] removeParamsCb)
			{
				super.updateControls(removeParamsCb);
				if(myParameterIndex < 0)
				{
					return;
				}
				restartInplaceIntroduceTemplate();
			}

			protected IntList getParametersToRemove()
			{
				IntList parameters = IntLists.newArrayList();
				for(int i = 0; i < myParametersToRemove.length; i++)
				{
					if(myParametersToRemove[i] != null)
					{
						parameters.add(i);
					}
				}
				return parameters;
			}
		};
		myPanel.appendOccurrencesDelegate(myWholePanel);
	}

	@Override
	protected PsiVariable createFieldToStartTemplateOn(final String[] names, final PsiType defaultType)
	{
		final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myMethod.getProject());
		return ApplicationManager.getApplication().runWriteAction(new Computable<PsiParameter>()
		{
			@Override
			public PsiParameter compute()
			{
				final String name = getInputName() != null ? getInputName() : names[0];
				final PsiParameter anchor = JavaIntroduceParameterMethodUsagesProcessor.getAnchorParameter(myMethod);
				final PsiParameter psiParameter = (PsiParameter) myMethod.getParameterList()
						.addAfter(elementFactory.createParameter(name, defaultType), anchor);
				PsiUtil.setModifierProperty(psiParameter, PsiModifier.FINAL, myPanel.hasFinalModifier());
				myParameterIndex = myMethod.getParameterList().getParameterIndex(psiParameter);
				return psiParameter;
			}
		});
	}

	@Override
	protected PsiElement checkLocalScope()
	{
		return myMethod;
	}

	@Override
	protected SearchScope getReferencesSearchScope(VirtualFile file)
	{
		return new LocalSearchScope(myMethod);
	}

	@Override
	protected VariableKind getVariableKind()
	{
		return VariableKind.PARAMETER;
	}

	@Override
	protected String[] suggestNames(PsiType defaultType, String propName)
	{
		return IntroduceParameterHandler.createNameSuggestionGenerator(myExpr, propName, myProject, null)
				.getSuggestedNameInfo(defaultType).names;
	}


	@Nullable
	private PsiParameter getParameter()
	{
		if(!myMethod.isValid())
		{
			return null;
		}
		final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
		return parameters.length > myParameterIndex && myParameterIndex >= 0 ? parameters[myParameterIndex] : null;
	}


	@Override
	protected JComponent getComponent()
	{
		return myWholePanel;
	}

	@Override
	public boolean isReplaceAllOccurrences()
	{
		return myPanel.isReplaceAllOccurences();
	}

	@Override
	protected PsiVariable getVariable()
	{
		return getParameter();
	}

	@Override
	protected boolean startsOnTheSameElement(RefactoringActionHandler handler, PsiElement element)
	{
		return super.startsOnTheSameElement(handler, element) && handler instanceof IntroduceParameterHandler;
	}


	@Override
	protected void saveSettings(@Nonnull PsiVariable psiVariable)
	{
		myPanel.saveSettings(JavaRefactoringSettings.getInstance());
	}

	protected void performIntroduce()
	{
		boolean isDeleteLocalVariable = false;

		PsiExpression parameterInitializer = myExpr;
		if(getLocalVariable() != null)
		{
			if(myPanel.isUseInitializer())
			{
				parameterInitializer = getLocalVariable().getInitializer();
			}
			isDeleteLocalVariable = myPanel.isDeleteLocalVariable();
		}

		final IntList parametersToRemove = myPanel.getParametersToRemove();

		final IntroduceParameterProcessor processor =
				new IntroduceParameterProcessor(myProject, myMethod,
						myMethodToSearchFor, parameterInitializer, myExpr,
						(PsiLocalVariable) getLocalVariable(), isDeleteLocalVariable, getInputName(),
						myPanel.isReplaceAllOccurences(),
						myPanel.getReplaceFieldsWithGetters(), myMustBeFinal || myPanel.isGenerateFinal(),
						isGenerateDelegate(),
						getType(),
						parametersToRemove);
		final Runnable runnable = new Runnable()
		{
			public void run()
			{
				final Runnable performRefactoring = new Runnable()
				{
					public void run()
					{
						processor.setPrepareSuccessfulSwingThreadCallback(new Runnable()
						{
							@Override
							public void run()
							{
							}
						});
						processor.run();
						normalizeParameterIdxAccordingToRemovedParams(parametersToRemove);
						final PsiParameter parameter = getParameter();
						if(parameter != null)
						{
							InplaceIntroduceParameterPopup.super.saveSettings(parameter);
						}
					}
				};
				if(ApplicationManager.getApplication().isUnitTestMode())
				{
					performRefactoring.run();
				}
				else
				{
					ApplicationManager.getApplication().invokeLater(performRefactoring);
				}
			}
		};
		CommandProcessor.getInstance().executeCommand(myProject, runnable, getCommandName(), null);
	}

	public boolean isGenerateDelegate()
	{
		return myPanel.isGenerateDelegate();
	}

	@Override
	protected void updateTitle(@Nullable PsiVariable variable)
	{
		if(variable == null)
		{
			return;
		}
		updateTitle(variable, variable.getName());
	}

	@Override
	protected void updateTitle(@Nullable final PsiVariable variable, final String value)
	{
		final PsiElement declarationScope = variable != null ? ((PsiParameter) variable).getDeclarationScope() : null;
		if(declarationScope instanceof PsiMethod)
		{
			final PsiMethod psiMethod = (PsiMethod) declarationScope;
			final StringBuilder buf = new StringBuilder();
			buf.append(psiMethod.getName()).append(" (");
			boolean frst = true;
			final List<TextRange> ranges2Remove = new ArrayList<TextRange>();
			TextRange addedRange = null;
			for(PsiParameter parameter : psiMethod.getParameterList().getParameters())
			{
				if(frst)
				{
					frst = false;
				}
				else
				{
					buf.append(", ");
				}
				int startOffset = buf.length();
				if(myMustBeFinal || myPanel.isGenerateFinal())
				{
					buf.append("final ");
				}
				buf.append(parameter.getType().getPresentableText()).append(" ").append(variable == parameter ? value : parameter.getName());
				int endOffset = buf.length();
				if(variable == parameter)
				{
					addedRange = new TextRange(startOffset, endOffset);
				}
				else if(myPanel.isParamToRemove(parameter))
				{
					ranges2Remove.add(new TextRange(startOffset, endOffset));
				}
			}

			buf.append(")");
			setPreviewText(buf.toString());
			final MarkupModel markupModel = DocumentMarkupModel.forDocument(getPreviewEditor().getDocument(), myProject, true);
			markupModel.removeAllHighlighters();
			for(TextRange textRange : ranges2Remove)
			{
				markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(), 0, getTestAttributesForRemoval(), HighlighterTargetArea.EXACT_RANGE);
			}
			markupModel.addRangeHighlighter(addedRange.getStartOffset(), addedRange.getEndOffset(), 0, getTextAttributesForAdd(), HighlighterTargetArea.EXACT_RANGE);
			revalidate();
		}
	}

	private static TextAttributes getTextAttributesForAdd()
	{
		final TextAttributes textAttributes = new TextAttributes();
		textAttributes.setEffectType(EffectType.ROUNDED_BOX);
		textAttributes.setEffectColor(StandardColors.RED);
		return textAttributes;
	}

	private static TextAttributes getTestAttributesForRemoval()
	{
		final TextAttributes textAttributes = new TextAttributes();
		textAttributes.setEffectType(EffectType.STRIKEOUT);
		textAttributes.setEffectColor(StandardColors.BLACK);
		return textAttributes;
	}

	@Override
	protected String getActionName()
	{
		return "IntroduceParameter";
	}

	private void normalizeParameterIdxAccordingToRemovedParams(IntList parametersToRemove)
	{
		parametersToRemove.forEach(value -> {
			if(myParameterIndex >= value)
			{
				myParameterIndex--;
			}
		});
	}

	public void setReplaceAllOccurrences(boolean replaceAll)
	{
		myPanel.setReplaceAllOccurrences(replaceAll);
	}

	public PsiMethod getMethodToIntroduceParameter()
	{
		return myMethod;
	}

	public PsiMethod getMethodToSearchFor()
	{
		return myMethodToSearchFor;
	}
}
