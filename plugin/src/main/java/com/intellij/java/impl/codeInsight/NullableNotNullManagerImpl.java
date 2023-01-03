// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.HardcodedContracts;
import com.intellij.java.indexing.impl.stubs.index.JavaAnnotationIndex;
import com.intellij.java.language.codeInsight.*;
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
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.PsiUtilCore;
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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdom.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static com.intellij.java.language.codeInsight.AnnotationUtil.NOT_NULL;
import static com.intellij.java.language.codeInsight.AnnotationUtil.NULLABLE;

@Singleton
@State(name = "NullableNotNullManager", storages = @Storage("misc.xml"))
@ServiceImpl
public class NullableNotNullManagerImpl extends NullableNotNullManager implements PersistentStateComponent<Element>, ModificationTracker {
  public static final String TYPE_QUALIFIER_NICKNAME = "javax.annotation.meta.TypeQualifierNickname";
  private static final String INSTRUMENTED_NOT_NULLS_TAG = "instrumentedNotNulls";

  public String myDefaultNullable = JAVAX_ANNOTATION_NULLABLE;
  public String myDefaultNotNull = JAVAX_ANNOTATION_NONNULL;
  public final JDOMExternalizableStringList myNullables = new JDOMExternalizableStringList(Arrays.asList(DEFAULT_NULLABLES));
  public final JDOMExternalizableStringList myNotNulls = new JDOMExternalizableStringList(Arrays.asList(DEFAULT_NOT_NULLS));
  private List<String> myInstrumentedNotNulls = ContainerUtil.newArrayList(JAVAX_ANNOTATION_NONNULL);
  private final SimpleModificationTracker myTracker = new SimpleModificationTracker();

  @Inject
  public NullableNotNullManagerImpl(Project project) {
    super(project);
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

    if (myInstrumentedNotNulls.size() != 1 || !NOT_NULL.equals(myInstrumentedNotNulls.get(0))) {
      // poor man's @XCollection(style = XCollection.Style.v2)
      Element instrumentedNotNulls = new Element(INSTRUMENTED_NOT_NULLS_TAG);
      for (String value : myInstrumentedNotNulls) {
        instrumentedNotNulls.addContent(new Element("option").setAttribute("value", value));
      }
      component.addContent(instrumentedNotNulls);
    }

    return component;
  }

  private boolean hasDefaultValues() {
    return NOT_NULL.equals(myDefaultNotNull) &&
      NULLABLE.equals(myDefaultNullable) &&
      new HashSet<>(myNullables).equals(Set.of(DEFAULT_NULLABLES)) &&
      new HashSet<>(myNotNulls).equals(Set.of(DEFAULT_NOT_NULLS));
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
    myNotNulls.removeAll(Set.of(DEFAULT_NULLABLES));
    myNullables.removeAll(Set.of(DEFAULT_NOT_NULLS));
    myNullables.addAll(ContainerUtil.filter(DEFAULT_NULLABLES, s -> !myNullables.contains(s)));
    myNotNulls.addAll(ContainerUtil.filter(DEFAULT_NOT_NULLS, s -> !myNotNulls.contains(s)));
    myTracker.incModificationCount();
  }

  @Nonnull
  private List<PsiClass> getAllNullabilityNickNames() {
    if (!getNotNulls().contains(JAVAX_ANNOTATION_NONNULL)) {
      return Collections.emptyList();
    }
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
      List<PsiClass> result = new ArrayList<>();
      GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
      PsiClass[] nickDeclarations = JavaPsiFacade.getInstance(myProject).findClasses(TYPE_QUALIFIER_NICKNAME, scope);
      for (PsiClass tqNick : nickDeclarations) {
        result.addAll(ContainerUtil.findAll(MetaAnnotationUtil.getChildren(tqNick, scope),
                                            NullableNotNullManagerImpl::isNullabilityNickName));
      }
      if (nickDeclarations.length == 0) {
        result.addAll(getUnresolvedNicknameUsages());
      }
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  // some frameworks use jsr305 annotations but don't have them in classpath
  @Nonnull
  private List<PsiClass> getUnresolvedNicknameUsages() {
    List<PsiClass> result = new ArrayList<>();
    Collection<PsiAnnotation> annotations = JavaAnnotationIndex.getInstance()
                                                               .get(StringUtil.getShortName(TYPE_QUALIFIER_NICKNAME),
                                                                    myProject,
                                                                    GlobalSearchScope.allScope(myProject));
    for (PsiAnnotation annotation : annotations) {
      PsiElement context = annotation.getContext();
      if (context instanceof PsiModifierList && context.getContext() instanceof PsiClass) {
        PsiClass ownerClass = (PsiClass)context.getContext();
        if (ownerClass.isAnnotationType() && isNullabilityNickName(ownerClass)) {
          result.add(ownerClass);
        }
      }
    }
    return result;
  }

  @Nullable
  protected NullabilityAnnotationInfo isJsr305Default(@Nonnull PsiAnnotation annotation,
                                                      @Nonnull PsiAnnotation.TargetType[] placeTargetTypes) {
    PsiClass declaration = resolveAnnotationType(annotation);
    PsiModifierList modList = declaration == null ? null : declaration.getModifierList();
    if (modList == null) {
      return null;
    }

    PsiAnnotation tqDefault = AnnotationUtil.findAnnotation(declaration, true, "javax.annotation.meta.TypeQualifierDefault");
    if (tqDefault == null) {
      return null;
    }

    Set<PsiAnnotation.TargetType> required = AnnotationTargetUtil.extractRequiredAnnotationTargets(tqDefault.findAttributeValue(null));
    if (required == null || (!required.isEmpty() && !ContainerUtil.intersects(required, Arrays.asList(placeTargetTypes)))) {
      return null;
    }

    for (PsiAnnotation qualifier : modList.getAnnotations()) {
      Nullability nullability = getJsr305QualifierNullability(qualifier);
      if (nullability != null) {
        return new NullabilityAnnotationInfo(annotation, nullability, true);
      }
    }
    return null;
  }

  @Override
  @Nullable
  protected NullabilityAnnotationInfo getNullityDefault(@Nonnull PsiModifierListOwner container,
                                                        @Nonnull PsiAnnotation.TargetType[] placeTargetTypes,
                                                        PsiElement context, boolean superPackage) {
    PsiModifierList modifierList = container.getModifierList();
    if (modifierList == null) {
      return null;
    }
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
      NullabilityAnnotationInfo result = checkNullityDefault(annotation, placeTargetTypes, superPackage);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  private NullabilityAnnotationInfo checkNullityDefault(@Nonnull PsiAnnotation annotation,
                                                        @Nonnull PsiAnnotation.TargetType[] placeTargetTypes,
                                                        boolean superPackage) {
    NullabilityAnnotationInfo jsr = superPackage ? null : isJsr305Default(annotation, placeTargetTypes);
    return jsr != null ? jsr : CheckerFrameworkNullityUtil.isCheckerDefault(annotation, placeTargetTypes);
  }

  @Nullable
  private static PsiClass resolveAnnotationType(@Nonnull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement element = annotation.getNameReferenceElement();
    PsiElement declaration = element == null ? null : element.resolve();
    if (!(declaration instanceof PsiClass) || !((PsiClass)declaration).isAnnotationType()) {
      return null;
    }
    return (PsiClass)declaration;
  }

  @Nullable
  private Nullability getJsr305QualifierNullability(@Nonnull PsiAnnotation qualifier) {
    String qName = qualifier.getQualifiedName();
    if (qName == null || !qName.startsWith("javax.annotation.")) {
      return null;
    }

    if (qName.equals(JAVAX_ANNOTATION_NULLABLE) && getNullables().contains(qName)) {
      return Nullability.NULLABLE;
    }
    if (qName.equals(JAVAX_ANNOTATION_NONNULL)) {
      return extractNullityFromWhenValue(qualifier);
    }
    return null;
  }

  private static boolean isNullabilityNickName(@Nonnull PsiClass candidate) {
    String qname = candidate.getQualifiedName();
    if (qname == null || qname.startsWith("javax.annotation.")) {
      return false;
    }
    return getNickNamedNullability(candidate) != Nullability.UNKNOWN;
  }

  @Nonnull
  private static Nullability getNickNamedNullability(@Nonnull PsiClass psiClass) {
    if (AnnotationUtil.findAnnotation(psiClass, TYPE_QUALIFIER_NICKNAME) == null) {
      return Nullability.UNKNOWN;
    }

    PsiAnnotation nonNull = AnnotationUtil.findAnnotation(psiClass, JAVAX_ANNOTATION_NONNULL);
    return nonNull != null ? extractNullityFromWhenValue(nonNull) : Nullability.UNKNOWN;
  }

  @Nonnull
  private static Nullability extractNullityFromWhenValue(@Nonnull PsiAnnotation nonNull) {
    PsiAnnotationMemberValue when = nonNull.findAttributeValue("when");
    if (when instanceof PsiReferenceExpression) {
      String refName = ((PsiReferenceExpression)when).getReferenceName();
      if ("ALWAYS".equals(refName)) {
        return Nullability.NOT_NULL;
      }
      if ("MAYBE".equals(refName) || "NEVER".equals(refName)) {
        return Nullability.NULLABLE;
      }
    }

    // 'when' is unknown and annotation is known -> default value (for javax.annotation.Nonnull is ALWAYS)
    if (when == null && JAVAX_ANNOTATION_NONNULL.equals(nonNull.getQualifiedName())) {
      return Nullability.NOT_NULL;
    }
    return Nullability.UNKNOWN;
  }

  @Nonnull
  private List<String> filterNickNames(@Nonnull Nullability nullability) {
    return ContainerUtil.mapNotNull(getAllNullabilityNickNames(),
                                    c -> getNickNamedNullability(c) == nullability ? c.getQualifiedName() : null);
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
  protected Set<String> getAllNullabilityAnnotationsWithNickNames() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
      Set<String> result = new HashSet<>();
      result.addAll(getNotNulls());
      result.addAll(getNullables());
      result.addAll(ContainerUtil.mapNotNull(getAllNullabilityNickNames(), PsiClass::getQualifiedName));
      return CachedValueProvider.Result.create(Collections.unmodifiableSet(result), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @Override
  public long getModificationCount() {
    return myTracker.getModificationCount();
  }
}
