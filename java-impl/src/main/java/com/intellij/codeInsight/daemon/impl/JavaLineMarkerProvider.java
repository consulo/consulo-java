/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo;
import com.intellij.codeInsight.daemon.NavigateAction;
import com.intellij.concurrency.JobLauncher;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import consulo.ui.image.Image;

public class JavaLineMarkerProvider extends LineMarkerProviderDescriptor
{
	protected final DaemonCodeAnalyzerSettings myDaemonSettings;
	protected final EditorColorsManager myColorsManager;
	private final Option myLambdaOption = new Option("java.lambda", "Lambda", AllIcons.Gutter.ImplementingFunctional);
	private final Option myOverriddenOption = new Option("java.overridden", "Overridden method", AllIcons.Gutter.OverridenMethod);
	private final Option myImplementedOption = new Option("java.implemented", "Implemented method", AllIcons.Gutter.ImplementedMethod);
	private final Option myOverridingOption = new Option("java.overriding", "Overriding method", AllIcons.Gutter.OverridingMethod);
	private final Option myImplementingOption = new Option("java.implementing", "Implementing method", AllIcons.Gutter.ImplementingMethod);

	public JavaLineMarkerProvider(DaemonCodeAnalyzerSettings daemonSettings, EditorColorsManager colorsManager)
	{
		myDaemonSettings = daemonSettings;
		myColorsManager = colorsManager;
	}

	@Override
	@Nullable
	public LineMarkerInfo getLineMarkerInfo(@Nonnull final PsiElement element)
	{
		PsiElement parent;
		if(element instanceof PsiIdentifier && (parent = element.getParent()) instanceof PsiMethod)
		{
			if(!myOverridingOption.isEnabled() && !myImplementingOption.isEnabled())
			{
				return null;
			}
			PsiMethod method = (PsiMethod) parent;
			MethodSignatureBackedByPsiMethod superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst();
			if(superSignature != null)
			{
				boolean overrides = method.hasModifierProperty(PsiModifier.ABSTRACT) == superSignature.getMethod().hasModifierProperty(PsiModifier.ABSTRACT);

				final Image icon;
				if(overrides)
				{
					if(!myOverridingOption.isEnabled())
					{
						return null;
					}
					icon = AllIcons.Gutter.OverridingMethod;
				}
				else
				{
					if(!myImplementingOption.isEnabled())
					{
						return null;
					}
					icon = AllIcons.Gutter.ImplementingMethod;
				}
				return createSuperMethodLineMarkerInfo(element, icon, Pass.LINE_MARKERS);
			}
		}

		final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(element);
		final PsiElement firstChild = element.getFirstChild();
		if(interfaceMethod != null && firstChild != null && myLambdaOption.isEnabled())
		{
			return createSuperMethodLineMarkerInfo(firstChild, AllIcons.Gutter.ImplementingFunctional, Pass.LINE_MARKERS);
		}

		if(myDaemonSettings.SHOW_METHOD_SEPARATORS && firstChild == null)
		{
			PsiElement element1 = element;
			boolean isMember = false;
			while(element1 != null && !(element1 instanceof PsiFile) && element1.getPrevSibling() == null)
			{
				element1 = element1.getParent();
				if(element1 instanceof PsiMember)
				{
					isMember = true;
					break;
				}
			}
			if(isMember && !(element1 instanceof PsiAnonymousClass || element1.getParent() instanceof PsiAnonymousClass))
			{
				PsiFile file = element1.getContainingFile();
				Document document = file == null ? null : PsiDocumentManager.getInstance(file.getProject()).getLastCommittedDocument(file);
				boolean drawSeparator = false;

				if(document != null)
				{
					CharSequence documentChars = document.getCharsSequence();
					int category = getCategory(element1, documentChars);
					for(PsiElement child = element1.getPrevSibling(); child != null; child = child.getPrevSibling())
					{
						int category1 = getCategory(child, documentChars);
						if(category1 == 0)
						{
							continue;
						}
						drawSeparator = category != 1 || category1 != 1;
						break;
					}
				}

				if(drawSeparator)
				{
					LineMarkerInfo info = new LineMarkerInfo<>(element, element.getTextRange(), null, Pass.LINE_MARKERS, FunctionUtil.<Object, String>nullConstant(), null, GutterIconRenderer
							.Alignment.RIGHT);
					EditorColorsScheme scheme = myColorsManager.getGlobalScheme();
					info.separatorColor = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
					info.separatorPlacement = SeparatorPlacement.TOP;
					return info;
				}
			}
		}

		return null;
	}

	@Nonnull
	private static LineMarkerInfo createSuperMethodLineMarkerInfo(@Nonnull PsiElement name, @Nonnull Image icon, int passId)
	{
		ArrowUpLineMarkerInfo info = new ArrowUpLineMarkerInfo(name, icon, MarkerType.OVERRIDING_METHOD, passId);
		return NavigateAction.setNavigateAction(info, "Go to super method", IdeActions.ACTION_GOTO_SUPER);
	}

	private static int getCategory(@Nonnull PsiElement element, @Nonnull CharSequence documentChars)
	{
		if(element instanceof PsiField || element instanceof PsiTypeParameter)
		{
			return 1;
		}
		if(element instanceof PsiClass || element instanceof PsiClassInitializer)
		{
			return 2;
		}
		if(element instanceof PsiMethod)
		{
			if(((PsiMethod) element).hasModifierProperty(PsiModifier.ABSTRACT))
			{
				return 1;
			}
			TextRange textRange = element.getTextRange();
			int start = textRange.getStartOffset();
			int end = Math.min(documentChars.length(), textRange.getEndOffset());
			int crlf = StringUtil.getLineBreakCount(documentChars.subSequence(start, end));
			return crlf == 0 ? 1 : 2;
		}
		return 0;
	}

	@Override
	public void collectSlowLineMarkers(@Nonnull final List<PsiElement> elements, @Nonnull final Collection<LineMarkerInfo> result)
	{
		ApplicationManager.getApplication().assertReadAccessAllowed();

		List<Computable<List<LineMarkerInfo>>> tasks = new ArrayList<>();

		MultiMap<PsiClass, PsiMethod> byClass = MultiMap.create();
		//noinspection ForLoopReplaceableByForEach
		for(int i = 0; i < elements.size(); i++)
		{
			PsiElement element = elements.get(i);
			ProgressManager.checkCanceled();
			if(!(element instanceof PsiIdentifier))
			{
				continue;
			}
			PsiElement parent = element.getParent();
			if(parent instanceof PsiMethod)
			{
				final PsiMethod method = (PsiMethod) parent;
				PsiClass psiClass = method.getContainingClass();
				if(PsiUtil.canBeOverriden(method) && psiClass != null)
				{
					byClass.putValue(psiClass, method);
				}
			}
			else if(parent instanceof PsiClass && !(parent instanceof PsiTypeParameter))
			{
				tasks.add(() -> collectInheritingClasses((PsiClass) parent));
			}
		}
		for(PsiClass psiClass : byClass.keySet())
		{
			Collection<PsiMethod> methods = byClass.get(psiClass);
			tasks.add(() -> collectSiblingInheritedMethods(methods));
			tasks.add(() -> collectOverridingMethods(methods, psiClass));
		}

		Object lock = new Object();
		ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
		JobLauncher.getInstance().invokeConcurrentlyUnderProgress(tasks, indicator, true, computable ->
		{
			List<LineMarkerInfo> infos = computable.compute();
			synchronized(lock)
			{
				result.addAll(infos);
			}
			return true;
		});
	}

	private static List<LineMarkerInfo> collectSiblingInheritedMethods(@Nonnull final Collection<PsiMethod> methods)
	{
		Map<PsiMethod, FindSuperElementsHelper.SiblingInfo> map = FindSuperElementsHelper.getSiblingInheritanceInfos(methods);
		return ContainerUtil.map(map.keySet(), method ->
		{
			PsiElement range = getMethodRange(method);
			ArrowUpLineMarkerInfo upInfo = new ArrowUpLineMarkerInfo(range, AllIcons.Gutter.SiblingInheritedMethod, MarkerType.SIBLING_OVERRIDING_METHOD, Pass.LINE_MARKERS);
			return NavigateAction.setNavigateAction(upInfo, "Go to super method", IdeActions.ACTION_GOTO_SUPER);
		});
	}

	@Nonnull
	private static PsiElement getMethodRange(@Nonnull PsiMethod method)
	{
		PsiElement range;
		if(method.isPhysical())
		{
			range = method.getNameIdentifier();
		}
		else
		{
			final PsiElement navigationElement = method.getNavigationElement();
			range = navigationElement instanceof PsiNameIdentifierOwner ? ((PsiNameIdentifierOwner) navigationElement).getNameIdentifier() : navigationElement;
		}
		if(range == null)
		{
			range = method;
		}
		return range;
	}

	protected List<LineMarkerInfo> collectInheritingClasses(@Nonnull PsiClass aClass)
	{
		if(aClass.hasModifierProperty(PsiModifier.FINAL))
		{
			return Collections.emptyList();
		}
		if(CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName()))
		{
			return Collections.emptyList(); // It's useless to have overridden markers for object.
		}

		PsiClass subClass = DirectClassInheritorsSearch.search(aClass).findFirst();
		if(subClass != null || FunctionalExpressionSearch.search(aClass).findFirst() != null)
		{
			final Image icon;
			if(aClass.isInterface())
			{
				if(!myImplementedOption.isEnabled())
				{
					return Collections.emptyList();
				}
				icon = AllIcons.Gutter.ImplementedMethod;
			}
			else
			{
				if(!myOverriddenOption.isEnabled())
				{
					return Collections.emptyList();
				}
				icon = AllIcons.Gutter.OverridenMethod;
			}
			PsiElement range = aClass.getNameIdentifier();
			if(range == null)
			{
				range = aClass;
			}
			MarkerType type = MarkerType.SUBCLASSED_CLASS;
			LineMarkerInfo info = new LineMarkerInfo<>(range, range.getTextRange(), icon, Pass.LINE_MARKERS, type.getTooltip(), type.getNavigationHandler(), GutterIconRenderer.Alignment.RIGHT);
			NavigateAction.setNavigateAction(info, aClass.isInterface() ? "Go to implementation(s)" : "Go to subclass(es)", IdeActions.ACTION_GOTO_IMPLEMENTATION);
			return Collections.singletonList(info);
		}
		return Collections.emptyList();
	}

	private List<LineMarkerInfo> collectOverridingMethods(@Nonnull final Iterable<PsiMethod> _methods, @Nonnull PsiClass containingClass)
	{
		if(!myOverriddenOption.isEnabled() && !myImplementedOption.isEnabled())
		{
			return Collections.emptyList();
		}
		final Set<PsiMethod> overridden = new HashSet<>();

		Set<PsiMethod> methodSet = ContainerUtil.newHashSet(_methods);

		AllOverridingMethodsSearch.search(containingClass).forEach(pair ->
		{
			ProgressManager.checkCanceled();

			final PsiMethod superMethod = pair.getFirst();
			if(methodSet.remove(superMethod))
			{
				overridden.add(superMethod);
			}
			return !methodSet.isEmpty();
		});

		if(!methodSet.isEmpty())
		{
			final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(containingClass);
			if(interfaceMethod != null && FunctionalExpressionSearch.search(containingClass).findFirst() != null)
			{
				overridden.add(interfaceMethod);
			}
		}

		List<LineMarkerInfo> result = new ArrayList<>();
		for(PsiMethod method : overridden)
		{
			ProgressManager.checkCanceled();
			boolean overrides = !method.hasModifierProperty(PsiModifier.ABSTRACT);
			if(overrides)
			{
				if(!myOverriddenOption.isEnabled())
				{
					return Collections.emptyList();
				}
			}
			else
			{
				if(!myImplementedOption.isEnabled())
				{
					return Collections.emptyList();
				}
			}
			PsiElement range = getMethodRange(method);
			final MarkerType type = MarkerType.OVERRIDDEN_METHOD;
			final Image icon = overrides ? AllIcons.Gutter.OverridenMethod : AllIcons.Gutter.ImplementedMethod;
			LineMarkerInfo<PsiElement> info = new LineMarkerInfo<>(range, range.getTextRange(), icon, Pass.LINE_MARKERS, type.getTooltip(), type.getNavigationHandler(), GutterIconRenderer.Alignment
					.RIGHT);
			NavigateAction.setNavigateAction(info, overrides ? "Go to overriding methods" : "Go to implementation(s)", IdeActions.ACTION_GOTO_IMPLEMENTATION);
			result.add(info);
		}
		return result;
	}

	@Override
	public String getName()
	{
		return "Java line markers";
	}

	@Override
	public Option[] getOptions()
	{
		return new Option[]{
				myLambdaOption,
				myOverriddenOption,
				myImplementedOption,
				myOverridingOption,
				myImplementingOption
		};
	}

	private static class ArrowUpLineMarkerInfo extends MergeableLineMarkerInfo<PsiElement>
	{
		private ArrowUpLineMarkerInfo(@Nonnull PsiElement element, @Nonnull Image icon, @Nonnull MarkerType markerType, int passId)
		{
			super(element, element.getTextRange(), icon, passId, markerType.getTooltip(), markerType.getNavigationHandler(), GutterIconRenderer.Alignment.LEFT);
		}

		@Override
		public boolean canMergeWith(@Nonnull MergeableLineMarkerInfo<?> info)
		{
			if(!(info instanceof ArrowUpLineMarkerInfo))
			{
				return false;
			}
			PsiElement otherElement = info.getElement();
			PsiElement myElement = getElement();
			return otherElement != null && myElement != null;
		}

		@Nonnull
		@Override
		public Image getCommonIcon(@Nonnull List<MergeableLineMarkerInfo> infos)
		{
			return myIcon;
		}

		@Nonnull
		@Override
		public Function<? super PsiElement, String> getCommonTooltip(@Nonnull List<MergeableLineMarkerInfo> infos)
		{
			return element -> "Multiple method overrides";
		}

		@Override
		public String getElementPresentation(PsiElement element)
		{
			final PsiElement parent = element.getParent();
			if(parent instanceof PsiFunctionalExpression)
			{
				return PsiExpressionTrimRenderer.render((PsiExpression) parent);
			}
			return super.getElementPresentation(element);
		}
	}
}
