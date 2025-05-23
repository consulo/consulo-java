/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.psi;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.jvm.JvmTypeDeclaration;
import com.intellij.java.language.jvm.types.JvmReferenceType;
import com.intellij.java.language.jvm.types.JvmSubstitutor;
import com.intellij.java.language.jvm.types.JvmType;
import com.intellij.java.language.jvm.types.JvmTypeResolveResult;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.collection.ArrayFactory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a class type.
 *
 * @author max
 */
public abstract class PsiClassType extends PsiType implements JvmReferenceType {
  public static final PsiClassType[] EMPTY_ARRAY = new PsiClassType[0];
  public static final ArrayFactory<PsiClassType> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiClassType[count];

  protected final LanguageLevel myLanguageLevel;

  protected PsiClassType(LanguageLevel languageLevel) {
    this(languageLevel, PsiAnnotation.EMPTY_ARRAY);
  }

  protected PsiClassType(LanguageLevel languageLevel, @Nonnull PsiAnnotation[] annotations) {
    super(annotations);
    myLanguageLevel = languageLevel;
  }

  public PsiClassType(LanguageLevel languageLevel, @Nonnull TypeAnnotationProvider provider) {
    super(provider);
    myLanguageLevel = languageLevel;
  }

  @Nonnull
  @Override
  public PsiClassType annotate(@Nonnull TypeAnnotationProvider provider) {
    return (PsiClassType)super.annotate(provider);
  }

  /**
   * Resolves the class reference and returns the resulting class.
   *
   * @return the class instance, or null if the reference resolve failed.
   */
  @Nullable
  public abstract PsiClass resolve();

  /**
   * Returns the non-qualified name of the class referenced by the type.
   *
   * @return the name of the class.
   */
  public abstract String getClassName();

  /**
   * Returns the list of type arguments for the type.
   *
   * @return the array of type arguments, or an empty array if the type does not point to a generic class or interface.
   */
  @Nonnull
  public abstract PsiType[] getParameters();

  public int getParameterCount() {
    return getParameters().length;
  }

  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof PsiClassType)) {
      return obj instanceof PsiCapturedWildcardType &&
        ((PsiCapturedWildcardType)obj).getLowerBound().equalsToText(CommonClassNames.JAVA_LANG_OBJECT) &&
        equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
    }
    PsiClassType otherClassType = (PsiClassType)obj;

    String className = getClassName();
    String otherClassName = otherClassType.getClassName();
    if (!Objects.equals(className, otherClassName)) return false;

    if (getParameterCount() != otherClassType.getParameterCount()) return false;

    final ClassResolveResult result = resolveGenerics();
    final ClassResolveResult otherResult = otherClassType.resolveGenerics();
    if (result == otherResult) return true;

    final PsiClass aClass = result.getElement();
    final PsiClass otherClass = otherResult.getElement();
    if (aClass == null || otherClass == null) {
      return aClass == otherClass;
    }
    return aClass.getManager().areElementsEquivalent(aClass, otherClass) && PsiUtil.equalOnEquivalentClasses(this,
                                                                                                             aClass,
                                                                                                             otherClassType,
                                                                                                             otherClass);
  }

  /**
   * Checks if the class type has any parameters with no substituted arguments.
   *
   * @return true if the class type has non-substituted parameters, false otherwise
   */
  public boolean hasParameters() {
    final ClassResolveResult resolveResult = resolveGenerics();
    PsiClass aClass = resolveResult.getElement();
    if (aClass == null) {
      return false;
    }
    boolean hasParams = false;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(aClass)) {
      if (resolveResult.getSubstitutor().substitute(parameter) == null) {
        return false;
      }
      hasParams = true;
    }
    return hasParams;
  }

  /**
   * Checks if the class type has any parameters which are not unbounded wildcards (and not extends-wildcard with the same bound as corresponding type parameter bound)
   * and do not have substituted arguments.
   *
   * @return true if the class type has nontrivial non-substituted parameters, false otherwise
   */
  public boolean hasNonTrivialParameters() {
    final ClassResolveResult resolveResult = resolveGenerics();
    PsiClass aClass = resolveResult.getElement();
    if (aClass == null) {
      return false;
    }
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(aClass)) {
      PsiType type = resolveResult.getSubstitutor().substitute(parameter);
      if (type != null) {
        if (!(type instanceof PsiWildcardType)) {
          return true;
        }
        final PsiType bound = ((PsiWildcardType)type).getBound();
        if (bound != null) {
          if (((PsiWildcardType)type).isExtends()) {
            final PsiClass superClass = parameter.getSuperClass();
            if (superClass != null && PsiUtil.resolveClassInType(bound) == superClass) {
              continue;
            }
          }
          return true;
        }
      }
    }
    return false;
  }

  public int hashCode() {
    final String className = getClassName();
    if (className == null) {
      return 0;
    }
    return className.hashCode();
  }

  @Override
  @Nonnull
  public PsiType[] getSuperTypes() {
    ClassResolveResult resolveResult = resolveGenerics();
    PsiClass aClass = resolveResult.getElement();
    if (aClass == null) {
      return EMPTY_ARRAY;
    }

    PsiClassType[] superTypes = aClass.getSuperTypes();
    PsiType[] substitutionResults = createArray(superTypes.length);
    for (int i = 0; i < superTypes.length; i++) {
      substitutionResults[i] = resolveResult.getSubstitutor().substitute(superTypes[i]);
    }
    return substitutionResults;
  }

  /**
   * Checks whether the specified resolve result represents a raw type. <br>
   * Raw type is a class type for a class <i>with type parameters</i> which does not assign
   * any value to them. If a class does not have any type parameters, it cannot generate any raw type.
   */
  public static boolean isRaw(ClassResolveResult resolveResult) {
    PsiClass psiClass = resolveResult.getElement();
    return psiClass != null && PsiUtil.isRawSubstitutor(psiClass, resolveResult.getSubstitutor());
  }

  /**
   * Checks whether this type is a raw type. <br>
   * Raw type is a class type for a class <i>with type parameters</i> which does not assign
   * any value to them. If a class does not have any type parameters, it cannot generate any raw type.
   */
  public boolean isRaw() {
    return isRaw(resolveGenerics());
  }

  /**
   * Returns the resolve result containing the class referenced by the class type and the
   * substitutor which can substitute the values of type arguments used in the class type
   * declaration.
   *
   * @return the resolve result instance.
   */
  @Nonnull
  public abstract ClassResolveResult resolveGenerics();

  /**
   * Returns the raw type (with no values assigned to type parameters) corresponding to this type.
   *
   * @return the raw type instance.
   */
  @Nonnull
  public abstract PsiClassType rawType();

  /**
   * Overrides {@link PsiType#getResolveScope()} to narrow specify @NotNull.
   */
  @Override
  @Nonnull
  public abstract GlobalSearchScope getResolveScope();


  @Override
  public <A> A accept(@Nonnull PsiTypeVisitor<A> visitor) {
    return visitor.visitClassType(this);
  }

  @Nonnull
  public abstract LanguageLevel getLanguageLevel();

  /**
   * Functional style setter preserving original type's language level
   *
   * @param languageLevel level to obtain class type with
   * @return type with requested language level
   */
  @Nonnull
  @Contract(pure = true)
  public abstract PsiClassType setLanguageLevel(@Nonnull LanguageLevel languageLevel);

  @Nonnull
  @Override
  public String getName() {
    return getClassName();
  }

  /**
   * If class-type is created from the explicit reference in the code returns that reference.
   *
   * @return reference which the type is created from. Returns null if not applicable.
   */
  @Nullable
  public PsiElement getPsiContext() {
    return null;
  }

  @Nullable
  @Override
  public JvmTypeResolveResult resolveType() {
    ClassResolveResult resolveResult = resolveGenerics();
    PsiClass clazz = resolveResult.getElement();
    return clazz == null ? null : new JvmTypeResolveResult() {

      private final JvmSubstitutor mySubstitutor = new PsiJvmConversionHelper.PsiJvmSubstitutor(resolveResult.getSubstitutor());

      @Nonnull
      @Override
      public JvmTypeDeclaration getDeclaration() {
        return clazz;
      }

      @Nonnull
      @Override
      public JvmSubstitutor getSubstitutor() {
        return mySubstitutor;
      }
    };
  }

  @Nonnull
  @Override
  public Iterable<JvmType> typeArguments() {
    return Arrays.asList(getParameters());
  }

  /**
   * Represents the result of resolving a reference to a Java class.
   */
  public interface ClassResolveResult extends JavaResolveResult {
    @Override
    PsiClass getElement();

    /**
     * @return human-readable inference error if resolve of the class type involves type inference.
     * Currently, the only possibility for this is inference in deconstruction pattern.
     */
    @Nullable
    default String getInferenceError() {
      return null;
    }

    ClassResolveResult EMPTY = new ClassResolveResult() {
      @Override
      public PsiClass getElement() {
        return null;
      }

      @Nonnull
      @Override
      public PsiSubstitutor getSubstitutor() {
        return PsiSubstitutor.EMPTY;
      }

      @Override
      public boolean isValidResult() {
        return false;
      }

      @Override
      public boolean isAccessible() {
        return false;
      }

      @Override
      public boolean isStaticsScopeCorrect() {
        return false;
      }

      @Override
      public PsiElement getCurrentFileResolveScope() {
        return null;
      }

      @Override
      public boolean isPackagePrefixPackageReference() {
        return false;
      }
    };
  }

  public abstract static class Stub extends PsiClassType {
    protected Stub(LanguageLevel languageLevel, @Nonnull PsiAnnotation[] annotations) {
      super(languageLevel, annotations);
    }

    protected Stub(LanguageLevel languageLevel, @Nonnull TypeAnnotationProvider annotations) {
      super(languageLevel, annotations);
    }

    @Nonnull
    @Override
    public final String getPresentableText() {
      return getPresentableText(false);
    }

    @Nonnull
    @Override
    public abstract String getPresentableText(boolean annotated);

    @Nonnull
    @Override
    public final String getCanonicalText() {
      return getCanonicalText(false);
    }

    @Nonnull
    @Override
    public abstract String getCanonicalText(boolean annotated);
  }
}