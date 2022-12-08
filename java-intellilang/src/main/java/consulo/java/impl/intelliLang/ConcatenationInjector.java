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
package consulo.java.impl.intelliLang;

import com.intellij.java.indexing.impl.stubs.index.JavaAnnotationIndex;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.document.util.ProperTextRange;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.util.PatternValuesIndex;
import consulo.ide.impl.intelliLang.Configuration;
import consulo.ide.impl.intelliLang.inject.InjectedLanguage;
import consulo.ide.impl.intelliLang.inject.InjectorUtils;
import consulo.ide.impl.intelliLang.inject.TemporaryPlacesRegistry;
import consulo.ide.impl.intelliLang.inject.config.BaseInjection;
import consulo.ide.impl.intelliLang.inject.config.InjectionPlace;
import consulo.ide.impl.psi.injection.LanguageInjectionSupport;
import consulo.ide.impl.psi.injection.impl.ProjectInjectionConfiguration;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.inject.ConcatenationAwareInjector;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.inject.MultiHostRegistrar;
import consulo.language.inject.ReferenceInjector;
import consulo.language.parser.ParserDefinition;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.Trinity;
import consulo.util.lang.ref.Ref;
import consulo.java.impl.intelliLang.util.AnnotationUtilEx;
import consulo.java.impl.intelliLang.util.ContextComputationProcessor;
import consulo.java.impl.intelliLang.util.PsiUtilEx;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * @author cdr
 */
public class ConcatenationInjector implements ConcatenationAwareInjector
{
	private final Configuration myConfiguration;
	private final Project myProject;
	private final TemporaryPlacesRegistry myTemporaryPlacesRegistry;
	private final CachedValue<Collection<String>> myAnnoIndex;
	private final CachedValue<Collection<String>> myXmlIndex;
	private final LanguageInjectionSupport mySupport;


	public ConcatenationInjector(ProjectInjectionConfiguration configuration, Project project, TemporaryPlacesRegistry temporaryPlacesRegistry)
	{
		myConfiguration = configuration;
		myProject = project;
		myTemporaryPlacesRegistry = temporaryPlacesRegistry;
		mySupport = InjectorUtils.findNotNullInjectionSupport(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID);
		myXmlIndex = CachedValuesManager.getManager(myProject).createCachedValue(new CachedValueProvider<Collection<String>>()
		{
			public Result<Collection<String>> compute()
			{
				final Map<ElementPattern<?>, BaseInjection> map = new HashMap<>();
				for(BaseInjection injection : myConfiguration.getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID))
				{
					for(InjectionPlace place : injection.getInjectionPlaces())
					{
						if(!place.isEnabled() || place.getElementPattern() == null)
						{
							continue;
						}
						map.put(place.getElementPattern(), injection);
					}
				}
				final Set<String> stringSet = PatternValuesIndex.buildStringIndex(map.keySet());
				return new Result<>(stringSet, myConfiguration);
			}
		}, false);
		myAnnoIndex = CachedValuesManager.getManager(myProject).createCachedValue(new CachedValueProvider<Collection<String>>()
		{
			public Result<Collection<String>> compute()
			{
				final String annotationClass = myConfiguration.getAdvancedConfiguration().getLanguageAnnotationClass();
				final Collection<String> result = new HashSet<>();
				final ArrayList<String> annoClasses = new ArrayList<>(3);
				annoClasses.add(StringUtil.getShortName(annotationClass));
				for(int cursor = 0; cursor < annoClasses.size(); cursor++)
				{
					final String annoClass = annoClasses.get(cursor);
					for(PsiAnnotation annotation : JavaAnnotationIndex.getInstance().get(annoClass, myProject, GlobalSearchScope.allScope(myProject)))
					{
						final PsiElement modList = annotation.getParent();
						if(!(modList instanceof PsiModifierList))
						{
							continue;
						}
						final PsiElement element = modList.getParent();
						if(element instanceof PsiParameter)
						{
							final PsiElement scope = ((PsiParameter) element).getDeclarationScope();
							if(scope instanceof PsiNamedElement)
							{
								ContainerUtil.addIfNotNull(result, ((PsiNamedElement) scope).getName());
							}
							else
							{
								ContainerUtil.addIfNotNull(result, ((PsiNamedElement) element).getName());
							}
						}
						else if(element instanceof PsiNamedElement)
						{
							if(element instanceof PsiClass && ((PsiClass) element).isAnnotationType())
							{
								final String s = ((PsiClass) element).getName();
								if(!annoClasses.contains(s))
								{
									annoClasses.add(s);
								}
							}
							else
							{
								ContainerUtil.addIfNotNull(result, ((PsiNamedElement) element).getName());
							}
						}
					}
				}
				return new Result<>(result, PsiModificationTracker.MODIFICATION_COUNT, myConfiguration);
			}
		}, false);
	}

	@Override
	public void inject(@Nonnull final MultiHostRegistrar registrar, @Nonnull PsiElement... operands)
	{
		if(operands.length == 0)
		{
			return;
		}
		boolean hasLiteral = false;
		InjectedLanguage tempInjectedLanguage = null;
		PsiFile containingFile = null;
		for(PsiElement operand : operands)
		{
			if(PsiUtilEx.isStringOrCharacterLiteral(operand))
			{
				if(containingFile == null)
				{
					containingFile = operands[0].getContainingFile();
				}

				tempInjectedLanguage = myTemporaryPlacesRegistry.getLanguageFor((PsiLanguageInjectionHost) operand, containingFile);
				hasLiteral = true;
				if(tempInjectedLanguage != null)
				{
					break;
				}
			}
		}
		if(!hasLiteral)
		{
			return;
		}
		final Language tempLanguage = tempInjectedLanguage == null ? null : tempInjectedLanguage.getLanguage();
		final PsiFile finalContainingFile = containingFile;
		InjectionProcessor injectionProcessor = new InjectionProcessor(myConfiguration, mySupport, operands)
		{
			@Override
			protected Pair<PsiLanguageInjectionHost, Language> processInjection(Language language,
																				List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list,
																				boolean settingsAvailable,
																				boolean unparsable)
			{
				InjectorUtils.registerInjection(language, list, finalContainingFile, registrar);
				PsiLanguageInjectionHost host = list.get(0).getFirst();
				InjectorUtils.registerSupport(mySupport, settingsAvailable, host, language);
				InjectorUtils.putInjectedFileUserData(host, language, InjectedLanguageManager.FRANKENSTEIN_INJECTION, unparsable ? Boolean.TRUE : null);
				return Pair.create(host, language);
			}

			@Override
			protected boolean areThereInjectionsWithName(String methodName, boolean annoOnly)
			{
				if(getAnnotatedElementsValue().contains(methodName))
				{
					return true;
				}
				if(!annoOnly && getXmlAnnotatedElementsValue().contains(methodName))
				{
					return true;
				}
				return false;
			}
		};

		if(tempLanguage != null)
		{
			BaseInjection baseInjection = new BaseInjection(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID);
			baseInjection.setInjectedLanguageId(tempInjectedLanguage.getID());
			List<Pair<PsiLanguageInjectionHost, Language>> list = injectionProcessor.processInjectionWithContext(baseInjection, false);
			for(Pair<PsiLanguageInjectionHost, Language> pair : list)
			{
				PsiLanguageInjectionHost host = pair.getFirst();
				Language language = pair.getSecond();
				InjectorUtils.putInjectedFileUserData(host, language, LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE, tempInjectedLanguage);
			}
		}
		else
		{
			injectionProcessor.processInjections();
		}
	}

	public static class InjectionProcessor
	{

		private final Configuration myConfiguration;
		private final LanguageInjectionSupport mySupport;
		private final PsiElement[] myOperands;
		private boolean myShouldStop;
		private boolean myUnparsable;

		public InjectionProcessor(Configuration configuration, LanguageInjectionSupport support, PsiElement... operands)
		{
			myConfiguration = configuration;
			mySupport = support;
			myOperands = operands;
		}

		public void processInjections()
		{
			final PsiElement firstOperand = myOperands[0];
			final PsiElement topBlock = PsiUtil.getTopLevelEnclosingCodeBlock(firstOperand, null);
			final LocalSearchScope searchScope = new LocalSearchScope(new PsiElement[]{
					topBlock instanceof PsiCodeBlock
							? topBlock : firstOperand.getContainingFile()
			}, "", true);
			final Set<PsiModifierListOwner> visitedVars = new HashSet<>();
			final LinkedList<PsiElement> places = new LinkedList<>();
			places.add(firstOperand);
			final AnnotationUtilEx.AnnotatedElementVisitor visitor = new AnnotationUtilEx.AnnotatedElementVisitor()
			{
				@RequiredReadAction
				public boolean visitMethodParameter(PsiExpression expression, PsiCallExpression psiCallExpression)
				{
					final PsiExpressionList list = psiCallExpression.getArgumentList();
					assert list != null;
					final int index = ArrayUtil.indexOf(list.getExpressions(), expression);
					final String methodName;
					if(psiCallExpression instanceof PsiMethodCallExpression)
					{
						final String referenceName = ((PsiMethodCallExpression) psiCallExpression).getMethodExpression().getReferenceName();
						if("super".equals(referenceName) || "this".equals(referenceName))
						{ // constructor call
							final PsiClass psiClass = PsiTreeUtil.getParentOfType(psiCallExpression, PsiClass.class, true);
							final PsiClass psiTargetClass = "super".equals(referenceName) ? psiClass == null ? null : psiClass.getSuperClass() : psiClass;
							methodName = psiTargetClass == null ? null : psiTargetClass.getName();
						}
						else
						{
							methodName = referenceName;
						}
					}
					else if(psiCallExpression instanceof PsiNewExpression)
					{
						final PsiJavaCodeReferenceElement classRef = ((PsiNewExpression) psiCallExpression).getClassOrAnonymousClassReference();
						methodName = classRef == null ? null : classRef.getReferenceName();
					}
					else
					{
						methodName = null;
					}
					if(methodName != null && areThereInjectionsWithName(methodName, false))
					{
						final PsiMethod method = psiCallExpression.resolveMethod();
						final PsiParameter[] parameters = method == null ? PsiParameter.EMPTY_ARRAY : method.getParameterList().getParameters();
						if(index >= 0 && index < parameters.length && method != null)
						{
							process(parameters[index], method, index);
						}
					}
					return false;
				}

				@RequiredReadAction
				public boolean visitMethodReturnStatement(PsiReturnStatement parent, PsiMethod method)
				{
					if(areThereInjectionsWithName(method.getName(), false))
					{
						process(method, method, -1);
					}
					return false;
				}

				@RequiredReadAction
				public boolean visitVariable(PsiVariable variable)
				{
					if(myConfiguration.getAdvancedConfiguration().getDfaOption() != Configuration.DfaOption.OFF && visitedVars.add(variable))
					{
						ReferencesSearch.search(variable, searchScope).forEach(psiReference -> {
							final PsiElement element = psiReference.getElement();
							if(element instanceof PsiExpression)
							{
								final PsiExpression refExpression = (PsiExpression) element;
								places.add(refExpression);
								if(!myUnparsable)
								{
									myUnparsable = checkUnparsableReference(refExpression);
								}
							}
							return true;
						});
					}
					if(!processCommentInjections(variable))
					{
						myShouldStop = true;
					}
					else if(areThereInjectionsWithName(variable.getName(), false))
					{
						process(variable, null, -1);
					}
					return false;
				}

				@RequiredReadAction
				public boolean visitAnnotationParameter(PsiNameValuePair nameValuePair, PsiAnnotation psiAnnotation)
				{
					final String paramName = nameValuePair.getName();
					final String methodName = paramName != null ? paramName : PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;
					if(areThereInjectionsWithName(methodName, false))
					{
						final PsiReference reference = nameValuePair.getReference();
						final PsiElement element = reference == null ? null : reference.resolve();
						if(element instanceof PsiMethod)
						{
							process((PsiMethod) element, (PsiMethod) element, -1);
						}
					}
					return false;
				}

				@RequiredReadAction
				public boolean visitReference(PsiReferenceExpression expression)
				{
					if(myConfiguration.getAdvancedConfiguration().getDfaOption() == Configuration.DfaOption.OFF)
					{
						return true;
					}
					final PsiElement e = expression.resolve();
					if(e instanceof PsiVariable)
					{
						if(e instanceof PsiParameter)
						{
							final PsiParameter p = (PsiParameter) e;
							final PsiElement declarationScope = p.getDeclarationScope();
							final PsiMethod method = declarationScope instanceof PsiMethod ? (PsiMethod) declarationScope : null;
							final PsiParameterList parameterList = method == null ? null : method.getParameterList();
							// don't check catchblock parameters & etc.
							if(!(parameterList == null || parameterList != e.getParent()) &&
									areThereInjectionsWithName(method.getName(), false))
							{
								final int parameterIndex = parameterList.getParameterIndex((PsiParameter) e);
								process((PsiModifierListOwner) e, method, parameterIndex);
							}
						}
						visitVariable((PsiVariable) e);
					}
					return !myShouldStop;
				}
			};

			while(!places.isEmpty() && !myShouldStop)
			{
				final PsiElement curPlace = places.removeFirst();
				AnnotationUtilEx.visitAnnotatedElements(curPlace, visitor);
			}
		}

		private boolean processCommentInjections(PsiVariable owner)
		{
			Ref<PsiElement> causeRef = Ref.create();
			PsiElement anchor = owner.getFirstChild() instanceof PsiComment ?
					(owner.getModifierList() != null ? owner.getModifierList() : owner.getTypeElement()) : owner;
			if(anchor == null)
			{
				return true;
			}
			BaseInjection injection = mySupport.findCommentInjection(anchor, causeRef);
			return injection == null || processCommentInjectionInner(owner, causeRef.get(), injection);
		}

		protected boolean processCommentInjectionInner(PsiVariable owner, PsiElement comment, BaseInjection injection)
		{
			processInjectionWithContext(injection, false);
			return false;
		}

		private void process(final PsiModifierListOwner owner, PsiMethod method, int paramIndex)
		{
			if(!processAnnotationInjections(owner))
			{
				myShouldStop = true;
			}
			for(BaseInjection injection : myConfiguration.getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID))
			{
				if(injection.acceptsPsiElement(owner))
				{
					if(!processXmlInjections(injection, owner, method, paramIndex))
					{
						myShouldStop = true;
						break;
					}
				}
			}
		}

		private boolean processAnnotationInjections(final PsiModifierListOwner annoElement)
		{
			final String checkName;
			if(annoElement instanceof PsiParameter)
			{
				final PsiElement scope = ((PsiParameter) annoElement).getDeclarationScope();
				checkName = scope instanceof PsiMethod ? ((PsiNamedElement) scope).getName() : ((PsiNamedElement) annoElement).getName();
			}
			else if(annoElement instanceof PsiNamedElement)
			{
				checkName = ((PsiNamedElement) annoElement).getName();
			}
			else
			{
				checkName = null;
			}
			if(checkName == null || !areThereInjectionsWithName(checkName, true))
			{
				return true;
			}
			final PsiAnnotation[] annotations =
					AnnotationUtilEx.getAnnotationFrom(annoElement, myConfiguration.getAdvancedConfiguration().getLanguageAnnotationPair(), true);
			if(annotations.length > 0)
			{
				return processAnnotationInjectionInner(annoElement, annotations);
			}
			return true;
		}

		protected boolean processAnnotationInjectionInner(PsiModifierListOwner owner, PsiAnnotation[] annotations)
		{
			final String id = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
			final String prefix = AnnotationUtilEx.calcAnnotationValue(annotations, "prefix");
			final String suffix = AnnotationUtilEx.calcAnnotationValue(annotations, "suffix");
			final BaseInjection injection = new BaseInjection(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID);
			if(prefix != null)
			{
				injection.setPrefix(prefix);
			}
			if(suffix != null)
			{
				injection.setSuffix(suffix);
			}
			if(id != null)
			{
				injection.setInjectedLanguageId(id);
			}
			processInjectionWithContext(injection, false);
			return false;
		}

		protected boolean processXmlInjections(BaseInjection injection, PsiModifierListOwner owner, PsiMethod method, int paramIndex)
		{
			processInjectionWithContext(injection, true);
			if(injection.isTerminal())
			{
				return false;
			}
			return true;
		}

		protected List<Pair<PsiLanguageInjectionHost, Language>> processInjectionInner(BaseInjection injection, boolean settingsAvailable)
		{
			return processInjectionWithContext(injection, settingsAvailable);
		}

		private List<Pair<PsiLanguageInjectionHost, Language>> processInjectionWithContext(BaseInjection injection, boolean settingsAvailable)
		{
			Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
			if(language == null)
			{
				ReferenceInjector injector = ReferenceInjector.findById(injection.getInjectedLanguageId());
				if(injector != null)
				{
					language = injector.toLanguage();
				}
				else
				{
					return List.of();
				}
			}

			final boolean separateFiles = !injection.isSingleFile() && StringUtil.isNotEmpty(injection.getValuePattern());

			final Ref<Boolean> unparsableRef = Ref.create(myUnparsable);
			final List<Object> objects = ContextComputationProcessor.collectOperands(injection.getPrefix(), injection.getSuffix(), unparsableRef, myOperands);
			if(objects.isEmpty())
			{
				return List.of();
			}
			final List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> result = new ArrayList<>();
			final int len = objects.size();
			for(int i = 0; i < len; i++)
			{
				String curPrefix = null;
				Object o = objects.get(i);
				if(o instanceof String)
				{
					curPrefix = (String) o;
					if(i == len - 1)
					{
						return List.of(); // IDEADEV-26751
					}
					o = objects.get(++i);
				}
				String curSuffix = null;
				PsiLanguageInjectionHost curHost = null;
				if(o instanceof PsiLanguageInjectionHost)
				{
					curHost = (PsiLanguageInjectionHost) o;
					if(i == len - 2)
					{
						final Object next = objects.get(i + 1);
						if(next instanceof String)
						{
							i++;
							curSuffix = (String) next;
						}
					}
				}
				if(curHost == null)
				{
					unparsableRef.set(Boolean.TRUE);
				}
				else
				{
					if(!(curHost instanceof PsiLiteralExpression))
					{
						TextRange textRange = ElementManipulators.getManipulator(curHost).getRangeInElement(curHost);
						ProperTextRange.assertProperRange(textRange, injection);
						result.add(Trinity.create(curHost, InjectedLanguage.create(injection.getInjectedLanguageId(), curPrefix, curSuffix, true),
								textRange));
					}
					else
					{
						final List<TextRange> injectedArea = injection.getInjectedArea(curHost);
						for(int j = 0, injectedAreaSize = injectedArea.size(); j < injectedAreaSize; j++)
						{
							TextRange textRange = injectedArea.get(j);
							ProperTextRange.assertProperRange(textRange, injection);
							result.add(Trinity.create(
									curHost, InjectedLanguage.create(injection.getInjectedLanguageId(),
											(separateFiles || j == 0 ? curPrefix : ""),
											(separateFiles || j == injectedAreaSize - 1 ? curSuffix : ""),
											true), textRange));
						}
					}
				}
			}

			List<Pair<PsiLanguageInjectionHost, Language>> res = new ArrayList<>();
			if(separateFiles)
			{
				for(Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result)
				{
					ContainerUtil.addIfNotNull(res, processInjection(language, Collections.singletonList(trinity), settingsAvailable, false));
				}
			}
			else
			{
				if(isReferenceInject(language))
				{
					// OMG in case of reference inject they confused shreds (several places in the host file to form a single injection) with several injections
					for(Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result)
					{
						ContainerUtil.addIfNotNull(res, processInjection(language, Collections.singletonList(trinity), settingsAvailable, unparsableRef.get()));
					}
				}
				else
				{
					ContainerUtil.addIfNotNull(res, processInjection(language, result, settingsAvailable, unparsableRef.get()));
				}
			}
			return res;
		}


		private static boolean isReferenceInject(Language language)
		{
			return ParserDefinition.forLanguage(language) == null && ReferenceInjector.findById(language.getID()) != null;
		}

		protected Pair<PsiLanguageInjectionHost, Language> processInjection(Language language,
																			List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list,
																			boolean xmlInjection,
																			boolean unparsable)
		{
			return null;
		}

		protected boolean areThereInjectionsWithName(String methodName, boolean annoOnly)
		{
			return true;
		}
	}

	private static boolean checkUnparsableReference(final PsiExpression refExpression)
	{
		final PsiElement parent = refExpression.getParent();
		if(parent instanceof PsiAssignmentExpression)
		{
			final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) parent;
			final IElementType operation = assignmentExpression.getOperationTokenType();
			if(assignmentExpression.getLExpression() == refExpression && JavaTokenType.PLUSEQ.equals(operation))
			{
				return true;
			}
		}
		else if(parent instanceof PsiPolyadicExpression)
		{
			return true;
		}
		return false;
	}


	public Collection<String> getAnnotatedElementsValue()
	{
		// note: external annotations not supported
		return myAnnoIndex.getValue();
	}

	private Collection<String> getXmlAnnotatedElementsValue()
	{
		return myXmlIndex.getValue();
	}
}
