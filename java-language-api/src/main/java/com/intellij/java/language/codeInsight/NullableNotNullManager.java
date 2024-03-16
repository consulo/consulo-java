// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.codeInsight;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.java.language.codeInsight.AnnotationUtil.*;

/**
 * @author anna
 */
@ServiceAPI(ComponentScope.PROJECT)
public abstract class NullableNotNullManager {
  protected static final Logger LOG = Logger.getInstance(NullableNotNullManager.class);

  protected static final String JAKARTA_ANNOTATION_NULLABLE = "jakarta.annotation.Nullable";
  protected static final String JAKARTA_ANNOTATION_NONNULL = "jakarta.annotation.Nonnull";

  protected interface NullabilityAnnotationDataHolder {
    /**
     * @return qualified names of all recognized annotations
     */
    Set<String> qualifiedNames();

    /**
     * @param annotation annotation qualified name to check
     * @return nullability
     */
    @Nullable
    Nullability getNullability(String annotation);
  }

  public static NullableNotNullManager getInstance(Project project) {
    return project.getInstance(NullableNotNullManager.class);
  }


  /**
   * @return list of default non-container annotations that apply to the nullable element
   */
  @Nonnull
  public abstract List<String> getDefaultNullables();

  /**
   * @return list of default non-container annotations that apply to the not-null element
   */
  @Nonnull
  public abstract List<String> getDefaultNotNulls();

  /**
   * @return if owner has a @NotNull or @Nullable annotation, or is in scope of @ParametersAreNullableByDefault or ParametersAreNonnullByDefault
   */
  public boolean hasNullability(@Nonnull PsiModifierListOwner owner) {
    return isNullable(owner, false) || isNotNull(owner, false);
  }

  public abstract void setNotNulls(@Nonnull String... annotations);

  public abstract void setNullables(@Nonnull String... annotations);

  @Nonnull
  public abstract String getDefaultNullable();

  public abstract void setDefaultNullable(@Nonnull String defaultNullable);

  @Nonnull
  public abstract String getDefaultNotNull();

  public abstract boolean isTypeUseAnnotationLocationRestricted(String name);

  public abstract boolean canAnnotateLocals(String name);

  @Nonnull
  public abstract Optional<Nullability> getAnnotationNullability(String name);

  @Nullable
  public PsiAnnotation copyNotNullAnnotation(@Nonnull PsiModifierListOwner original, @Nonnull PsiModifierListOwner generated) {
    NullabilityAnnotationInfo info = findOwnNullabilityInfo(original);
    if (info == null || info.getNullability() != Nullability.NOT_NULL) {
      return null;
    }
    return copyAnnotation(info.getAnnotation(), generated);
  }

  @Nullable
  public PsiAnnotation copyNullableAnnotation(@Nonnull PsiModifierListOwner original, @Nonnull PsiModifierListOwner generated) {
    NullabilityAnnotationInfo info = findOwnNullabilityInfo(original);
    if (info == null || info.getNullability() != Nullability.NULLABLE) {
      return null;
    }
    return copyAnnotation(info.getAnnotation(), generated);
  }

  @Nullable
  public PsiAnnotation copyNullableOrNotNullAnnotation(@Nonnull PsiModifierListOwner original, @Nonnull PsiModifierListOwner generated) {
    NullabilityAnnotationInfo src = findOwnNullabilityInfo(original);
    if (src == null) {
      return null;
    }
    NullabilityAnnotationInfo effective = findEffectiveNullabilityInfo(generated);
    if (effective != null && effective.getNullability() == src.getNullability()) {
      return null;
    }
    return copyAnnotation(src.getAnnotation(), generated);
  }

  @Nullable
  private static PsiAnnotation copyAnnotation(@Nonnull PsiAnnotation annotation, @Nonnull PsiModifierListOwner target) {
    String qualifiedName = annotation.getQualifiedName();
    if (qualifiedName != null) {
      if (JavaPsiFacade.getInstance(annotation.getProject()).findClass(qualifiedName, target.getResolveScope()) == null) {
        return null;
      }

      // type annotations are part of target's type and should not to be copied explicitly to avoid duplication
      if (!AnnotationTargetUtil.isTypeAnnotation(annotation)) {

        PsiModifierList modifierList = target.getModifierList();
        if (modifierList != null && !modifierList.hasAnnotation(qualifiedName)) {
          return modifierList.addAnnotation(qualifiedName);
        }
      }
    }

    return null;
  }

  public abstract void setDefaultNotNull(@Nonnull String defaultNotNull);

  /**
   * Returns own nullability annotation info for given element. Returned annotation is not inherited and
   * not container annotation for class/package. Still it could be inferred or external.
   *
   * @param owner element to find a nullability info for
   * @return own nullability annotation info.
   */
  public @Nullable NullabilityAnnotationInfo findOwnNullabilityInfo(@Nonnull PsiModifierListOwner owner) {
    NullabilityAnnotationInfo info = findEffectiveNullabilityInfo(owner);
    if (info == null || info.isContainer() || info.getInheritedFrom() != null) return null;
    return info;
  }


  /**
   * Returns information about explicit nullability annotation (without looking into external/inferred annotations,
   * but looking into container annotations). This method is rarely useful in client code, it's designed mostly
   * to aid the inference procedure.
   *
   * @param owner element to get the info about
   * @return the annotation info or null if no explicit annotation found
   */
  @Nullable
  public NullabilityAnnotationInfo findExplicitNullability(PsiModifierListOwner owner) {
    NullabilityAnnotationInfo result = findPlainAnnotation(owner, false, true, getAllNullabilityAnnotationsWithNickNames());
    if (result != null) {
      return result;
    }
    return findContainerAnnotation(owner);
  }

  /**
   * Returns nullability annotation info which has effect for given element.
   *
   * @param owner element to find an annotation for
   * @return effective nullability annotation info, or null if not found.
   */
  @Nullable
  public NullabilityAnnotationInfo findEffectiveNullabilityInfo(@Nonnull PsiModifierListOwner owner) {
    PsiType type = getOwnerType(owner);
    if (type == null || TypeConversionUtil.isPrimitiveAndNotNull(type)) {
      return null;
    }

    return LanguageCachedValueUtil.getCachedValue(owner, () -> CachedValueProvider.Result
      .create(doFindEffectiveNullabilityAnnotation(owner), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nullable
  private NullabilityAnnotationInfo doFindEffectiveNullabilityAnnotation(@Nonnull PsiModifierListOwner owner) {
    @Nullable NullabilityAnnotationInfo result = findPlainAnnotation(owner, true, false, getAllNullabilityAnnotationsWithNickNames());
    if (result != null) {
      return result;
    }

    boolean lambdaParameter = owner instanceof PsiParameter && owner.getParent() instanceof PsiParameterList &&
      owner.getParent().getParent() instanceof PsiLambdaExpression;

    if (!lambdaParameter) {
      // For lambda parameter, inherited annotation overrides the default one
      NullabilityAnnotationInfo defaultInfo = findNullityDefaultFiltered(owner);
      if (defaultInfo != null) return defaultInfo;
    }

    if (owner instanceof PsiParameter) {
      List<PsiParameter> superParameters = getSuperAnnotationOwners((PsiParameter)owner);
      if (!superParameters.isEmpty()) {
        for (PsiParameter parameter : superParameters) {
          NullabilityAnnotationInfo plain = findPlainAnnotation(parameter, false, false, getAllNullabilityAnnotationsWithNickNames());
          // Plain not null annotation is not inherited
          if (plain != null) return null;
          NullabilityAnnotationInfo defaultInfo = findContainerAnnotation(parameter);
          if (defaultInfo != null) {
            return defaultInfo.getNullability() == Nullability.NOT_NULL ? defaultInfo.withInheritedFrom(parameter) : null;
          }
        }
        return null;
      }
    }

    if (lambdaParameter) {
      return findNullityDefaultFiltered(owner);
    }
    return null;
  }

  private @Nullable NullabilityAnnotationInfo findNullityDefaultFiltered(@Nonnull PsiModifierListOwner owner) {
    NullabilityAnnotationInfo defaultInfo = findContainerAnnotation(owner);
    if (defaultInfo != null && (defaultInfo.getNullability() == Nullability.NULLABLE || !hasHardcodedContracts(owner))) {
      return defaultInfo;
    }
    return null;
  }

  /**
   * Looks for applicable container annotation, ignoring explicit, inferred, external, or inherited annotations.
   * Usually, should not be used directly, as {@link #findEffectiveNullabilityInfo(PsiModifierListOwner)} will
   * return container annotation if it's applicable.
   *
   * @param owner member to find annotation for
   * @return container annotation applicable to the owner location
   */
  public @Nullable NullabilityAnnotationInfo findContainerAnnotation(@Nonnull PsiModifierListOwner owner) {
    return findNullabilityDefault(owner, AnnotationTargetUtil.getTargetsForLocation(owner.getModifierList()));
  }

  @Nullable
  private NullabilityAnnotationInfo findNullabilityDefault(@Nonnull PsiElement place,
                                                           @Nonnull PsiAnnotation.TargetType... placeTargetTypes) {
    PsiElement element = place.getContext();
    while (element != null) {
      if (element instanceof PsiModifierListOwner) {
        NullabilityAnnotationInfo result = getNullityDefault((PsiModifierListOwner)element, placeTargetTypes, place, false);
        if (result != null) {
          return result;
        }
      }

      if (element instanceof PsiClassOwner) {
        String packageName = ((PsiClassOwner)element).getPackageName();
        PsiJavaPackage psiPackage = JavaPsiFacade.getInstance(element.getProject()).findPackage(packageName);
        NullabilityAnnotationInfo fromPackage = findNullityDefaultOnPackage(placeTargetTypes, psiPackage, place);
        if (fromPackage != null) {
          return fromPackage;
        }
        return findNullityDefaultOnModule(placeTargetTypes, element);
      }

      element = element.getContext();
    }
    return null;
  }

  protected @Nullable NullabilityAnnotationInfo findNullityDefaultOnModule(@Nonnull PsiAnnotation.TargetType[] types,
                                                                           @Nonnull PsiElement element) {
    return null;
  }

  /**
   * @return an annotation (if any) with the given nullability semantics on the given declaration or its type. In case of conflicts,
   * type annotations are preferred.
   */
  public @Nullable PsiAnnotation findExplicitNullabilityAnnotation(@Nonnull PsiModifierListOwner owner,
                                                                   @Nonnull Collection<Nullability> nullabilities) {
    NullabilityAnnotationDataHolder holder = getAllNullabilityAnnotationsWithNickNames();
    Set<String> filteredSet =
      holder.qualifiedNames().stream().filter(qName -> nullabilities.contains(holder.getNullability(qName))).collect(Collectors.toSet());
    NullabilityAnnotationDataHolder filtered = new NullabilityAnnotationDataHolder() {
      @Override
      public Set<String> qualifiedNames() {
        return filteredSet;
      }

      @Override
      public @Nullable Nullability getNullability(String annotation) {
        Nullability origNullability = holder.getNullability(annotation);
        return nullabilities.contains(origNullability) ? origNullability : null;
      }
    };
    NullabilityAnnotationInfo result = findPlainAnnotation(owner, false, false, filtered);
    return result == null || !nullabilities.contains(result.getNullability()) ? null : result.getAnnotation();
  }

  private @Nullable NullabilityAnnotationInfo findPlainAnnotation(@Nonnull PsiModifierListOwner owner,
                                                                  boolean checkBases,
                                                                  boolean skipExternal,
                                                                  NullabilityAnnotationDataHolder annotations) {
    AnnotationAndOwner memberAnno;
    if (checkBases && owner instanceof PsiMethod) {
      memberAnno = findAnnotationAndOwnerInHierarchy(owner, annotations.qualifiedNames(), skipExternal);
    }
    else {
      PsiAnnotation annotation = findAnnotation(owner, annotations.qualifiedNames(), skipExternal);
      memberAnno = annotation == null ? null : new AnnotationAndOwner(owner, annotation);
    }
    PsiType type = PsiUtil.getTypeByPsiElement(owner);
    if (memberAnno != null && type instanceof PsiArrayType && !isInferredAnnotation(memberAnno.annotation) &&
      !isExternalAnnotation(memberAnno.annotation) && AnnotationTargetUtil.isTypeAnnotation(memberAnno.annotation)) {
      // Ambiguous TYPE_USE annotation on array type: we consider that it annotates an array component instead.
      // ignore inferred/external annotations here, as they are applicable to PsiModifierListOwner only, regardless of target
      memberAnno = null;
    }
    if (memberAnno != null) {
      Nullability nullability = annotations.getNullability(memberAnno.annotation.getQualifiedName());
      if (nullability == null) return null;
      nullability = correctNullability(nullability, memberAnno.annotation);
      if (type != null) {
        for (PsiAnnotation typeAnno : type.getApplicableAnnotations()) {
          if (typeAnno == memberAnno.annotation) continue;
          Nullability typeNullability = annotations.getNullability(typeAnno.getQualifiedName());
          if (typeNullability == null) continue;
          if (typeNullability != nullability) {
            return null;
          }
          // Prefer type annotation over inherited annotation; necessary for Nullable/NotNull inspection
          memberAnno = new AnnotationAndOwner(owner, typeAnno);
          break;
        }
      }
      return new NullabilityAnnotationInfo(memberAnno.annotation, nullability, memberAnno.owner == owner ? null : memberAnno.owner, false);
    }
    if (type instanceof PsiPrimitiveType) return null;
    return findAnnotationInTypeHierarchy(type, annotations);
  }

  /**
   * @param type           type to check
   * @param qualifiedNames annotation qualified names of TYPE_USE annotations to look for
   * @return found type annotation, or null if not found. For type parameter types upper bound annotations are also checked
   */
  @Contract("null, _ -> null")
  private @Nullable NullabilityAnnotationInfo findAnnotationInTypeHierarchy(@Nullable PsiType type,
                                                                            @Nonnull NullabilityAnnotationDataHolder qualifiedNames) {
    if (type == null) return null;
    Ref<NullabilityAnnotationInfo> result = Ref.create(null);
    InheritanceUtil.processSuperTypes(type, true, eachType -> {
      for (PsiAnnotation annotation : eachType.getAnnotations()) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedNames.qualifiedNames().contains(qualifiedName)) {
          Nullability nullability = qualifiedNames.getNullability(qualifiedName);
          if (nullability != null) {
            nullability = correctNullability(nullability, annotation);
            result.set(new NullabilityAnnotationInfo(annotation, nullability, false));
          }
          return false;
        }
      }
      if (!(eachType instanceof PsiClassType)) return true;
      PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(eachType);
      if (!(targetClass instanceof PsiTypeParameter)) return false;
      if (targetClass.getExtendsListTypes().length == 0) {
        NullabilityAnnotationInfo info = findNullabilityDefault(targetClass, PsiAnnotation.TargetType.TYPE_PARAMETER);
        if (info != null) {
          result.set(info);
          return false;
        }
      }
      return true;
    });
    return result.get();
  }

  @Nonnull
  protected Nullability correctNullability(@Nonnull Nullability nullability, @Nonnull PsiAnnotation annotation) {
    return nullability;
  }

  @Nonnull
  protected List<String> getNullablesWithNickNames() {
    return getNullables();
  }

  @Nonnull
  protected List<String> getNotNullsWithNickNames() {
    return getNotNulls();
  }

  @Nonnull
  protected abstract NullabilityAnnotationDataHolder getAllNullabilityAnnotationsWithNickNames();

  protected boolean hasHardcodedContracts(@Nonnull PsiElement element) {
    return false;
  }

  @Nullable
  private static PsiType getOwnerType(@Nonnull PsiModifierListOwner owner) {
    if (owner instanceof PsiVariable) {
      return ((PsiVariable)owner).getType();
    }
    if (owner instanceof PsiMethod) {
      return ((PsiMethod)owner).getReturnType();
    }
    return null;
  }

  public boolean isNullable(@Nonnull PsiModifierListOwner owner, boolean checkBases) {
    NullabilityAnnotationInfo info = findEffectiveNullabilityInfo(owner);
    return info != null && info.getNullability() == Nullability.NULLABLE && (checkBases || info.getInheritedFrom() == null);
  }

  public boolean isNotNull(@Nonnull PsiModifierListOwner owner, boolean checkBases) {
    NullabilityAnnotationInfo info = findEffectiveNullabilityInfo(owner);
    return info != null && info.getNullability() == Nullability.NOT_NULL && (checkBases || info.getInheritedFrom() == null);
  }

  /**
   * @param context place in PSI tree
   * @return default nullability for type-use elements at given place
   */
  @Nullable
  public NullabilityAnnotationInfo findDefaultTypeUseNullability(@Nullable PsiElement context) {
    if (context == null) {
      return null;
    }
    if (context.getParent() instanceof PsiTypeElement && context.getParent().getParent() instanceof PsiLocalVariable) {
      return null;
    }
    return findNullabilityDefault(context, PsiAnnotation.TargetType.TYPE_USE);
  }

  @Nullable
  NullabilityAnnotationInfo findNullityDefaultInHierarchy(@Nonnull PsiModifierListOwner owner) {
    PsiAnnotation.TargetType[] placeTargetTypes = AnnotationTargetUtil.getTargetsForLocation(owner.getModifierList());

    PsiElement element = owner.getParent();
    while (element != null) {
      if (element instanceof PsiModifierListOwner) {
        NullabilityAnnotationInfo result = getNullityDefault((PsiModifierListOwner)element, placeTargetTypes, owner, false);
        if (result != null) {
          return result;
        }
      }

      if (element instanceof PsiClassOwner) {
        String packageName = ((PsiClassOwner)element).getPackageName();
        return findNullityDefaultOnPackage(placeTargetTypes, JavaPsiFacade.getInstance(element.getProject()).findPackage(packageName),
                                           owner);
      }

      element = element.getContext();
    }
    return null;
  }

  @Nullable
  private NullabilityAnnotationInfo findNullityDefaultOnPackage(@Nonnull PsiAnnotation.TargetType[] placeTargetTypes,
                                                                @Nullable PsiJavaPackage psiPackage,
                                                                PsiElement context) {
    boolean superPackage = false;
    while (psiPackage != null) {
      NullabilityAnnotationInfo onPkg = getNullityDefault(psiPackage, placeTargetTypes, context, superPackage);
      if (onPkg != null) {
        return onPkg;
      }
      superPackage = true;
      psiPackage = psiPackage.getParentPackage();
    }
    return null;
  }

  @Nullable
  protected abstract NullabilityAnnotationInfo getNullityDefault(@Nonnull PsiModifierListOwner container,
                                                                 @Nonnull PsiAnnotation.TargetType[] placeTargetTypes,
                                                                 PsiElement context, boolean superPackage);

  @Nonnull
  public abstract List<String> getNullables();

  @Nonnull
  public abstract List<String> getNotNulls();

  /**
   * Returns true if given element is known to be nullable
   *
   * @param owner element to check
   * @return true if given element is known to be nullable
   */
  public static boolean isNullable(@Nonnull PsiModifierListOwner owner) {
    return getNullability(owner) == Nullability.NULLABLE;
  }

  /**
   * Returns true if given element is known to be non-nullable
   *
   * @param owner element to check
   * @return true if given element is known to be non-nullable
   */
  public static boolean isNotNull(@Nonnull PsiModifierListOwner owner) {
    return getNullability(owner) == Nullability.NOT_NULL;
  }

  /**
   * Returns nullability of given element defined by annotations.
   *
   * @param owner element to find nullability for
   * @return found nullability; {@link Nullability#UNKNOWN} if not specified or non-applicable
   */
  @Nonnull
  public static Nullability getNullability(@Nonnull PsiModifierListOwner owner) {
    NullabilityAnnotationInfo info = getInstance(owner.getProject()).findEffectiveNullabilityInfo(owner);
    return info == null ? Nullability.UNKNOWN : info.getNullability();
  }

  @Nonnull
  public abstract List<String> getInstrumentedNotNulls();

  public abstract void setInstrumentedNotNulls(@Nonnull List<String> names);

  /**
   * Checks if given annotation specifies the nullability (either nullable or not-null)
   *
   * @param annotation annotation to check
   * @return true if given annotation specifies nullability
   */
  public static boolean isNullabilityAnnotation(@Nonnull PsiAnnotation annotation) {
    return getInstance(annotation.getProject()).getAllNullabilityAnnotationsWithNickNames()
                                               .getNullability(annotation.getQualifiedName()) != null;
  }
}