// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.HardcodedContracts;
import com.intellij.java.indexing.impl.stubs.index.JavaAnnotationIndex;
import com.intellij.java.language.annoPackages.AnnotationPackageSupport;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.ContextNullabilityInfo;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.codeInsight.MetaAnnotationUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.util.ModificationTracker;
import consulo.component.util.SimpleModificationTracker;
import consulo.java.language.impl.annoPackages.Jsr305Support;
import consulo.language.impl.psi.DummyHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.DelegatingGlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.DefaultJDOMExternalizer;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizableStringList;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static com.intellij.java.language.codeInsight.AnnotationUtil.NOT_NULL;

@Singleton
@State(name = "NullableNotNullManager", storages = @Storage("misc.xml"))
@ServiceImpl
public class NullableNotNullManagerImpl extends NullableNotNullManager implements PersistentStateComponent<Element>, ModificationTracker {
    private static final String INSTRUMENTED_NOT_NULLS_TAG = "instrumentedNotNulls";

    public String myDefaultNullable = JAKARTA_ANNOTATION_NULLABLE;
    public String myDefaultNotNull = JAKARTA_ANNOTATION_NONNULL;
    public final JDOMExternalizableStringList myNullables = new JDOMExternalizableStringList();
    public final JDOMExternalizableStringList myNotNulls = new JDOMExternalizableStringList();
    private List<String> myInstrumentedNotNulls = List.of(NOT_NULL);
    private final SimpleModificationTracker myTracker = new SimpleModificationTracker();

    private final Project myProject;

    @Inject
    public NullableNotNullManagerImpl(Project project) {
        myProject = project;

        AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(project.getApplication());

        myNullables.addAll(cache.myDefaultNullables.keySet());
        myNotNulls.addAll(cache.myDefaultNotNulls.keySet());
    }

    @Override
    public List<String> getDefaultNullables() {
        AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(myProject.getApplication());
        return new ArrayList<>(cache.myDefaultNullables.keySet());
    }

    @Override
    public List<String> getDefaultNotNulls() {
        AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(myProject.getApplication());
        return new ArrayList<>(cache.myDefaultNotNulls.keySet());
    }

    @Override
    public void setNotNulls(String... annotations) {
        myNotNulls.clear();
        Collections.addAll(myNotNulls, annotations);
        normalizeDefaults();
    }

    @Override
    public void setNullables(String... annotations) {
        myNullables.clear();
        Collections.addAll(myNullables, annotations);
        normalizeDefaults();
    }

    @Override
    public String getDefaultNullable() {
        return myDefaultNullable;
    }

    @Override
    public @NotNull String getDefaultAnnotation(@NotNull Nullability nullability, @NotNull PsiElement context) {
        AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(Application.get());

        Collection<String> annotations = switch (nullability) {
            case NOT_NULL -> myNotNulls;
            case NULLABLE -> myNullables;
            case UNKNOWN -> cache.myDefaultUnknowns.keySet();
        };
        PsiFile containingFile = context.getContainingFile();
        if (containingFile instanceof DummyHolder) {
            PsiElement element = containingFile.getContext();
            if (element != null) {
                containingFile = element.getContainingFile();
            }
        }
        PsiFile file = containingFile.getOriginalFile();
        Module module = ModuleUtilCore.findModuleForFile(file);
        if (module == null) {
            return getDefaultAnnotation(nullability);
        }
        GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
        for (String annotation : annotations) {
            if (JavaPsiFacade.getInstance(module.getProject()).findClass(annotation, moduleScope) != null) {
                return annotation;
            }
        }
        return getDefaultAnnotation(nullability);
    }

    private @NotNull String getDefaultAnnotation(@NotNull Nullability nullability) {
        return switch (nullability) {
            case NOT_NULL -> getDefaultNotNull();
            case NULLABLE -> getDefaultNullable();
            case UNKNOWN -> AnnotationUtil.UNKNOWN_NULLABILITY;
        };
    }

    @Override
    public void setDefaultNullable(String defaultNullable) {
        LOG.assertTrue(getNullables().contains(defaultNullable));
        myDefaultNullable = defaultNullable;
        myTracker.incModificationCount();
    }

    @Override
    public String getDefaultNotNull() {
        return myDefaultNotNull;
    }

    @Override
    public boolean isTypeUseAnnotationLocationRestricted(String name) {
        AnnotationPackageSupport support = findAnnotationSupport(name);
        return support != null && support.isTypeUseAnnotationLocationRestricted();
    }

    @Override
    public boolean canAnnotateLocals(String name) {
        AnnotationPackageSupport support = findAnnotationSupport(name);
        return support == null || support.canAnnotateLocals();
    }

    @Nullable
    private AnnotationPackageSupport findAnnotationSupport(String name) {
        AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(myProject.getApplication());

        AnnotationPackageSupport support = cache.myDefaultUnknowns.get(name);
        if (support == null) {
            support = cache.myDefaultNotNulls.get(name);
            if (support == null) {
                support = cache.myDefaultNullables.get(name);
            }
        }
        return support;
    }

    @Override
    public Optional<Nullability> getAnnotationNullability(String name) {
        if (myNotNulls.contains(name)) {
            return Optional.of(Nullability.NOT_NULL);
        }
        if (myNullables.contains(name)) {
            return Optional.of(Nullability.NULLABLE);
        }

        AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(myProject.getApplication());
        if (cache.myDefaultUnknowns.containsKey(name)) {
            return Optional.of(Nullability.UNKNOWN);
        }
        return Optional.empty();
    }

    @Override
    public void setDefaultNotNull(String defaultNotNull) {
        LOG.assertTrue(getNotNulls().contains(defaultNotNull));
        myDefaultNotNull = defaultNotNull;
        myTracker.incModificationCount();
    }

    @Override
    public List<String> getNullables() {
        return Collections.unmodifiableList(myNullables);
    }

    @Override
    public List<String> getNotNulls() {
        return Collections.unmodifiableList(myNotNulls);
    }

    @Override
    public List<String> getInstrumentedNotNulls() {
        return Collections.unmodifiableList(myInstrumentedNotNulls);
    }

    @Override
    public void setInstrumentedNotNulls(List<String> names) {
        myInstrumentedNotNulls = ContainerUtil.sorted(names);
        myTracker.incModificationCount();
    }

    @Override
    public boolean isNonNullUsedForInstrumentation(@NotNull PsiAnnotation annotation) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) return false;
        AnnotationPackageSupport support = AnnotationPackageSupport
            .EP_NAME
            .findFirstSafe(e -> e.getNullabilityAnnotations(Nullability.NOT_NULL).contains(qualifiedName));
        return support != null && support.isNonNullUsedForInstrumentation();
    }

    @Override
    protected boolean hasHardcodedContracts(PsiElement element) {
        return HardcodedContracts.hasHardcodedContracts(element);
    }

    @Override
    public Element getState() {
        Element component = new Element("component");

        if (!hasDefaultValues()) {
            try {
                DefaultJDOMExternalizer.writeExternal(this, component);
            }
            catch (WriteExternalException e) {
                LOG.error(e);
            }
        }

        if (myInstrumentedNotNulls.size() != 1 || !JAKARTA_ANNOTATION_NONNULL.equals(myInstrumentedNotNulls.get(0))) {
            // poor man's @XCollection(style = XCollection.Style.v2)
            Element instrumentedNotNulls = new Element(INSTRUMENTED_NOT_NULLS_TAG);
            for (String value : myInstrumentedNotNulls) {
                instrumentedNotNulls.addContent(new Element("option").setAttribute("value", value));
            }
            component.addContent(instrumentedNotNulls);
        }

        return component;
    }

    @Override
    protected ContextNullabilityInfo findNullityDefaultOnModule(PsiAnnotation.TargetType[] targetTypes,
                                                                PsiElement element) {
        PsiJavaModule module = JavaModuleGraphUtil.findDescriptorByElement(element);
        if (module != null) {
            return getNullityDefault(module, targetTypes);
        }
        return ContextNullabilityInfo.EMPTY;
    }

    private boolean hasDefaultValues() {
        AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(myProject.getApplication());
        return JAKARTA_ANNOTATION_NONNULL.equals(myDefaultNotNull) &&
            JAKARTA_ANNOTATION_NULLABLE.equals(myDefaultNullable) &&
            new HashSet<>(myNullables).equals(cache.myDefaultNullables.keySet()) &&
            new HashSet<>(myNotNulls).equals(cache.myDefaultNotNulls.keySet());
    }

    @Override
    public void loadState(Element state) {
        try {
            DefaultJDOMExternalizer.readExternal(this, state);
            normalizeDefaults();
        }
        catch (InvalidDataException e) {
            LOG.error(e);
        }

        Element instrumented = state.getChild(INSTRUMENTED_NOT_NULLS_TAG);
        if (instrumented == null) {
            myInstrumentedNotNulls = ContainerUtil.newArrayList(NOT_NULL);
        }
        else {
            myInstrumentedNotNulls = ContainerUtil.mapNotNull(instrumented.getChildren("option"), o -> o.getAttributeValue("value"));
        }
    }

    private void normalizeDefaults() {
        AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(myProject.getApplication());

        myNotNulls.removeAll(cache.myDefaultNullables.keySet());
        myNullables.removeAll(cache.myDefaultNotNulls.keySet());
        myNullables.addAll(ContainerUtil.filter(cache.myDefaultNullables.keySet(), s -> !myNullables.contains(s)));
        myNotNulls.addAll(ContainerUtil.filter(cache.myDefaultNotNulls.keySet(), s -> !myNotNulls.contains(s)));
        myTracker.incModificationCount();
    }

    private @NotNull Map<String, Nullability> getAllNullabilityNickNames() {
        if (!getNotNulls().contains(Jsr305Support.JAVAX_ANNOTATION_NONNULL)) {
            return Collections.emptyMap();
        }
        return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
            GlobalSearchScope scope = new DelegatingGlobalSearchScope(GlobalSearchScope.allScope(myProject)) {
                @Override
                public boolean contains(@NotNull VirtualFile file) {
                    return super.contains(file) && !FileTypeRegistry.getInstance().isFileOfType(file, JavaFileType.INSTANCE);
                }
            };
            PsiClass[] nickDeclarations = JavaPsiFacade.getInstance(myProject).findClasses(Jsr305Support.TYPE_QUALIFIER_NICKNAME, scope);
            Map<String, Nullability> result = StreamEx.of(nickDeclarations)
                .flatCollection(tqNick -> MetaAnnotationUtil.getChildren(tqNick, scope))
                .prepend(getPossiblyUnresolvedJavaNicknameUsages())
                .filter(Jsr305Support::isNullabilityNickName)
                .toMap(PsiClass::getQualifiedName, Jsr305Support::getNickNamedNullability, (n1, n2) -> n1 == n2 ? n1 : Nullability.UNKNOWN);
            return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
        });
    }

    // some frameworks use jsr305 annotations but don't have them in classpath
    private List<PsiClass> getPossiblyUnresolvedJavaNicknameUsages() {
        List<PsiClass> result = new ArrayList<>();
        Collection<PsiAnnotation> annotations = JavaAnnotationIndex.getInstance().getAnnotations(StringUtil.getShortName(
            Jsr305Support.TYPE_QUALIFIER_NICKNAME), myProject, GlobalSearchScope.allScope(myProject));
        for (PsiAnnotation annotation : annotations) {
            PsiElement context = annotation.getContext();
            if (context instanceof PsiModifierList && context.getContext() instanceof PsiClass ownerClass &&
                ownerClass.isAnnotationType() && Jsr305Support.isNullabilityNickName(ownerClass)) {
                result.add(ownerClass);
            }
        }
        return result;
    }

    @Override
    protected ContextNullabilityInfo getNullityDefault(PsiModifierListOwner container,
                                                       PsiAnnotation.TargetType[] placeTargetTypes) {
        LOG.assertTrue(!(container instanceof PsiPackage)); // Packages are handled separately in findNullityDefaultOnPackage
        ContextNullabilityInfo res = ContextNullabilityInfo.EMPTY;
        PsiModifierList modifierList = container.getModifierList();
        if (modifierList != null) {
            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                ContextNullabilityInfo info = checkNullityDefault(annotation, placeTargetTypes, false);
                res = res.orElse(info);
            }
        }
        return res;
    }

    @Override
    public @NotNull List<@NotNull PsiAnnotation> getConflictingContainerAnnotations(@NotNull PsiModifierList owner) {
        if (!owner.hasAnnotations()) {
            return List.of();
        }

        return Application.get().getExtensionPoint(AnnotationPackageSupport.class).computeSafeIfAny(annotationPackageSupport -> {
            List<@NotNull PsiAnnotation> annotations = annotationPackageSupport.getConflictingContainerAnnotations(owner);
            if (!annotations.isEmpty()) {
                return annotations;
            }
            return null;
        }, List.of());
    }

    private ContextNullabilityInfo checkNullityDefault(PsiAnnotation annotation,
                                                       PsiAnnotation.TargetType[] placeTargetTypes,
                                                       boolean superPackage) {
        return Application.get().getExtensionPoint(AnnotationPackageSupport.class).computeSafeIfAny(annotationPackageSupport -> {
            ContextNullabilityInfo info = ContextNullabilityInfo.EMPTY;
            for (AnnotationPackageSupport support : Application.get().getExtensionList(AnnotationPackageSupport.class)) {
                info = info.orElse(support.getNullabilityByContainerAnnotation(annotation, placeTargetTypes, superPackage));
            }

            return info;
        }, ContextNullabilityInfo.EMPTY);
    }

    private @NotNull List<String> filterNickNames(@NotNull Nullability nullability) {
        return StreamEx.ofKeys(getAllNullabilityNickNames(), nullability::equals).toList();
    }

    @Override
    protected Nullability correctNullability(Nullability nullability, PsiAnnotation annotation) {
        if (nullability == Nullability.NOT_NULL && annotation.hasQualifiedName(Jsr305Support.JAVAX_ANNOTATION_NONNULL)) {
            Nullability correctedNullability = Jsr305Support.extractNullityFromWhenValue(annotation);
            if (correctedNullability != null) {
                return correctedNullability;
            }
        }
        return nullability;
    }

    @Override
    protected List<String> getNullablesWithNickNames() {
        return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () ->
            CachedValueProvider.Result.create(ContainerUtil.concat(getNullables(), filterNickNames(Nullability.NULLABLE)),
                PsiModificationTracker.MODIFICATION_COUNT));
    }

    @Override
    protected List<String> getNotNullsWithNickNames() {
        return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () ->
            CachedValueProvider.Result.create(ContainerUtil.concat(getNotNulls(), filterNickNames(Nullability.NOT_NULL)),
                PsiModificationTracker.MODIFICATION_COUNT));
    }

    @Override
    protected NullabilityAnnotationDataHolder getAllNullabilityAnnotationsWithNickNames() {
        if (DumbService.isDumb(myProject) || myProject.isDefault()) {
            // Searching for nullability nicknames is not available in the dumb mode or for default project
            return NullabilityAnnotationDataHolder.fromMap(getNullabilityMap());
        }
        return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
            Map<String, Nullability> result = getNullabilityMap();
            getAllNullabilityNickNames().forEach(result::putIfAbsent);
            NullabilityAnnotationDataHolder holder = NullabilityAnnotationDataHolder.fromMap(result);
            return CachedValueProvider.Result.create(holder, PsiModificationTracker.MODIFICATION_COUNT);
        });
    }

    private @NotNull Map<String, Nullability> getNullabilityMap() {
        AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(myProject.getApplication());

        Map<String, Nullability> result = new HashMap<>();
        for (String qName : cache.myDefaultAll) {
            result.put(qName, null);
        }
        for (String qName : getNotNulls()) {
            result.put(qName, Nullability.NOT_NULL);
        }
        for (String qName : getNullables()) {
            result.put(qName, Nullability.NULLABLE);
        }
        for (String qName : cache.myDefaultUnknowns.keySet()) {
            result.put(qName, Nullability.UNKNOWN);
        }
        return result;
    }

    @Override
    public long getModificationCount() {
        return myTracker.getModificationCount();
    }
}
