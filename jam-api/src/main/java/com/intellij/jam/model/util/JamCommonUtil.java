/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.jam.model.util;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.jam.*;
import com.intellij.jam.model.common.CommonDomModelElement;
import com.intellij.jam.model.common.CommonModelElement;
import com.intellij.jam.model.common.CommonModelTarget;
import com.intellij.jam.reflect.JamAnnotationAttributeMeta;
import com.intellij.jam.reflect.JamAnnotationMeta;
import com.intellij.jam.reflect.JamClassMeta;
import com.intellij.jam.reflect.JamMemberMeta;
import com.intellij.jam.view.JamDeleteProvider;
import com.intellij.jam.view.tree.JamNodeDescriptor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import consulo.java.jam.util.JamCommonService;
import consulo.java.module.util.JavaClassNames;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.Sets;
import consulo.util.dataholder.Key;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.*;

/**
 * @author Gregory.Shrago
 */
public class JamCommonUtil
{
	@NonNls
	public static final String VALUE_PARAMETER = "value";

	private static final HashingStrategy<PsiClass> HASHING_STRATEGY = new HashingStrategy<PsiClass>()
	{
		public int hashCode(final PsiClass object)
		{
			final String qualifiedName = object.getQualifiedName();
			return qualifiedName == null ? 0 : qualifiedName.hashCode();
		}

		public boolean equals(final PsiClass o1, final PsiClass o2)
		{
			return Comparing.equal(o1.getQualifiedName(), o2.getQualifiedName());
		}
	};

	@Nullable
	public static Object computeMemberValue(@Nullable final PsiElement value)
	{
		if(value == null)
		{
			return null;
		}
		if(value instanceof XmlAttributeValue)
		{
			final GenericAttributeValue genericValue = DomManager.getDomManager(value.getProject()).getDomElement((XmlAttribute) value.getParent());
			return genericValue != null ? genericValue.getValue() : ((XmlAttributeValue) value).getValue();
		}

		try
		{
			return JavaPsiFacade.getInstance(value.getProject()).getConstantEvaluationHelper().computeConstantExpression(value, false);
		}
		catch(UnsupportedOperationException e)
		{
			// nothing
		}

		return null;
	}

	public static boolean isSuperClass(@Nullable final PsiClass firstClass, final String superClassQName)
	{
		return !processSuperClassList(firstClass, new SmartList<PsiClass>(), new Processor<PsiClass>()
		{
			public boolean process(final PsiClass superClass)
			{
				return !Comparing.equal(superClass.getQualifiedName(), superClassQName);
			}
		});
	}

	@Nonnull
	public static List<PsiClass> getSuperClassList(@Nullable final PsiClass firstClass)
	{
		final SmartList<PsiClass> list = new SmartList<PsiClass>();
		processSuperClassList(firstClass, list, new Processor<PsiClass>()
		{
			public boolean process(final PsiClass psiClass)
			{
				return true;
			}
		});
		return list;
	}

	public static boolean processSuperClassList(@Nullable final PsiClass firstClass, @Nonnull final Collection<PsiClass> supers, final Processor<PsiClass> processor)
	{
		for(PsiClass curClass = firstClass; curClass != null && !JavaClassNames.JAVA_LANG_OBJECT.equals(curClass.getQualifiedName()) && !supers.contains(curClass); curClass = curClass.getSuperClass())
		{
			ProgressManager.checkCanceled();
			if(!processor.process(curClass))
			{
				return false;
			}
			supers.add(curClass);
		}
		return true;
	}

	@Nullable
	public static <T> T getRootElement(final PsiFile file, final Class<T> domClass, final Module module)
	{
		if(!(file instanceof XmlFile))
		{
			return null;
		}
		final DomManager domManager = DomManager.getDomManager(file.getProject());
		final DomFileElement<DomElement> element = domManager.getFileElement((XmlFile) file, DomElement.class);
		if(element == null)
		{
			return null;
		}
		final DomElement root = element.getRootElement();
		if(!ReflectionUtil.isAssignable(domClass, root.getClass()))
		{
			return null;
		}
		return (T) root;
	}


	@Nullable
	public static String getDisplayName(final Object element)
	{
		if(element instanceof CommonModelElement)
		{
			final CommonModelElement modelElement = (CommonModelElement) element;
			String name = getElementName(modelElement);
			if(name == null)
			{
				name = "";
			}
			return getClassName(modelElement) + " '" + name + "'";
		}
		return null;
	}

	@Nullable
	public static String getElementName(final CommonModelElement element)
	{
		return ElementPresentationManager.getElementName(element);
	}

	public static String getClassName(final CommonModelElement element)
	{
		return ElementPresentationManager.getTypeNameForObject(element);
	}

	@Nullable
	public static Module findModuleForPsiElement(final PsiElement element)
	{
		PsiFile psiFile = element.getContainingFile();
		if(psiFile == null)
		{
			final VirtualFile virtualFile = PsiUtilBase.getVirtualFile(element);
			return virtualFile == null ? null : ProjectRootManager.getInstance(element.getProject()).getFileIndex().getModuleForFile(virtualFile);
		}

		psiFile = psiFile.getOriginalFile();
		if(psiFile instanceof XmlFile)
		{
			final DomFileElement<CommonDomModelElement> domFileElement = DomManager.getDomManager(element.getProject()).getFileElement((XmlFile) psiFile, CommonDomModelElement.class);
			if(domFileElement != null)
			{
				Module module = domFileElement.getRootElement().getModule();
				if(module != null)
				{
					return module;
				}
			}
		}

		return ModuleUtil.findModuleForPsiElement(psiFile);
	}

	@Nonnull
	public static PsiElement[] getTargetPsiElements(final CommonModelElement element)
	{
		final ArrayList<PsiElement> list = new ArrayList<PsiElement>();
		// todo add new JAM or drop this functionality
		for(CommonModelElement modelElement : ModelMergerUtil.getFilteredImplementations(element))
		{
			if(modelElement instanceof DomElement)
			{
				final DomElement domModelElement = (DomElement) modelElement;
				if(domModelElement.getXmlTag() != null)
				{
					list.add(domModelElement.getXmlTag());
				}
			}
			else if(modelElement instanceof JamChief)
			{
				list.add(((JamChief) modelElement).getPsiElement());
			}
			else
			{
				ContainerUtil.addIfNotNull(modelElement.getIdentifyingPsiElement(), list);
			}
		}
		final PsiElement[] result = list.isEmpty() ? PsiElement.EMPTY_ARRAY : PsiUtilCore.toPsiElementArray(list);
		Arrays.sort(result, new Comparator<PsiElement>()
		{
			public int compare(final PsiElement o1, final PsiElement o2)
			{
				return getWeight(o1) - getWeight(o2);
			}

			private int getWeight(PsiElement o)
			{
				if(o instanceof XmlTag)
				{
					return 0;
				}
				if(o instanceof PsiMember)
				{
					return 1;
				}
				if(o instanceof PsiAnnotation)
				{
					return 2;
				}
				return 3;
			}
		});
		return result;
	}

	public static boolean isInLibrary(final CommonModelElement modelElement)
	{
		if(modelElement == null)
		{
			return false;
		}
		final PsiElement psiElement = modelElement.getIdentifyingPsiElement();
		if(psiElement == null)
		{
			return false;
		}
		final PsiFile psiFile = psiElement.getContainingFile();
		if(psiFile == null)
		{
			return false;
		}
		final VirtualFile virtualFile = psiFile.getVirtualFile();
		if(virtualFile == null)
		{
			return false;
		}
		return ProjectRootManager.getInstance(modelElement.getPsiManager().getProject()).getFileIndex().isInLibraryClasses(virtualFile);
	}

	public static boolean isKindOfJavaFile(final PsiElement element)
	{
		return element instanceof PsiJavaFile && element.getLanguage().isKindOf(JavaLanguage.INSTANCE);
	}

	public static boolean isPlainJavaFile(final PsiElement element)
	{
		return JamCommonService.getInstance().isPlainJavaFile(element);
	}

	public static boolean isPlainXmlFile(final PsiElement element)
	{
		return JamCommonService.getInstance().isPlainXmlFile(element);
	}

	private static final Key<CachedValue<Module[]>> MODULE_DEPENDENCIES = Key.create("MODULE_DEPENDENCIES");
	private static final Key<CachedValue<Module[]>> MODULE_DEPENDENTS = Key.create("MODULE_DEPENDENTS");

	@Nonnull
	public static Module[] getAllModuleDependencies(@Nonnull final Module module)
	{
		CachedValue<Module[]> value = module.getUserData(MODULE_DEPENDENCIES);
		if(value == null)
		{
			module.putUserData(MODULE_DEPENDENCIES, value = CachedValuesManager.getManager(module.getProject()).createCachedValue(new CachedValueProvider<Module[]>()
			{
				public Result<Module[]> compute()
				{
					final Set<Module> result = addModuleDependencies(module, new HashSet<Module>(), false);
					return new Result<Module[]>(result.toArray(new Module[result.size()]), ProjectRootManager.getInstance(module.getProject()));
				}
			}, false));
		}
		return value.getValue();
	}

	@Nonnull
	public static Module[] getAllDependentModules(@Nonnull final Module module)
	{
		CachedValue<Module[]> value = module.getUserData(MODULE_DEPENDENTS);
		if(value == null)
		{
			module.putUserData(MODULE_DEPENDENTS, value = CachedValuesManager.getManager(module.getProject()).createCachedValue(new CachedValueProvider<Module[]>()
			{
				public Result<Module[]> compute()
				{
					final Module[] modules = ModuleManager.getInstance(module.getProject()).getModules();
					final Set<Module> result = addModuleDependents(module, new HashSet<Module>(), modules);
					return new Result<Module[]>(result.toArray(new Module[result.size()]), ProjectRootManager.getInstance(module.getProject()));
				}
			}, false));
		}
		return value.getValue();
	}

	public static Set<Module> addModuleDependencies(final Module module, final Set<Module> result, final boolean exported)
	{
		if(module.isDisposed())
		{
			return Collections.emptySet();
		}
		final OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
		for(OrderEntry orderEntry : orderEntries)
		{
			if(orderEntry.isValid() && orderEntry instanceof ModuleOrderEntry && (!exported || ((ModuleOrderEntry) orderEntry).isExported()))
			{
				final Module exportedModule = ((ModuleOrderEntry) orderEntry).getModule();
				if(result.add(exportedModule))
				{
					addModuleDependencies(exportedModule, result, true);
				}
			}
		}
		return result;
	}

	public static Set<Module> addModuleDependents(final Module module, final Set<Module> result, final Module[] modules)
	{
		if(!result.add(module))
		{
			return result;
		}
		for(Module allModule : modules)
		{
			final OrderEntry[] orderEntries = ModuleRootManager.getInstance(allModule).getOrderEntries();
			for(OrderEntry orderEntry : orderEntries)
			{
				if(orderEntry.isValid() && orderEntry instanceof ModuleOrderEntry)
				{
					final Module exportedModule = ((ModuleOrderEntry) orderEntry).getModule();
					if(exportedModule == module && result.add(allModule))
					{
						if(((ModuleOrderEntry) orderEntry).isExported())
						{
							addModuleDependents(allModule, result, modules);
						}
					}
				}
			}
		}
		return result;
	}

	@Nonnull
	public static Collection<PsiClass> getAnnotationTypesWithChildren(final String annotationName, final Module module)
	{
		final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
		final PsiClass psiClass = JavaPsiFacade.getInstance(module.getProject()).findClass(annotationName, scope);

		if(psiClass == null || !psiClass.isAnnotationType())
		{
			return Collections.emptyList();
		}

		final Set<PsiClass> classes = Sets.newHashSet(HASHING_STRATEGY);

		collectClassWithChildren(psiClass, classes, scope);

		return classes;
	}

	public static Collection<PsiClass> getAnnotationTypesWithChildren(final Module module, final Key<CachedValue<Collection<PsiClass>>> key, final String annotationName)
	{
		CachedValue<Collection<PsiClass>> cachedValue = module.getUserData(key);
		if(cachedValue == null)
		{
			cachedValue = CachedValuesManager.getManager(module.getProject()).createCachedValue(new CachedValueProvider<Collection<PsiClass>>()
			{
				public Result<Collection<PsiClass>> compute()
				{
					final Collection<PsiClass> classes = getAnnotationTypesWithChildren(annotationName, module);
					return new Result<Collection<PsiClass>>(classes, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
				}
			}, false);

			module.putUserData(key, cachedValue);
		}
		final Collection<PsiClass> classes = cachedValue.getValue();

		return classes == null ? Collections.<PsiClass>emptyList() : classes;
	}

	private static void collectClassWithChildren(final PsiClass psiClass, final Set<PsiClass> classes, final GlobalSearchScope scope)
	{
		classes.add(psiClass);

		for(PsiClass aClass : getChildren(psiClass, scope))
		{
			if(!classes.contains(aClass))
			{
				collectClassWithChildren(aClass, classes, scope);
			}
		}
	}

	public static Collection<PsiClass> getAnnotatedTypes(final Module module, final Key<CachedValue<Collection<PsiClass>>> key, final String annotationName)
	{

		CachedValue<Collection<PsiClass>> cachedValue = module.getUserData(key);
		if(cachedValue == null)
		{
			cachedValue = CachedValuesManager.getManager(module.getProject()).createCachedValue(new CachedValueProvider<Collection<PsiClass>>()
			{
				public Result<Collection<PsiClass>> compute()
				{
					final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
					final PsiClass psiClass = JavaPsiFacade.getInstance(module.getProject()).findClass(annotationName, scope);

					final Collection<PsiClass> classes;
					if(psiClass == null || !psiClass.isAnnotationType())
					{
						classes = Collections.emptyList();
					}
					else
					{
						classes = getChildren(psiClass, scope);
					}
					return new Result<Collection<PsiClass>>(classes, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
				}
			}, false);

			module.putUserData(key, cachedValue);
		}
		final Collection<PsiClass> classes = cachedValue.getValue();

		return classes == null ? Collections.<PsiClass>emptyList() : classes;
	}

	public static Set<PsiClass> getChildren(final PsiClass psiClass, final GlobalSearchScope scope)
	{
		if(!isAcceptedFor(psiClass, ElementType.ANNOTATION_TYPE, ElementType.TYPE))
		{
			return Collections.emptySet();
		}

		final String name = psiClass.getQualifiedName();
		if(name == null)
		{
			return Collections.emptySet();
		}

		final Set<PsiClass> result = Sets.newHashSet(HASHING_STRATEGY);

		AnnotatedMembersSearch.search(psiClass, scope).forEach(new Processor<PsiMember>()
		{
			public boolean process(final PsiMember psiMember)
			{
				if(psiMember instanceof PsiClass && ((PsiClass) psiMember).isAnnotationType())
				{
					result.add((PsiClass) psiMember);
				}
				return true;
			}
		});

		return result;
	}

	public static boolean isAcceptedFor(final PsiClass psiClass, final ElementType... elementTypes)
	{
		final PsiModifierList modifierList = psiClass.getModifierList();
		PsiAnnotation psiAnnotation = modifierList == null ? null : modifierList.findAnnotation(Target.class.getName());
		if(psiAnnotation == null)
		{
			return false;
		}

		return !processObjectArrayValue(psiAnnotation, "value", new Processor<PsiAnnotationMemberValue>()
		{
			@Override
			public boolean process(PsiAnnotationMemberValue value)
			{
				ElementType obj = getObjectValue(value, ElementType.class);
				if(obj == null)
				{
					return true;
				}
				for(ElementType type : elementTypes)
				{
					if(type.equals(obj))
					{
						return false;
					}
				}
				return true;
			}
		});
	}

	@Nullable
	public static XmlTag getXmlTag(final CommonModelElement object)
	{
		final DomElement dom = ModelMergerUtil.getImplementation(object, DomElement.class);
		return dom != null ? dom.getXmlTag() : null;
	}

	public static <M extends PsiModifierListOwner, V extends JamElement> List<V> getElementsIncludingSingle(final M member, final JamAnnotationMeta multiMeta, final JamAnnotationAttributeMeta<V, List<V>> multiAttrMeta)
	{
		final List<V> multiValue = multiMeta.getAttribute(member, multiAttrMeta);
		final PsiElementRef<PsiAnnotation> singleAnno = multiAttrMeta.getAnnotationMeta().getAnnotationRef(member);
		return singleAnno.isImaginary() ? multiValue : ContainerUtil.concat(Collections.singletonList(multiAttrMeta.getInstantiator().instantiate(singleAnno)), multiValue);
	}

	public static boolean isClassAvailable(final Project project, final String qName)
	{
		return JavaPsiFacade.getInstance(project).findClass(qName, GlobalSearchScope.allScope(project)) != null;
	}

	public static String getFirstRootAnnotation(final JamMemberMeta<?, ?> meta)
	{
		final List<JamAnnotationMeta> annos = meta == null ? null : meta.getAnnotations();
		return annos == null || annos.isEmpty() ? null : annos.get(0).getAnnoName();
	}

	@Nullable
	public static Object getModelObject(PsiElement element)
	{
		if(!(element instanceof PomTargetPsiElement))
		{
			return null;
		}
		final PomTarget target = ((PomTargetPsiElement) element).getTarget();
		return target instanceof CommonModelTarget ? ((CommonModelTarget) target).getCommonElement() : target instanceof JamPomTarget ? ((JamPomTarget) target).getJamElement() : target instanceof DomTarget ? ((DomTarget) target).getDomElement() : null;
	}

	@Nullable
	public static DeleteProvider createDeleteProvider(final AbstractTreeBuilder builder)
	{
		final List<DeleteProvider> toRun = new ArrayList<DeleteProvider>();
		final List<JamDeleteProvider> jamProviders = new ArrayList<JamDeleteProvider>();
		for(JamNodeDescriptor descriptor : builder.getSelectedElements(JamNodeDescriptor.class))
		{
			final DeleteProvider provider = descriptor.isValid() ? (DeleteProvider) descriptor.getDataForElement(PlatformDataKeys.DELETE_ELEMENT_PROVIDER) : null;
			if(provider instanceof JamDeleteProvider)
			{
				jamProviders.add((JamDeleteProvider) provider);
			}
			else if(provider != null)
			{
				toRun.add(provider);
			}
		}
		if(!jamProviders.isEmpty())
		{
			if(jamProviders.size() == 1)
			{
				toRun.add(jamProviders.get(0));
			}
			else
			{
				toRun.add(new JamDeleteProvider(jamProviders));
			}
		}

		if(toRun.isEmpty())
		{
			return null;
		}
		return new DeleteProvider()
		{
			@Override
			public void deleteElement(@Nonnull DataContext dataContext)
			{
				for(DeleteProvider provider : toRun)
				{
					provider.deleteElement(dataContext);
				}
			}

			@Override
			public boolean canDeleteElement(@Nonnull DataContext dataContext)
			{
				for(DeleteProvider provider : toRun)
				{
					if(!provider.canDeleteElement(dataContext))
					{
						return false;
					}
				}
				return true;
			}
		};
	}

	public static <T extends JamElement> CachedValue<List<T>> createClassCachedValue(final Project project, final Factory<GlobalSearchScope> scope, final JamClassMeta<? extends T>... meta)
	{
		return CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<List<T>>()
		{
			@Override
			public Result<List<T>> compute()
			{
				GlobalSearchScope searchScope = scope.create();
				final JamService jamService = JamService.getJamService(project);
				List<T> result = new ArrayList<T>();
				if(!DumbService.isDumb(project))
				{
					for(JamClassMeta<? extends T> classMeta : meta)
					{
						for(JamAnnotationMeta annotationMeta : classMeta.getRootAnnotations())
						{
							result.addAll(jamService.getJamClassElements(classMeta, annotationMeta.getAnnoName(), searchScope));
						}
					}
				}
				return new Result<List<T>>(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
			}
		}, false);
	}

	public static void setAnnotationAttributeValue(PsiAnnotation annotation, String attribute, String value)
	{
		new JamStringAttributeElement<String>(PsiElementRef.real(annotation), attribute, JamConverter.DUMMY_CONVERTER).setStringValue(value);
	}

	public static <T extends PsiModifierListOwner> void findAnnotatedElements(Class<T> elementClass, String annotationClass, PsiManager psiManager, GlobalSearchScope scope, Processor<T> processor)
	{
		final PsiClass aClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(annotationClass, GlobalSearchScope.allScope(psiManager.getProject()));
		if(aClass != null)
		{
			AnnotatedElementsSearch.<T>searchElements(aClass, scope, elementClass).forEach(processor);
		}
	}

	public static boolean processObjectArrayValue(PsiAnnotation annotation, String attributeName, Processor<PsiAnnotationMemberValue> processor)
	{
		if(annotation != null)
		{
			final PsiAnnotationMemberValue memberValue = annotation.findAttributeValue(attributeName);
			if(memberValue instanceof PsiArrayInitializerMemberValue)
			{
				PsiArrayInitializerMemberValue arrayValue = (PsiArrayInitializerMemberValue) memberValue;
				for(PsiAnnotationMemberValue value : arrayValue.getInitializers())
				{
					if(!processor.process(value))
					{
						return false;
					}
				}
			}
			else if(memberValue != null)
			{
				if(!processor.process(memberValue))
				{
					return false;
				}
			}
		}
		return true;
	}

	@Nullable
	public static PsiClass getPsiClass(@Nullable PsiAnnotationMemberValue psiAnnotationMemberValue)
	{
		PsiClass psiClass = null;
		if(psiAnnotationMemberValue instanceof PsiClassObjectAccessExpression)
		{
			final PsiType type = ((PsiClassObjectAccessExpression) psiAnnotationMemberValue).getOperand().getType();
			if(type instanceof PsiClassType)
			{
				psiClass = ((PsiClassType) type).resolve();
			}
		}
		else if(psiAnnotationMemberValue instanceof PsiExpression)
		{
			final Object value = computeMemberValue(psiAnnotationMemberValue);
			if(value instanceof String)
			{
				String className = StringUtil.stripQuotesAroundValue((String) value);
				psiClass = JavaPsiFacade.getInstance(psiAnnotationMemberValue.getProject()).findClass(className, psiAnnotationMemberValue.getResolveScope());
			}
		}
		if(psiClass != null && JavaClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName()))
		{
			return null;
		}
		return psiClass;
	}

	// we can consider using AnnotationUtil methods instead
	@Nullable
	public static <T> T getObjectValue(@Nullable PsiAnnotationMemberValue value, Class<T> clazz)
	{
		boolean isString = clazz == String.class;
		if(ReflectionUtil.isAssignable(Enum.class, clazz))
		{
			return (T) getEnumValue(value, (Class<Enum>) clazz);
		}
		//if (value instanceof PsiExpression) {
		final Object obj = computeMemberValue(value);
		if(obj != null && ReflectionUtil.isAssignable(clazz, obj.getClass()))
		{
			return isString ? (T) StringUtil.stripQuotesAroundValue((String) obj) : (T) obj;
		}
		//}
		return null;
	}

	@Nullable
	public static <T extends Enum> T getEnumValue(PsiAnnotationMemberValue memberValue, Class<T> clazz)
	{
		assert ReflectionUtil.isAssignable(Enum.class, clazz);
		if(memberValue instanceof PsiReferenceExpression)
		{
			final PsiReferenceExpression psiReferenceExpression = (PsiReferenceExpression) memberValue;
			final PsiElement psiElement = psiReferenceExpression.resolve();
			if(psiElement instanceof PsiField)
			{
				final PsiField psiField = (PsiField) psiElement;
				return (T) Enum.valueOf(clazz, psiField.getName());
			}
		}
		return null;
	}
}
