// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.HardcodedContracts;
import com.intellij.java.indexing.impl.stubs.index.JavaAnnotationIndex;
import com.intellij.java.language.annoPackages.AnnotationPackageSupport;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullabilityAnnotationInfo;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.codeInsight.MetaAnnotationUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.util.ModificationTracker;
import consulo.component.util.SimpleModificationTracker;
import consulo.java.language.impl.annoPackages.Jsr305Support;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.DelegatingGlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.DefaultJDOMExternalizer;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizableStringList;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdom.Element;

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
  @Nonnull
  public List<String> getDefaultNullables() {
    AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(myProject.getApplication());
    return new ArrayList<>(cache.myDefaultNullables.keySet());
  }

  @Override
  @Nonnull
  public List<String> getDefaultNotNulls() {
    AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(myProject.getApplication());
    return new ArrayList<>(cache.myDefaultNotNulls.keySet());
  }

  @Override
  public void setNotNulls(@Nonnull String... annotations) {
    myNotNulls.clear();
    Collections.addAll(myNotNulls, annotations);
    normalizeDefaults();
  }

  @Override
  public void setNullables(@Nonnull String... annotations) {
    myNullables.clear();
    Collections.addAll(myNullables, annotations);
    normalizeDefaults();
  }

  @Override
  @Nonnull
  public String getDefaultNullable() {
    return myDefaultNullable;
  }

  @Override
  public void setDefaultNullable(@Nonnull String defaultNullable) {
    LOG.assertTrue(getNullables().contains(defaultNullable));
    myDefaultNullable = defaultNullable;
    myTracker.incModificationCount();
  }

  @Override
  @Nonnull
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
  @Nonnull
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
  public void setDefaultNotNull(@Nonnull String defaultNotNull) {
    LOG.assertTrue(getNotNulls().contains(defaultNotNull));
    myDefaultNotNull = defaultNotNull;
    myTracker.incModificationCount();
  }

  @Override
  @Nonnull
  public List<String> getNullables() {
    return Collections.unmodifiableList(myNullables);
  }

  @Override
  @Nonnull
  public List<String> getNotNulls() {
    return Collections.unmodifiableList(myNotNulls);
  }

  @Nonnull
  @Override
  public List<String> getInstrumentedNotNulls() {
    return Collections.unmodifiableList(myInstrumentedNotNulls);
  }

  @Override
  public void setInstrumentedNotNulls(@Nonnull List<String> names) {
    myInstrumentedNotNulls = ContainerUtil.sorted(names);
    myTracker.incModificationCount();
  }

  @Override
  protected boolean hasHardcodedContracts(@Nonnull PsiElement element) {
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
  protected @Nullable NullabilityAnnotationInfo findNullityDefaultOnModule(PsiAnnotation.TargetType[] targetTypes,
                                                                           @Nonnull PsiElement element) {
    PsiJavaModule module = JavaModuleGraphUtil.findDescriptorByElement(element);
    if (module != null) {
      return getNullityDefault(module, targetTypes, element, false);
    }
    return null;
  }

  private boolean hasDefaultValues() {
    AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(myProject.getApplication());
    return JAKARTA_ANNOTATION_NONNULL.equals(myDefaultNotNull) &&
      JAKARTA_ANNOTATION_NULLABLE.equals(myDefaultNullable) &&
      new HashSet<>(myNullables).equals(cache.myDefaultNullables.keySet()) &&
      new HashSet<>(myNotNulls).equals(cache.myDefaultNotNulls.keySet());
  }

  @Override
  public void loadState(@Nonnull Element state) {
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

  @Nonnull
  private List<PsiClass> getAllNullabilityNickNames() {
    if (!getNotNulls().contains(Jsr305Support.JAVAX_ANNOTATION_NONNULL)) {
      return Collections.emptyList();
    }
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
      Set<PsiClass> result = new HashSet<>(getPossiblyUnresolvedJavaNicknameUsages());
      GlobalSearchScope scope = new DelegatingGlobalSearchScope(GlobalSearchScope.allScope(myProject)) {
        @Override
        public boolean contains(@Nonnull VirtualFile file) {
          return super.contains(file) && !FileTypeRegistry.getInstance().isFileOfType(file, JavaFileType.INSTANCE);
        }
      };
      PsiClass[] nickDeclarations = JavaPsiFacade.getInstance(myProject).findClasses(Jsr305Support.TYPE_QUALIFIER_NICKNAME, scope);
      for (PsiClass tqNick : nickDeclarations) {
        result.addAll(ContainerUtil.findAll(MetaAnnotationUtil.getChildren(tqNick, scope), Jsr305Support::isNullabilityNickName));
      }
      return CachedValueProvider.Result.create(new ArrayList<>(result), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  // some frameworks use jsr305 annotations but don't have them in classpath
  @Nonnull
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
  @Nullable
  protected NullabilityAnnotationInfo getNullityDefault(@Nonnull PsiModifierListOwner container,
                                                        @Nonnull PsiAnnotation.TargetType[] placeTargetTypes,
                                                        PsiElement context, boolean superPackage) {
    PsiModifierList modifierList = container.getModifierList();
    if (modifierList == null) return null;
    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      if (container instanceof PsiPackage) {
        VirtualFile annotationFile = PsiUtilCore.getVirtualFile(annotation);
        VirtualFile ownerFile = PsiUtilCore.getVirtualFile(context);
        if (annotationFile != null && ownerFile != null && !annotationFile.equals(ownerFile)) {
          ProjectFileIndex index = ProjectRootManager.getInstance(container.getProject()).getFileIndex();
          VirtualFile annotationRoot = index.getClassRootForFile(annotationFile);
          VirtualFile ownerRoot = index.getClassRootForFile(ownerFile);
          if (ownerRoot != null && !ownerRoot.equals(annotationRoot)) {
            continue;
          }
        }
      }
      NullabilityAnnotationInfo result = checkNullityDefault(annotation, context, placeTargetTypes, superPackage);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  private NullabilityAnnotationInfo checkNullityDefault(@Nonnull PsiAnnotation annotation,
                                                        @Nonnull PsiElement context,
                                                        @Nonnull PsiAnnotation.TargetType[] placeTargetTypes,
                                                        boolean superPackage) {
    for (AnnotationPackageSupport support : myProject.getApplication().getExtensionList(AnnotationPackageSupport.class)) {
      NullabilityAnnotationInfo info = support.getNullabilityByContainerAnnotation(annotation, context, placeTargetTypes, superPackage);
      if (info != null) {
        return info;
      }
    }
    return null;
  }

  @Nonnull
  private List<String> filterNickNames(@Nonnull Nullability nullability) {
    return ContainerUtil.mapNotNull(getAllNullabilityNickNames(),
                                    c -> Jsr305Support.getNickNamedNullability(c) == nullability ? c.getQualifiedName() : null);
  }

  @Override
  protected @Nonnull Nullability correctNullability(@Nonnull Nullability nullability, @Nonnull PsiAnnotation annotation) {
    if (nullability == Nullability.NOT_NULL && annotation.hasQualifiedName(Jsr305Support.JAVAX_ANNOTATION_NONNULL)) {
      Nullability correctedNullability = Jsr305Support.extractNullityFromWhenValue(annotation);
      if (correctedNullability != null) {
        return correctedNullability;
      }
    }
    return nullability;
  }

  @Nonnull
  @Override
  protected List<String> getNullablesWithNickNames() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () ->
      CachedValueProvider.Result.create(ContainerUtil.concat(getNullables(), filterNickNames(Nullability.NULLABLE)),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nonnull
  @Override
  protected List<String> getNotNullsWithNickNames() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () ->
      CachedValueProvider.Result.create(ContainerUtil.concat(getNotNulls(), filterNickNames(Nullability.NOT_NULL)),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nonnull
  @Override
  protected NullabilityAnnotationDataHolder getAllNullabilityAnnotationsWithNickNames() {
    AnnotationPackageSupportCache cache = AnnotationPackageSupportCache.get(myProject.getApplication());

    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
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
      for (PsiClass aClass : getAllNullabilityNickNames()) {
        String qName = aClass.getQualifiedName();
        if (qName != null) {
          result.putIfAbsent(qName, Jsr305Support.getNickNamedNullability(aClass));
        }
      }
      NullabilityAnnotationDataHolder holder = new NullabilityAnnotationDataHolder() {
        @Override
        public Set<String> qualifiedNames() {
          return result.keySet();
        }

        @Override
        public @Nullable Nullability getNullability(String annotation) {
          return result.get(annotation);
        }
      };
      return CachedValueProvider.Result.create(holder, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @Override
  public long getModificationCount() {
    return myTracker.getModificationCount();
  }
}
