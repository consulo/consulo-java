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
package com.intellij.psi.impl;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.*;
import com.intellij.util.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import consulo.java.module.util.JavaClassNames;
import consulo.logging.Logger;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolderEx;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author ik
 * @since 24.10.2003
 */
public class PsiClassImplUtil
{
	private static final Logger LOG = Logger.getInstance(PsiClassImplUtil.class);
	private static final Key<ParameterizedCachedValue<Map<GlobalSearchScope, MembersMap>, PsiClass>> MAP_IN_CLASS_KEY = Key.create("MAP_KEY");

	private static boolean JAVA_CORRECT_CLASS_TYPE_BY_PLACE_RESOLVE_SCOPE = SystemProperties.getBooleanProperty("java.correct.class.type.by.place.resolve.scope", true);

	private PsiClassImplUtil()
	{
	}

	@Nonnull
	public static PsiField[] getAllFields(@Nonnull PsiClass aClass)
	{
		List<PsiField> map = getAllByMap(aClass, MemberType.FIELD);
		return map.toArray(new PsiField[map.size()]);
	}

	@Nonnull
	public static PsiMethod[] getAllMethods(@Nonnull PsiClass aClass)
	{
		List<PsiMethod> methods = getAllByMap(aClass, MemberType.METHOD);
		return methods.toArray(new PsiMethod[methods.size()]);
	}

	@Nonnull
	public static PsiClass[] getAllInnerClasses(@Nonnull PsiClass aClass)
	{
		List<PsiClass> classes = getAllByMap(aClass, MemberType.CLASS);
		return classes.toArray(new PsiClass[classes.size()]);
	}

	@javax.annotation.Nullable
	public static PsiField findFieldByName(@Nonnull PsiClass aClass, String name, boolean checkBases)
	{
		List<PsiMember> byMap = findByMap(aClass, name, checkBases, MemberType.FIELD);
		return byMap.isEmpty() ? null : (PsiField) byMap.get(0);
	}

	@Nonnull
	public static PsiMethod[] findMethodsByName(@Nonnull PsiClass aClass, String name, boolean checkBases)
	{
		List<PsiMember> methods = findByMap(aClass, name, checkBases, MemberType.METHOD);
		//noinspection SuspiciousToArrayCall
		return methods.toArray(new PsiMethod[methods.size()]);
	}

	@javax.annotation.Nullable
	public static PsiMethod findMethodBySignature(@Nonnull PsiClass aClass, @Nonnull PsiMethod patternMethod, final boolean checkBases)
	{
		final List<PsiMethod> result = findMethodsBySignature(aClass, patternMethod, checkBases, true);
		return result.isEmpty() ? null : result.get(0);
	}

	// ----------------------------- findMethodsBySignature -----------------------------------

	@Nonnull
	public static PsiMethod[] findMethodsBySignature(@Nonnull PsiClass aClass, @Nonnull PsiMethod patternMethod, final boolean checkBases)
	{
		List<PsiMethod> methods = findMethodsBySignature(aClass, patternMethod, checkBases, false);
		return methods.toArray(new PsiMethod[methods.size()]);
	}

	@Nonnull
	private static List<PsiMethod> findMethodsBySignature(@Nonnull PsiClass aClass, @Nonnull PsiMethod patternMethod, boolean checkBases, boolean stopOnFirst)
	{
		final PsiMethod[] methodsByName = aClass.findMethodsByName(patternMethod.getName(), checkBases);
		if(methodsByName.length == 0)
		{
			return Collections.emptyList();
		}
		final List<PsiMethod> methods = new SmartList<PsiMethod>();
		final MethodSignature patternSignature = patternMethod.getSignature(PsiSubstitutor.EMPTY);
		for(final PsiMethod method : methodsByName)
		{
			final PsiClass superClass = method.getContainingClass();
			final PsiSubstitutor substitutor = checkBases && !aClass.equals(superClass) && superClass != null ? TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY)
					: PsiSubstitutor.EMPTY;
			final MethodSignature signature = method.getSignature(substitutor);
			if(signature.equals(patternSignature))
			{
				methods.add(method);
				if(stopOnFirst)
				{
					break;
				}
			}
		}
		return methods;
	}

	// ----------------------------------------------------------------------------------------

	@javax.annotation.Nullable
	public static PsiClass findInnerByName(@Nonnull PsiClass aClass, String name, boolean checkBases)
	{
		List<PsiMember> byMap = findByMap(aClass, name, checkBases, MemberType.CLASS);
		return byMap.isEmpty() ? null : (PsiClass) byMap.get(0);
	}

	@Nonnull
	private static List<PsiMember> findByMap(@Nonnull PsiClass aClass, String name, boolean checkBases, @Nonnull MemberType type)
	{
		if(name == null)
		{
			return Collections.emptyList();
		}

		if(checkBases)
		{
			PsiMember[] list = getMap(aClass, type).get(name);
			if(list == null)
			{
				return Collections.emptyList();
			}
			return Arrays.asList(list);
		}
		else
		{
			PsiMember[] members = null;
			switch(type)
			{
				case METHOD:
					members = aClass.getMethods();
					break;
				case CLASS:
					members = aClass.getInnerClasses();
					break;
				case FIELD:
					members = aClass.getFields();
					break;
			}

			List<PsiMember> list = new ArrayList<PsiMember>();
			for(PsiMember member : members)
			{
				if(name.equals(member.getName()))
				{
					list.add(member);
				}
			}
			return list;
		}
	}

	@Nonnull
	public static <T extends PsiMember> List<Pair<T, PsiSubstitutor>> getAllWithSubstitutorsByMap(@Nonnull PsiClass aClass, @Nonnull MemberType type)
	{
		return withSubstitutors(aClass, getMap(aClass, type).get(ALL));
	}

	@Nonnull
	private static <T extends PsiMember> List<T> getAllByMap(@Nonnull PsiClass aClass, @Nonnull MemberType type)
	{
		List<Pair<T, PsiSubstitutor>> pairs = getAllWithSubstitutorsByMap(aClass, type);

		final List<T> ret = new ArrayList<T>(pairs.size());
		//noinspection ForLoopReplaceableByForEach
		for(int i = 0; i < pairs.size(); i++)
		{
			Pair<T, PsiSubstitutor> pair = pairs.get(i);
			T t = pair.getFirst();
			LOG.assertTrue(t != null, aClass);
			ret.add(t);
		}
		return ret;
	}

	@NonNls
	private static final String ALL = "Intellij-IDEA-ALL";

	public enum MemberType
	{
		CLASS,
		FIELD,
		METHOD
	}

	private static Map<String, PsiMember[]> getMap(@Nonnull PsiClass aClass, @Nonnull MemberType type)
	{
		ParameterizedCachedValue<Map<GlobalSearchScope, MembersMap>, PsiClass> value = getValues(aClass);
		return value.getValue(aClass).get(aClass.getResolveScope()).get(type);
	}

	@Nonnull
	private static ParameterizedCachedValue<Map<GlobalSearchScope, MembersMap>, PsiClass> getValues(@Nonnull PsiClass aClass)
	{
		ParameterizedCachedValue<Map<GlobalSearchScope, MembersMap>, PsiClass> value = aClass.getUserData(MAP_IN_CLASS_KEY);
		if(value == null)
		{
			value = CachedValuesManager.getManager(aClass.getProject()).createParameterizedCachedValue(ByNameCachedValueProvider.INSTANCE, false);
			//Do not cache for nonphysical elements
			if(aClass.isPhysical())
			{
				value = ((UserDataHolderEx) aClass).putUserDataIfAbsent(MAP_IN_CLASS_KEY, value);
			}
		}
		return value;
	}

	private static class ClassIconRequest
	{
		@Nonnull
		private final PsiClass psiClass;
		private final int flags;
		private final Icon symbolIcon;

		private ClassIconRequest(@Nonnull PsiClass psiClass, int flags, Icon symbolIcon)
		{
			this.psiClass = psiClass;
			this.flags = flags;
			this.symbolIcon = symbolIcon;
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
			{
				return true;
			}
			if(!(o instanceof ClassIconRequest))
			{
				return false;
			}

			ClassIconRequest that = (ClassIconRequest) o;

			return flags == that.flags && psiClass.equals(that.psiClass);
		}

		@Override
		public int hashCode()
		{
			int result = psiClass.hashCode();
			result = 31 * result + flags;
			return result;
		}
	}

	@Nonnull
	public static SearchScope getClassUseScope(@Nonnull PsiClass aClass)
	{
		if(aClass instanceof PsiAnonymousClass)
		{
			return new LocalSearchScope(aClass);
		}
		final GlobalSearchScope maximalUseScope = ResolveScopeManager.getElementUseScope(aClass);
		PsiFile file = aClass.getContainingFile();
		if(PsiImplUtil.isInServerPage(file))
		{
			return maximalUseScope;
		}
		final PsiClass containingClass = aClass.getContainingClass();
		if(aClass.hasModifierProperty(PsiModifier.PUBLIC) || aClass.hasModifierProperty(PsiModifier.PROTECTED))
		{
			return containingClass == null ? maximalUseScope : containingClass.getUseScope();
		}
		else if(aClass.hasModifierProperty(PsiModifier.PRIVATE) || aClass instanceof PsiTypeParameter)
		{
			PsiClass topClass = PsiUtil.getTopLevelClass(aClass);
			return new LocalSearchScope(topClass == null ? aClass.getContainingFile() : topClass);
		}
		else
		{
			PsiJavaPackage aPackage = null;
			if(file instanceof PsiJavaFile)
			{
				aPackage = JavaPsiFacade.getInstance(aClass.getProject()).findPackage(((PsiJavaFile) file).getPackageName());
			}

			if(aPackage == null)
			{
				PsiDirectory dir = file.getContainingDirectory();
				if(dir != null)
				{
					aPackage = JavaDirectoryService.getInstance().getPackage(dir);
				}
			}

			if(aPackage != null)
			{
				SearchScope scope = PackageScope.packageScope(aPackage, false);
				scope = scope.intersectWith(maximalUseScope);
				return scope;
			}

			return new LocalSearchScope(file);
		}
	}

	public static boolean isMainOrPremainMethod(@Nonnull PsiMethod method)
	{
		if(!PsiType.VOID.equals(method.getReturnType()))
		{
			return false;
		}
		String name = method.getName();
		if(!("main".equals(name) || "premain".equals(name) || "agentmain".equals(name)))
		{
			return false;
		}

		PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
		MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
		try
		{
			MethodSignature main = createSignatureFromText(factory, "void main(String[] args);");
			if(MethodSignatureUtil.areSignaturesEqual(signature, main))
			{
				return true;
			}
			MethodSignature premain = createSignatureFromText(factory, "void premain(String args, java.lang.instrument.Instrumentation i);");
			if(MethodSignatureUtil.areSignaturesEqual(signature, premain))
			{
				return true;
			}
			MethodSignature agentmain = createSignatureFromText(factory, "void agentmain(String args, java.lang.instrument.Instrumentation i);");
			if(MethodSignatureUtil.areSignaturesEqual(signature, agentmain))
			{
				return true;
			}
		}
		catch(IncorrectOperationException e)
		{
			LOG.error(e);
		}

		return false;
	}

	@Nonnull
	private static MethodSignature createSignatureFromText(@Nonnull PsiElementFactory factory, @Nonnull String text)
	{
		return factory.createMethodFromText(text, null).getSignature(PsiSubstitutor.EMPTY);
	}

	private static class MembersMap
	{
		final ConcurrentMap<MemberType, Map<String, PsiMember[]>> myMap;

		MembersMap(PsiClass psiClass, GlobalSearchScope scope)
		{
			myMap = createMembersMap(psiClass, scope);
		}

		private Map<String, PsiMember[]> get(MemberType type)
		{
			return myMap.get(type);
		}
	}

	private static ConcurrentMap<MemberType, Map<String, PsiMember[]>> createMembersMap(PsiClass psiClass, GlobalSearchScope scope)
	{
		return ConcurrentFactoryMap.createMap(key -> {
			final Map<String, List<PsiMember>> map = new HashMap<>();

			final List<PsiMember> allMembers = new ArrayList<>();
			map.put(ALL, allMembers);

			ElementClassFilter filter = key == MemberType.CLASS ? ElementClassFilter.CLASS :
					key == MemberType.METHOD ? ElementClassFilter.METHOD : ElementClassFilter.FIELD;
			final ElementClassHint classHint = kind -> key == MemberType.CLASS && kind == ElementClassHint.DeclarationKind.CLASS ||
					key == MemberType.FIELD && (kind == ElementClassHint.DeclarationKind.FIELD || kind == ElementClassHint.DeclarationKind.ENUM_CONST) ||
					key == MemberType.METHOD && kind == ElementClassHint.DeclarationKind.METHOD;
			FilterScopeProcessor<MethodCandidateInfo> processor = new FilterScopeProcessor<MethodCandidateInfo>(filter)
			{
				@Override
				protected void add(@Nonnull PsiElement element, @Nonnull PsiSubstitutor substitutor)
				{
					if(key == MemberType.CLASS && element instanceof PsiClass || key == MemberType.METHOD && element instanceof PsiMethod || key == MemberType.FIELD && element instanceof PsiField)
					{
						PsiUtilCore.ensureValid(element);
						allMembers.add((PsiMember) element);
						String currentName = ((PsiMember) element).getName();
						List<PsiMember> listByName = map.computeIfAbsent(currentName, __ -> ContainerUtil.newSmartList());
						listByName.add((PsiMember) element);
					}
				}

				@Override
				public <K> K getHint(@Nonnull Key<K> hintKey)
				{
					//noinspection unchecked
					return ElementClassHint.KEY == hintKey ? (K) classHint : super.getHint(hintKey);
				}
			};

			processDeclarationsInClassNotCached(psiClass, processor, ResolveState.initial(), null, null, psiClass, false, PsiUtil.getLanguageLevel(psiClass), scope);
			Map<String, PsiMember[]> result = new HashMap<>();
			for(Map.Entry<String, List<PsiMember>> entry : map.entrySet())
			{
				result.put(entry.getKey(), entry.getValue().toArray(PsiMember.EMPTY_ARRAY));
			}
			return result;
		});
	}

	private static class ByNameCachedValueProvider implements ParameterizedCachedValueProvider<Map<GlobalSearchScope, MembersMap>, PsiClass>
	{
		private static final ByNameCachedValueProvider INSTANCE = new ByNameCachedValueProvider();

		@Override
		public CachedValueProvider.Result<Map<GlobalSearchScope, MembersMap>> compute(@Nonnull final PsiClass myClass)
		{
			final Map<GlobalSearchScope, MembersMap> map = ConcurrentFactoryMap.createMap(scope -> new MembersMap(myClass, scope));
			return CachedValueProvider.Result.create(map, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
		}
	}

	public static boolean processDeclarationsInClass(@Nonnull PsiClass aClass,
													 @Nonnull final PsiScopeProcessor processor,
													 @Nonnull ResolveState state,
													 @javax.annotation.Nullable Set<PsiClass> visited,
													 PsiElement last,
													 @Nonnull PsiElement place,
													 @Nonnull LanguageLevel languageLevel,
													 boolean isRaw)
	{
		return processDeclarationsInClass(aClass, processor, state, visited, last, place, languageLevel, isRaw, place.getResolveScope());
	}

	private static boolean processDeclarationsInClass(@Nonnull PsiClass aClass,
													  @Nonnull final PsiScopeProcessor processor,
													  @Nonnull ResolveState state,
													  @javax.annotation.Nullable Set<PsiClass> visited,
													  PsiElement last,
													  @Nonnull PsiElement place,
													  @Nonnull LanguageLevel languageLevel,
													  boolean isRaw,
													  @Nonnull GlobalSearchScope resolveScope)
	{
		if(last instanceof PsiTypeParameterList || last instanceof PsiModifierList && aClass.getModifierList() == last)
		{
			return true;
		}
		if(visited != null && visited.contains(aClass))
		{
			return true;
		}

		PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
		isRaw = isRaw || PsiUtil.isRawSubstitutor(aClass, substitutor);

		final NameHint nameHint = processor.getHint(NameHint.KEY);
		if(nameHint != null)
		{
			String name = nameHint.getName(state);
			return processCachedMembersByName(aClass, processor, state, visited, last, place, isRaw, substitutor, getValues(aClass).getValue(aClass).get(resolveScope), name, languageLevel);
		}
		return processDeclarationsInClassNotCached(aClass, processor, state, visited, last, place, isRaw, languageLevel, resolveScope);
	}

	private static boolean processCachedMembersByName(@Nonnull final PsiClass aClass,
													  @Nonnull PsiScopeProcessor processor,
													  @Nonnull ResolveState state,
													  @javax.annotation.Nullable Set<PsiClass> visited,
													  PsiElement last,
													  @Nonnull final PsiElement place,
													  final boolean isRaw,
													  @Nonnull final PsiSubstitutor substitutor,
													  @Nonnull MembersMap value,
													  String name,
													  @Nonnull final LanguageLevel languageLevel)
	{
		Function<PsiMember, PsiSubstitutor> finalSubstitutor = new Function<PsiMember, PsiSubstitutor>()
		{
			final ScopedClassHierarchy hierarchy = ScopedClassHierarchy.getHierarchy(aClass, place.getResolveScope());
			final PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();

			@Override
			public PsiSubstitutor fun(PsiMember member)
			{
				PsiClass containingClass = ObjectUtil.assertNotNull(member.getContainingClass());
				PsiSubstitutor superSubstitutor = hierarchy.getSuperMembersSubstitutor(containingClass, languageLevel);
				PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(containingClass, superSubstitutor == null ? PsiSubstitutor.EMPTY : superSubstitutor, aClass, substitutor, factory,
						languageLevel);
				return member instanceof PsiMethod ? checkRaw(isRaw, factory, (PsiMethod) member, finalSubstitutor) : finalSubstitutor;
			}
		};

		final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);

		if(classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.FIELD))
		{
			final PsiField fieldByName = aClass.findFieldByName(name, false);
			if(fieldByName != null)
			{
				processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
				if(!processor.execute(fieldByName, state))
				{
					return false;
				}
			}
			else
			{
				final Map<String, PsiMember[]> allFieldsMap = value.get(MemberType.FIELD);

				final PsiMember[] list = allFieldsMap.get(name);
				if(list != null)
				{
					boolean resolved = false;
					for(final PsiMember candidateField : list)
					{
						PsiClass containingClass = candidateField.getContainingClass();
						if(containingClass == null)
						{
							LOG.error("No class for field " + candidateField.getName() + " of " + candidateField.getClass());
							continue;
						}

						processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
						if(!processor.execute(candidateField, state.put(PsiSubstitutor.KEY, finalSubstitutor.fun(candidateField))))
						{
							resolved = true;
						}
					}
					if(resolved)
					{
						return false;
					}
				}
			}
		}
		if(classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS))
		{
			if(last != null && last.getContext() == aClass)
			{
				if(last instanceof PsiClass)
				{
					if(!processor.execute(last, state))
					{
						return false;
					}
				}
				// Parameters
				final PsiTypeParameterList list = aClass.getTypeParameterList();
				if(list != null && !list.processDeclarations(processor, state, last, place))
				{
					return false;
				}
			}
			if(!(last instanceof PsiReferenceList))
			{
				final PsiClass classByName = aClass.findInnerClassByName(name, false);
				if(classByName != null)
				{
					processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
					if(!processor.execute(classByName, state))
					{
						return false;
					}
				}
				else
				{
					Map<String, PsiMember[]> allClassesMap = value.get(MemberType.CLASS);

					PsiMember[] list = allClassesMap.get(name);
					if(list != null)
					{
						boolean resolved = false;
						for(final PsiMember inner : list)
						{
							PsiClass containingClass = inner.getContainingClass();
							if(containingClass != null)
							{
								processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
								if(!processor.execute(inner, state.put(PsiSubstitutor.KEY, finalSubstitutor.fun(inner))))
								{
									resolved = true;
								}
							}
						}
						if(resolved)
						{
							return false;
						}
					}
				}
			}
		}
		if(classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.METHOD))
		{
			if(processor instanceof MethodResolverProcessor)
			{
				final MethodResolverProcessor methodResolverProcessor = (MethodResolverProcessor) processor;
				if(methodResolverProcessor.isConstructor())
				{
					final PsiMethod[] constructors = aClass.getConstructors();
					methodResolverProcessor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
					for(PsiMethod constructor : constructors)
					{
						if(!methodResolverProcessor.execute(constructor, state))
						{
							return false;
						}
					}
					return true;
				}
			}
			Map<String, PsiMember[]> allMethodsMap = value.get(MemberType.METHOD);
			PsiMember[] list = allMethodsMap.get(name);
			if(list != null)
			{
				boolean resolved = false;
				for(final PsiMember candidate : list)
				{
					ProgressIndicatorProvider.checkCanceled();
					PsiMethod candidateMethod = (PsiMethod) candidate;
					if(processor instanceof MethodResolverProcessor)
					{
						if(candidateMethod.isConstructor() != ((MethodResolverProcessor) processor).isConstructor())
						{
							continue;
						}
					}
					final PsiClass containingClass = candidateMethod.getContainingClass();
					if(containingClass == null || visited != null && visited.contains(containingClass))
					{
						continue;
					}

					processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
					if(!processor.execute(candidateMethod, state.put(PsiSubstitutor.KEY, finalSubstitutor.fun(candidateMethod))))
					{
						resolved = true;
					}
				}
				if(resolved)
				{
					return false;
				}

				if(visited != null)
				{
					for(PsiMember aList : list)
					{
						visited.add(aList.getContainingClass());
					}
				}
			}
		}
		return true;
	}

	private static PsiSubstitutor checkRaw(boolean isRaw, @Nonnull PsiElementFactory factory, @Nonnull PsiMethod candidateMethod, @Nonnull PsiSubstitutor substitutor)
	{
		//4.8-2. Raw Types and Inheritance
		//certain members of a raw type are not erased,
		//namely static members whose types are parameterized, and members inherited from a non-generic supertype.
		if(isRaw && !candidateMethod.hasModifierProperty(PsiModifier.STATIC))
		{
			final PsiClass containingClass = candidateMethod.getContainingClass();
			if(containingClass != null && containingClass.hasTypeParameters())
			{
				PsiTypeParameter[] methodTypeParameters = candidateMethod.getTypeParameters();
				substitutor = factory.createRawSubstitutor(substitutor, methodTypeParameters);
			}
		}
		return substitutor;
	}

	public static PsiSubstitutor obtainFinalSubstitutor(@Nonnull PsiClass candidateClass,
														@Nonnull PsiSubstitutor candidateSubstitutor,
														@Nonnull PsiClass aClass,
														@Nonnull PsiSubstitutor substitutor,
														@Nonnull PsiElementFactory elementFactory,
														@Nonnull LanguageLevel languageLevel)
	{
		if(PsiUtil.isRawSubstitutor(aClass, substitutor))
		{
			return elementFactory.createRawSubstitutor(candidateClass).putAll(substitutor);
		}
		final PsiType containingType = elementFactory.createType(candidateClass, candidateSubstitutor, languageLevel);
		PsiType type = substitutor.substitute(containingType);
		if(!(type instanceof PsiClassType))
		{
			return candidateSubstitutor;
		}
		return ((PsiClassType) type).resolveGenerics().getSubstitutor();
	}

	private static boolean processDeclarationsInClassNotCached(@Nonnull PsiClass aClass,
															   @Nonnull final PsiScopeProcessor processor,
															   @Nonnull final ResolveState state,
															   @javax.annotation.Nullable Set<PsiClass> visited,
															   final PsiElement last,
															   @Nonnull final PsiElement place,
															   final boolean isRaw,
															   @Nonnull final LanguageLevel languageLevel,
															   @Nonnull final GlobalSearchScope resolveScope)
	{
		if(visited == null)
		{
			visited = new HashSet<PsiClass>();
		}
		if(!visited.add(aClass))
		{
			return true;
		}
		processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
		final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
		final NameHint nameHint = processor.getHint(NameHint.KEY);


		if(classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.FIELD))
		{
			if(nameHint != null)
			{
				final PsiField fieldByName = aClass.findFieldByName(nameHint.getName(state), false);
				if(fieldByName != null && !processor.execute(fieldByName, state))
				{
					return false;
				}
			}
			else
			{
				final PsiField[] fields = aClass.getFields();
				for(final PsiField field : fields)
				{
					if(!processor.execute(field, state))
					{
						return false;
					}
				}
			}
		}

		PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();

		if(classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.METHOD))
		{
			PsiSubstitutor baseSubstitutor = state.get(PsiSubstitutor.KEY);
			final PsiMethod[] methods = nameHint != null ? aClass.findMethodsByName(nameHint.getName(state), false) : aClass.getMethods();
			for(final PsiMethod method : methods)
			{
				PsiSubstitutor finalSubstitutor = checkRaw(isRaw, factory, method, baseSubstitutor);
				ResolveState methodState = finalSubstitutor == baseSubstitutor ? state : state.put(PsiSubstitutor.KEY, finalSubstitutor);
				if(!processor.execute(method, methodState))
				{
					return false;
				}
			}
		}

		if(classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS))
		{
			if(last != null && last.getContext() == aClass)
			{
				// Parameters
				final PsiTypeParameterList list = aClass.getTypeParameterList();
				if(list != null && !list.processDeclarations(processor, ResolveState.initial(), last, place))
				{
					return false;
				}
			}

			if(!(last instanceof PsiReferenceList) && !(last instanceof PsiModifierList))
			{
				// Inners
				if(nameHint != null)
				{
					final PsiClass inner = aClass.findInnerClassByName(nameHint.getName(state), false);
					if(inner != null)
					{
						if(!processor.execute(inner, state))
						{
							return false;
						}
					}
				}
				else
				{
					final PsiClass[] inners = aClass.getInnerClasses();
					for(final PsiClass inner : inners)
					{
						if(!processor.execute(inner, state))
						{
							return false;
						}
					}
				}
			}
		}

		if(last instanceof PsiReferenceList)
		{
			return true;
		}

		final Set<PsiClass> visited1 = visited;
		return processSuperTypes(aClass, state.get(PsiSubstitutor.KEY), factory, languageLevel, resolveScope, new PairProcessor<PsiClass, PsiSubstitutor>()
		{
			@Override
			public boolean process(PsiClass superClass, PsiSubstitutor finalSubstitutor)
			{
				return processDeclarationsInClass(superClass, processor, state.put(PsiSubstitutor.KEY, finalSubstitutor), visited1, last, place, languageLevel, isRaw, resolveScope);
			}
		});
	}

	@javax.annotation.Nullable
	public static <T extends PsiType> T correctType(@javax.annotation.Nullable final T originalType, @Nonnull final GlobalSearchScope resolveScope)
	{
		if(originalType == null || !JAVA_CORRECT_CLASS_TYPE_BY_PLACE_RESOLVE_SCOPE)
		{
			return originalType;
		}

		return new TypeCorrector(resolveScope).correctType(originalType);
	}

	public static List<PsiClassType.ClassResolveResult> getScopeCorrectedSuperTypes(final PsiClass aClass, GlobalSearchScope resolveScope)
	{
		return ScopedClassHierarchy.getHierarchy(aClass, resolveScope).getImmediateSupersWithCapturing();
	}

	static boolean processSuperTypes(@Nonnull PsiClass aClass,
									 PsiSubstitutor substitutor,
									 @Nonnull PsiElementFactory factory,
									 @Nonnull LanguageLevel languageLevel,
									 GlobalSearchScope resolveScope,
									 PairProcessor<PsiClass, PsiSubstitutor> processor)
	{
		boolean resolved = false;
		for(PsiClassType.ClassResolveResult superTypeResolveResult : getScopeCorrectedSuperTypes(aClass, resolveScope))
		{
			PsiClass superClass = superTypeResolveResult.getElement();
			assert superClass != null;
			PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(superClass, superTypeResolveResult.getSubstitutor(), aClass, substitutor, factory, languageLevel);
			if(!processor.process(superClass, finalSubstitutor))
			{
				resolved = true;
			}
		}
		return !resolved;
	}

	@javax.annotation.Nullable
	public static PsiClass getSuperClass(@Nonnull PsiClass psiClass)
	{
		if(psiClass.isInterface())
		{
			return findSpecialSuperClass(psiClass, JavaClassNames.JAVA_LANG_OBJECT);
		}
		if(psiClass.isEnum())
		{
			return findSpecialSuperClass(psiClass, JavaClassNames.JAVA_LANG_ENUM);
		}

		if(psiClass instanceof PsiAnonymousClass)
		{
			PsiClassType baseClassReference = ((PsiAnonymousClass) psiClass).getBaseClassType();
			PsiClass baseClass = baseClassReference.resolve();
			if(baseClass == null || baseClass.isInterface())
			{
				return findSpecialSuperClass(psiClass, JavaClassNames.JAVA_LANG_OBJECT);
			}
			return baseClass;
		}

		if(JavaClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName()))
		{
			return null;
		}

		final PsiClassType[] referenceElements = psiClass.getExtendsListTypes();

		if(referenceElements.length == 0)
		{
			return findSpecialSuperClass(psiClass, JavaClassNames.JAVA_LANG_OBJECT);
		}

		PsiClass psiResolved = referenceElements[0].resolve();
		return psiResolved == null ? findSpecialSuperClass(psiClass, JavaClassNames.JAVA_LANG_OBJECT) : psiResolved;
	}

	@javax.annotation.Nullable
	private static PsiClass findSpecialSuperClass(@Nonnull PsiClass psiClass, String className)
	{
		return JavaPsiFacade.getInstance(psiClass.getProject()).findClass(className, psiClass.getResolveScope());
	}

	@Nonnull
	public static PsiClass[] getSupers(@Nonnull PsiClass psiClass)
	{
		final PsiClass[] supers = getSupersInner(psiClass);
		for(final PsiClass aSuper : supers)
		{
			LOG.assertTrue(aSuper != null);
		}
		return supers;
	}

	@Nonnull
	private static PsiClass[] getSupersInner(@Nonnull PsiClass psiClass)
	{
		PsiClassType[] extendsListTypes = psiClass.getExtendsListTypes();

		if(psiClass.isInterface())
		{
			return resolveClassReferenceList(extendsListTypes, psiClass, true);
		}

		if(psiClass instanceof PsiAnonymousClass)
		{
			PsiAnonymousClass psiAnonymousClass = (PsiAnonymousClass) psiClass;
			PsiClassType baseClassReference = psiAnonymousClass.getBaseClassType();
			PsiClass baseClass = baseClassReference.resolve();
			if(baseClass != null)
			{
				if(baseClass.isInterface())
				{
					PsiClass objectClass = findSpecialSuperClass(psiClass, JavaClassNames.JAVA_LANG_OBJECT);
					return objectClass != null ? new PsiClass[]{
							objectClass,
							baseClass
					} : new PsiClass[]{baseClass};
				}
				return new PsiClass[]{baseClass};
			}

			PsiClass objectClass = findSpecialSuperClass(psiClass, JavaClassNames.JAVA_LANG_OBJECT);
			return objectClass != null ? new PsiClass[]{objectClass} : PsiClass.EMPTY_ARRAY;
		}
		if(psiClass instanceof PsiTypeParameter)
		{
			if(extendsListTypes.length == 0)
			{
				final PsiClass objectClass = findSpecialSuperClass(psiClass, JavaClassNames.JAVA_LANG_OBJECT);
				return objectClass != null ? new PsiClass[]{objectClass} : PsiClass.EMPTY_ARRAY;
			}
			return resolveClassReferenceList(extendsListTypes, psiClass, false);
		}

		PsiClassType[] implementsListTypes = psiClass.getImplementsListTypes();
		PsiClass[] interfaces = resolveClassReferenceList(implementsListTypes, psiClass, false);

		PsiClass superClass = getSuperClass(psiClass);
		if(superClass == null)
		{
			return interfaces;
		}
		PsiClass[] types = new PsiClass[interfaces.length + 1];
		types[0] = superClass;
		System.arraycopy(interfaces, 0, types, 1, interfaces.length);

		return types;
	}

	@Nonnull
	public static PsiClassType[] getSuperTypes(@Nonnull PsiClass psiClass)
	{
		if(psiClass instanceof PsiAnonymousClass)
		{
			PsiClassType baseClassType = ((PsiAnonymousClass) psiClass).getBaseClassType();
			PsiClass baseClass = baseClassType.resolve();
			if(baseClass == null || !baseClass.isInterface())
			{
				return new PsiClassType[]{baseClassType};
			}
			else
			{
				PsiClassType objectType = PsiType.getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope());
				return new PsiClassType[]{
						objectType,
						baseClassType
				};
			}
		}

		PsiClassType[] extendsTypes = psiClass.getExtendsListTypes();
		PsiClassType[] implementsTypes = psiClass.getImplementsListTypes();
		boolean hasExtends = extendsTypes.length != 0;
		int extendsListLength = extendsTypes.length + (hasExtends ? 0 : 1);
		PsiClassType[] result = new PsiClassType[extendsListLength + implementsTypes.length];

		System.arraycopy(extendsTypes, 0, result, 0, extendsTypes.length);
		if(!hasExtends)
		{
			if(JavaClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName()))
			{
				return PsiClassType.EMPTY_ARRAY;
			}
			PsiManager manager = psiClass.getManager();
			PsiClassType objectType = PsiType.getJavaLangObject(manager, psiClass.getResolveScope());
			result[0] = objectType;
		}
		System.arraycopy(implementsTypes, 0, result, extendsListLength, implementsTypes.length);
		return result;
	}

	@Nonnull
	private static PsiClassType getAnnotationSuperType(@Nonnull PsiClass psiClass, @Nonnull PsiElementFactory factory)
	{
		return factory.createTypeByFQClassName(JavaClassNames.JAVA_LANG_ANNOTATION_ANNOTATION, psiClass.getResolveScope());
	}

	private static PsiClassType getEnumSuperType(@Nonnull PsiClass psiClass, @Nonnull PsiElementFactory factory)
	{
		PsiClassType superType;
		final PsiClass enumClass = findSpecialSuperClass(psiClass, JavaClassNames.JAVA_LANG_ENUM);
		if(enumClass == null)
		{
			try
			{
				superType = (PsiClassType) factory.createTypeFromText(JavaClassNames.JAVA_LANG_ENUM, null);
			}
			catch(IncorrectOperationException e)
			{
				superType = null;
			}
		}
		else
		{
			final PsiTypeParameter[] typeParameters = enumClass.getTypeParameters();
			PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
			if(typeParameters.length == 1)
			{
				substitutor = substitutor.put(typeParameters[0], factory.createType(psiClass));
			}
			superType = new PsiImmediateClassType(enumClass, substitutor);
		}
		return superType;
	}

	@Nonnull
	public static PsiClass[] getInterfaces(@Nonnull PsiTypeParameter typeParameter)
	{
		final PsiClassType[] referencedTypes = typeParameter.getExtendsListTypes();
		if(referencedTypes.length == 0)
		{
			return PsiClass.EMPTY_ARRAY;
		}
		final List<PsiClass> result = new ArrayList<PsiClass>(referencedTypes.length);
		for(PsiClassType referencedType : referencedTypes)
		{
			final PsiClass psiClass = referencedType.resolve();
			if(psiClass != null && psiClass.isInterface())
			{
				result.add(psiClass);
			}
		}
		return result.toArray(new PsiClass[result.size()]);
	}

	@Nonnull
	public static PsiClass[] getInterfaces(@Nonnull PsiClass psiClass)
	{
		if(psiClass.isInterface())
		{
			return resolveClassReferenceList(psiClass.getExtendsListTypes(), psiClass, false);
		}

		if(psiClass instanceof PsiAnonymousClass)
		{
			PsiClassType baseClassReference = ((PsiAnonymousClass) psiClass).getBaseClassType();
			PsiClass baseClass = baseClassReference.resolve();
			return baseClass != null && baseClass.isInterface() ? new PsiClass[]{baseClass} : PsiClass.EMPTY_ARRAY;
		}

		final PsiClassType[] implementsListTypes = psiClass.getImplementsListTypes();
		return resolveClassReferenceList(implementsListTypes, psiClass, false);
	}

	@Nonnull
	private static PsiClass[] resolveClassReferenceList(@Nonnull PsiClassType[] listOfTypes, @Nonnull PsiClass psiClass, boolean includeObject)
	{
		PsiClass objectClass = null;
		if(includeObject)
		{
			objectClass = findSpecialSuperClass(psiClass, JavaClassNames.JAVA_LANG_OBJECT);
			if(objectClass == null)
			{
				includeObject = false;
			}
		}
		if(listOfTypes.length == 0)
		{
			if(includeObject)
			{
				return new PsiClass[]{objectClass};
			}
			return PsiClass.EMPTY_ARRAY;
		}

		int referenceCount = listOfTypes.length;
		if(includeObject)
		{
			referenceCount++;
		}

		PsiClass[] resolved = new PsiClass[referenceCount];
		int resolvedCount = 0;

		if(includeObject)
		{
			resolved[resolvedCount++] = objectClass;
		}
		for(PsiClassType reference : listOfTypes)
		{
			PsiClass refResolved = reference.resolve();
			if(refResolved != null)
			{
				resolved[resolvedCount++] = refResolved;
			}
		}

		if(resolvedCount < referenceCount)
		{
			PsiClass[] shorter = new PsiClass[resolvedCount];
			System.arraycopy(resolved, 0, shorter, 0, resolvedCount);
			resolved = shorter;
		}

		return resolved;
	}

	@Nonnull
	public static List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@Nonnull PsiClass psiClass, String name, boolean checkBases)
	{
		if(!checkBases)
		{
			final PsiMethod[] methodsByName = psiClass.findMethodsByName(name, false);
			final List<Pair<PsiMethod, PsiSubstitutor>> ret = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>(methodsByName.length);
			for(final PsiMethod method : methodsByName)
			{
				ret.add(Pair.create(method, PsiSubstitutor.EMPTY));
			}
			return ret;
		}
		PsiMember[] list = getMap(psiClass, MemberType.METHOD).get(name);
		if(list == null)
		{
			return Collections.emptyList();
		}
		return withSubstitutors(psiClass, list);
	}

	@Nonnull
	private static <T extends PsiMember> List<Pair<T, PsiSubstitutor>> withSubstitutors(@Nonnull final PsiClass psiClass, PsiMember[] members)
	{
		final ScopedClassHierarchy hierarchy = ScopedClassHierarchy.getHierarchy(psiClass, psiClass.getResolveScope());
		final LanguageLevel level = PsiUtil.getLanguageLevel(psiClass);
		return ContainerUtil.map(members, new Function<PsiMember, Pair<T, PsiSubstitutor>>()
		{
			@Override
			public Pair<T, PsiSubstitutor> fun(PsiMember member)
			{
				PsiClass containingClass = member.getContainingClass();
				PsiSubstitutor substitutor = containingClass == null ? null : hierarchy.getSuperMembersSubstitutor(containingClass, level);
				//noinspection unchecked
				return Pair.create((T) member, substitutor == null ? PsiSubstitutor.EMPTY : substitutor);
			}
		});
	}

	@Nonnull
	public static PsiClassType[] getExtendsListTypes(@Nonnull PsiClass psiClass)
	{
		if(psiClass.isEnum())
		{
			PsiClassType enumSuperType = getEnumSuperType(psiClass, JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory());
			return enumSuperType == null ? PsiClassType.EMPTY_ARRAY : new PsiClassType[]{enumSuperType};
		}
		if(psiClass.isAnnotationType())
		{
			return new PsiClassType[]{getAnnotationSuperType(psiClass, JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory())};
		}
		PsiType upperBound = InferenceSession.getUpperBound(psiClass);
		if(upperBound == null && psiClass instanceof PsiTypeParameter)
		{
			upperBound = LambdaUtil.getFunctionalTypeMap().get(psiClass);
		}
		if(upperBound instanceof PsiIntersectionType)
		{
			final PsiType[] conjuncts = ((PsiIntersectionType) upperBound).getConjuncts();
			final List<PsiClassType> result = new ArrayList<PsiClassType>();
			for(PsiType conjunct : conjuncts)
			{
				if(conjunct instanceof PsiClassType)
				{
					result.add((PsiClassType) conjunct);
				}
			}
			return result.toArray(new PsiClassType[result.size()]);
		}
		if(upperBound instanceof PsiClassType)
		{
			return new PsiClassType[]{(PsiClassType) upperBound};
		}
		final PsiReferenceList extendsList = psiClass.getExtendsList();
		if(extendsList != null)
		{
			return extendsList.getReferencedTypes();
		}
		return PsiClassType.EMPTY_ARRAY;
	}

	@Nonnull
	public static PsiClassType[] getImplementsListTypes(@Nonnull PsiClass psiClass)
	{
		final PsiReferenceList extendsList = psiClass.getImplementsList();
		if(extendsList != null)
		{
			return extendsList.getReferencedTypes();
		}
		return PsiClassType.EMPTY_ARRAY;
	}

	static boolean isInExtendsList(@Nonnull PsiClass psiClass, @Nonnull PsiClass baseClass, @javax.annotation.Nullable String baseName, @Nonnull PsiManager manager)
	{
		if(psiClass.isEnum())
		{
			return JavaClassNames.JAVA_LANG_ENUM.equals(baseClass.getQualifiedName());
		}
		if(psiClass.isAnnotationType())
		{
			return JavaClassNames.JAVA_LANG_ANNOTATION_ANNOTATION.equals(baseClass.getQualifiedName());
		}
		PsiType upperBound = InferenceSession.getUpperBound(psiClass);
		if(upperBound == null && psiClass instanceof PsiTypeParameter)
		{
			upperBound = LambdaUtil.getFunctionalTypeMap().get(psiClass);
		}
		if(upperBound instanceof PsiIntersectionType)
		{
			final PsiType[] conjuncts = ((PsiIntersectionType) upperBound).getConjuncts();
			for(PsiType conjunct : conjuncts)
			{
				if(conjunct instanceof PsiClassType && ((PsiClassType) conjunct).getClassName().equals(baseName) && baseClass.equals(((PsiClassType) conjunct).resolve()))
				{
					return true;
				}
			}
			return false;
		}
		if(upperBound instanceof PsiClassType)
		{
			return ((PsiClassType) upperBound).getClassName().equals(baseName) && baseClass.equals(((PsiClassType) upperBound).resolve());
		}

		return isInReferenceList(psiClass.getExtendsList(), baseClass, baseName, manager);
	}

	static boolean isInReferenceList(@javax.annotation.Nullable PsiReferenceList list, @Nonnull PsiClass baseClass, @javax.annotation.Nullable String baseName, @Nonnull PsiManager manager)
	{
		if(list == null)
		{
			return false;
		}
		if(list instanceof StubBasedPsiElement)
		{
			StubElement stub = ((StubBasedPsiElement) list).getStub();
			if(stub instanceof PsiClassReferenceListStub && baseName != null)
			{
				// classStub.getReferencedNames() is cheaper than getReferencedTypes()
				PsiClassReferenceListStub classStub = (PsiClassReferenceListStub) stub;
				String[] names = classStub.getReferencedNames();
				for(int i = 0; i < names.length; i++)
				{
					String name = names[i];
					int typeParam = name.indexOf('<');
					if(typeParam != -1)
					{
						name = name.substring(0, typeParam);
					}
					// baseName=="ArrayList" classStub.getReferenceNames()[i]=="java.util.ArrayList"
					if(name.endsWith(baseName))
					{
						PsiClassType[] referencedTypes = classStub.getReferencedTypes();
						PsiClass resolved = referencedTypes[i].resolve();
						if(manager.areElementsEquivalent(baseClass, resolved))
						{
							return true;
						}
					}
				}
				return false;
			}
			if(stub != null)
			{
				// groovy etc
				for(PsiClassType type : list.getReferencedTypes())
				{
					if(Comparing.equal(type.getClassName(), baseName) && manager.areElementsEquivalent(baseClass, type.resolve()))
					{
						return true;
					}
				}
				return false;
			}
		}

		if(list.getLanguage() == JavaLanguage.INSTANCE)
		{
			// groovy doesn't have list.getReferenceElements()
			for(PsiJavaCodeReferenceElement referenceElement : list.getReferenceElements())
			{
				if(Comparing.strEqual(baseName, referenceElement.getReferenceName()) && manager.areElementsEquivalent(baseClass, referenceElement.resolve()))
				{
					return true;
				}
			}
			return false;
		}

		for(PsiClassType type : list.getReferencedTypes())
		{
			if(Comparing.equal(type.getClassName(), baseName) && manager.areElementsEquivalent(baseClass, type.resolve()))
			{
				return true;
			}
		}
		return false;
	}

	public static boolean isClassEquivalentTo(@Nonnull PsiClass aClass, PsiElement another)
	{
		if(aClass == another)
		{
			return true;
		}
		if(!(another instanceof PsiClass))
		{
			return false;
		}
		String name1 = aClass.getName();
		if(name1 == null)
		{
			return false;
		}
		if(!another.isValid())
		{
			return false;
		}
		String name2 = ((PsiClass) another).getName();
		if(name2 == null)
		{
			return false;
		}
		if(name1.hashCode() != name2.hashCode())
		{
			return false;
		}
		if(!name1.equals(name2))
		{
			return false;
		}
		String qName1 = aClass.getQualifiedName();
		String qName2 = ((PsiClass) another).getQualifiedName();
		if(qName1 == null || qName2 == null)
		{
			//noinspection StringEquality
			if(qName1 != qName2)
			{
				return false;
			}

			if(aClass instanceof PsiTypeParameter && another instanceof PsiTypeParameter)
			{
				PsiTypeParameter p1 = (PsiTypeParameter) aClass;
				PsiTypeParameter p2 = (PsiTypeParameter) another;

				return p1.getIndex() == p2.getIndex() && (aClass.getManager().areElementsEquivalent(p1.getOwner(), p2.getOwner()) || TypeConversionUtil.areSameFreshVariables(p1, p2));
			}
			else
			{
				return false;
			}
		}
		if(qName1.hashCode() != qName2.hashCode() || !qName1.equals(qName2))
		{
			return false;
		}

		if(aClass.getOriginalElement().equals(another.getOriginalElement()))
		{
			return true;
		}

		final PsiFile file1 = aClass.getContainingFile().getOriginalFile();
		final PsiFile file2 = another.getContainingFile().getOriginalFile();

		//see com.intellij.openapi.vcs.changes.PsiChangeTracker
		//see com.intellij.psi.impl.PsiFileFactoryImpl#createFileFromText(CharSequence,PsiFile)
		final PsiFile original1 = file1.getUserData(PsiFileFactory.ORIGINAL_FILE);
		final PsiFile original2 = file2.getUserData(PsiFileFactory.ORIGINAL_FILE);
		if(original1 == original2 && original1 != null || original1 == file2 || original2 == file1 || file1 == file2)
		{
			return compareClassSeqNumber(aClass, (PsiClass) another);
		}

		final FileIndexFacade fileIndex = ServiceManager.getService(file1.getProject(), FileIndexFacade.class);
		final VirtualFile vfile1 = file1.getViewProvider().getVirtualFile();
		final VirtualFile vfile2 = file2.getViewProvider().getVirtualFile();
		boolean lib1 = fileIndex.isInLibraryClasses(vfile1);
		boolean lib2 = fileIndex.isInLibraryClasses(vfile2);

		return (fileIndex.isInSource(vfile1) || lib1) && (fileIndex.isInSource(vfile2) || lib2);
	}

	private static boolean compareClassSeqNumber(@Nonnull PsiClass aClass, @Nonnull PsiClass another)
	{
		// there may be several classes in one file, they must not be equal
		int index1 = getSeqNumber(aClass);
		if(index1 == -1)
		{
			return true;
		}
		int index2 = getSeqNumber(another);
		return index1 == index2;
	}

	private static int getSeqNumber(@Nonnull PsiClass aClass)
	{
		// sequence number of this class among its parent' child classes named the same
		PsiElement parent = aClass.getParent();
		if(parent == null)
		{
			return -1;
		}
		int seqNo = 0;
		for(PsiElement child : parent.getChildren())
		{
			if(child == aClass)
			{
				return seqNo;
			}
			if(child instanceof PsiClass && Comparing.strEqual(aClass.getName(), ((PsiClass) child).getName()))
			{
				seqNo++;
			}
		}
		return -1;
	}

	public static boolean isFieldEquivalentTo(@Nonnull PsiField field, PsiElement another)
	{
		if(!(another instanceof PsiField))
		{
			return false;
		}
		String name1 = field.getName();
		if(name1 == null)
		{
			return false;
		}
		if(!another.isValid())
		{
			return false;
		}

		String name2 = ((PsiField) another).getName();
		if(!name1.equals(name2))
		{
			return false;
		}
		PsiClass aClass1 = field.getContainingClass();
		PsiClass aClass2 = ((PsiField) another).getContainingClass();
		return aClass1 != null && aClass2 != null && field.getManager().areElementsEquivalent(aClass1, aClass2);
	}

	public static boolean isMethodEquivalentTo(@Nonnull PsiMethod method1, PsiElement another)
	{
		if(method1 == another)
		{
			return true;
		}
		if(!(another instanceof PsiMethod))
		{
			return false;
		}
		PsiMethod method2 = (PsiMethod) another;
		if(!another.isValid())
		{
			return false;
		}
		if(!method1.getName().equals(method2.getName()))
		{
			return false;
		}
		PsiClass aClass1 = method1.getContainingClass();
		PsiClass aClass2 = method2.getContainingClass();
		PsiManager manager = method1.getManager();
		if(!(aClass1 != null && aClass2 != null && manager.areElementsEquivalent(aClass1, aClass2)))
		{
			return false;
		}

		PsiParameter[] parameters1 = method1.getParameterList().getParameters();
		PsiParameter[] parameters2 = method2.getParameterList().getParameters();
		if(parameters1.length != parameters2.length)
		{
			return false;
		}
		for(int i = 0; i < parameters1.length; i++)
		{
			PsiParameter parameter1 = parameters1[i];
			PsiParameter parameter2 = parameters2[i];
			PsiType type1 = parameter1.getType();
			PsiType type2 = parameter2.getType();
			if(!compareParamTypes(manager, type1, type2, new HashSet<String>()))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean compareParamTypes(@Nonnull PsiManager manager, @Nonnull PsiType type1, @Nonnull PsiType type2, Set<String> visited)
	{
		if(type1 instanceof PsiArrayType)
		{
			if(type2 instanceof PsiArrayType)
			{
				final PsiType componentType1 = ((PsiArrayType) type1).getComponentType();
				final PsiType componentType2 = ((PsiArrayType) type2).getComponentType();
				if(compareParamTypes(manager, componentType1, componentType2, visited))
				{
					return true;
				}
			}
			return false;
		}

		if(!(type1 instanceof PsiClassType) || !(type2 instanceof PsiClassType))
		{
			return type1.equals(type2);
		}

		PsiClass class1 = ((PsiClassType) type1).resolve();
		PsiClass class2 = ((PsiClassType) type2).resolve();
		visited.add(type1.getCanonicalText());
		visited.add(type2.getCanonicalText());

		if(class1 instanceof PsiTypeParameter && class2 instanceof PsiTypeParameter)
		{
			if(!(Comparing.equal(class1.getName(), class2.getName()) && ((PsiTypeParameter) class1).getIndex() == ((PsiTypeParameter) class2).getIndex()))
			{
				return false;
			}
			final PsiClassType[] eTypes1 = class1.getExtendsListTypes();
			final PsiClassType[] eTypes2 = class2.getExtendsListTypes();
			if(eTypes1.length != eTypes2.length)
			{
				return false;
			}
			for(int i = 0; i < eTypes1.length; i++)
			{
				PsiClassType eType1 = eTypes1[i];
				PsiClassType eType2 = eTypes2[i];
				if(visited.contains(eType1.getCanonicalText()) || visited.contains(eType2.getCanonicalText()))
				{
					return false;
				}
				if(!compareParamTypes(manager, eType1, eType2, visited))
				{
					return false;
				}
			}
			return true;
		}

		return manager.areElementsEquivalent(class1, class2);
	}
}
