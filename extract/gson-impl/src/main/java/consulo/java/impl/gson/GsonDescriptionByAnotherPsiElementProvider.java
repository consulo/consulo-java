/*
 * Copyright 2013-2015 must-be.org
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

package consulo.java.impl.gson;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.psi.impl.source.PsiImmediateClassType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.RecursionManager;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.json.validation.NativeArray;
import consulo.json.validation.descriptionByAnotherPsiElement.DescriptionByAnotherPsiElementProvider;
import consulo.json.validation.descriptor.JsonObjectDescriptor;
import consulo.json.validation.descriptor.JsonPropertyDescriptor;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * @author VISTALL
 * @since 12.11.2015
 */
@ExtensionImpl
public class GsonDescriptionByAnotherPsiElementProvider implements DescriptionByAnotherPsiElementProvider<PsiClass> {
  public static class PropertyType {
    private final boolean myNullable;
    private final Object myValue;

    public PropertyType(Object value) {
      this(true, value);
    }

    public PropertyType(boolean nullable, Object value) {
      myNullable = nullable;
      myValue = value;
    }
  }

  @Nonnull
  @Override
  public String getId() {
    return "GSON";
  }

  @Nonnull
  @Override
  public String getPsiElementName() {
    return "Class";
  }

  @RequiredReadAction
  @Nonnull
  @Override
  public String getIdFromPsiElement(@Nonnull PsiClass psiClass) {
    return psiClass.getQualifiedName();
  }

  @RequiredReadAction
  @Nullable
  @Override
  public PsiClass getPsiElementById(@Nonnull String s, @Nonnull Project project) {
    return JavaPsiFacade.getInstance(project).findClass(s, GlobalSearchScope.allScope(project));
  }

  @RequiredUIAccess
  @Nullable
  @Override
  public PsiClass chooseElement(@Nonnull Project project) {
    TreeClassChooser classChooser = TreeClassChooserFactory.getInstance(project).createAllProjectScopeChooser("Choose class");
    classChooser.showDialog();
    return classChooser.getSelected();
  }

  @RequiredReadAction
  @Override
  public boolean isAvailable(@Nonnull Project project) {
    return ModuleExtensionHelper.getInstance(project).hasModuleExtension(JavaModuleExtension.class) && getPsiElementById("com.google.gson.Gson", project) != null;
  }

  @Override
  public void fillRootObject(@Nonnull PsiClass psiClass, @Nonnull JsonObjectDescriptor jsonObjectDescriptor) {
    PropertyType type = toType(psiClass.getProject(), null, new PsiImmediateClassType(psiClass, PsiSubstitutor.EMPTY));

    if (type != null && type.myValue instanceof JsonObjectDescriptor) {
      for (Map.Entry<String, JsonPropertyDescriptor> entry : ((JsonObjectDescriptor) type.myValue).getProperties().entrySet()) {
        jsonObjectDescriptor.getProperties().put(entry.getKey(), entry.getValue());
      }
    }
  }

  @Nullable
  private static PropertyType toType(@Nonnull Project project, @Nullable PsiField field, @Nonnull PsiType type) {
    if (PsiType.BYTE.equals(type)) {
      return new PropertyType(false, Number.class);
    } else if (PsiType.SHORT.equals(type)) {
      return new PropertyType(false, Number.class);
    } else if (PsiType.INT.equals(type)) {
      return new PropertyType(false, Number.class);
    } else if (PsiType.LONG.equals(type)) {
      return new PropertyType(false, Number.class);
    } else if (PsiType.FLOAT.equals(type)) {
      return new PropertyType(false, Number.class);
    } else if (PsiType.DOUBLE.equals(type)) {
      return new PropertyType(false, Number.class);
    } else if (PsiType.BOOLEAN.equals(type)) {
      return new PropertyType(false, Boolean.class);
    } else if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType) type).resolveGenerics();
      PsiClass psiClass = classResolveResult.getElement();
      if (psiClass != null) {
        String qualifiedName = psiClass.getQualifiedName();
        if (CommonClassNames.JAVA_LANG_STRING.equals(qualifiedName)) {
          return new PropertyType(String.class);
        } else if (CommonClassNames.JAVA_LANG_BOOLEAN.equals(qualifiedName) || "java.util.concurrent.atomic.AtomicBoolean".equals(qualifiedName)) {
          return new PropertyType(Boolean.class);
        } else if (CommonClassNames.JAVA_LANG_BYTE.equals(qualifiedName)) {
          return new PropertyType(Number.class);
        } else if (CommonClassNames.JAVA_LANG_SHORT.equals(qualifiedName)) {
          return new PropertyType(Number.class);
        } else if (CommonClassNames.JAVA_LANG_INTEGER.equals(qualifiedName) || "java.util.concurrent.atomic.AtomicInteger".equals(qualifiedName)) {
          return new PropertyType(Number.class);
        } else if (CommonClassNames.JAVA_LANG_LONG.equals(qualifiedName) || "java.util.concurrent.atomic.AtomicLong".equals(qualifiedName)) {
          return new PropertyType(Number.class);
        } else if (CommonClassNames.JAVA_LANG_FLOAT.equals(qualifiedName)) {
          return new PropertyType(Number.class);
        } else if (CommonClassNames.JAVA_LANG_DOUBLE.equals(qualifiedName) || "java.util.concurrent.atomic.AtomicDouble".equals(qualifiedName)) {
          return new PropertyType(Number.class);
        } else if ("java.util.concurrent.atomic.AtomicIntegerArray".equals(qualifiedName)) {
          return new PropertyType(new NativeArray(Number.class));
        } else if ("java.util.concurrent.atomic.AtomicLongArray".equals(qualifiedName)) {
          return new PropertyType(new NativeArray(Number.class));
        } else if ("java.util.concurrent.atomic.AtomicDoubleArray".equals(qualifiedName)) {
          return new PropertyType(new NativeArray(Number.class));
        }

        PsiClass collectionClass = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_UTIL_COLLECTION, GlobalSearchScope.allScope(project));
        if (collectionClass != null) {
          if (InheritanceUtil.isInheritorOrSelf(psiClass, collectionClass, true)) {
            PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(collectionClass, psiClass, classResolveResult.getSubstitutor());
            Collection<PsiType> values = superClassSubstitutor.getSubstitutionMap().values();
            if (!values.isEmpty()) {
              PsiType firstItem = ContainerUtil.getFirstItem(values);
              if (firstItem != null) {
                return toType(project, field, new PsiArrayType(firstItem));
              }
            }

            return new PropertyType(new NativeArray(Object.class));
          }
        }

        PsiClass mapClass = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_UTIL_MAP, GlobalSearchScope.allScope(project));
        if (mapClass != null) {
          if (InheritanceUtil.isInheritorOrSelf(psiClass, mapClass, true)) {
            PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(mapClass, psiClass, classResolveResult.getSubstitutor());
            Collection<PsiType> values = superClassSubstitutor.getSubstitutionMap().values();
            if (values.size() == 2) {
              PsiTypeParameter psiTypeParameter = mapClass.getTypeParameters()[1];
              PsiType valueType = superClassSubstitutor.substitute(psiTypeParameter);
              assert valueType != null;

              JsonObjectDescriptor objectDescriptor = new JsonObjectDescriptor();
              PropertyType valueJsonType = toType(project, field, valueType);
              addIfNotNull(objectDescriptor, valueJsonType, null);
              return new PropertyType(objectDescriptor);
            }

            return new PropertyType(new NativeArray(Object.class));
          }
        }

        JsonObjectDescriptor objectDescriptor = new JsonObjectDescriptor();
        PsiField[] allFields = psiClass.getAllFields();
        for (PsiField psiField : allFields) {
          if (psiField == field || psiField.hasModifierProperty(PsiModifier.STATIC)) {
            continue;
          }

          PropertyType classType = RecursionManager.doPreventingRecursion(GsonDescriptionByAnotherPsiElementProvider.class, false, () -> toType(project, psiField, psiField.getType()));

          addIfNotNull(objectDescriptor, classType, psiField);
        }

        return new PropertyType(objectDescriptor);
      }
    } else if (type instanceof PsiArrayType) {
      PsiType componentType = ((PsiArrayType) type).getComponentType();

      PropertyType propertyType = toType(project, field, componentType);
      if (propertyType == null) {
        return null;
      }
      return new PropertyType(new NativeArray(propertyType.myValue));
    }
    return null;
  }

  private static void addIfNotNull(@Nonnull JsonObjectDescriptor objectDescriptor, @Nullable PropertyType propertyType, @Nullable PsiField navElement) {
    if (propertyType == null) {
      return;
    }

    String propertyName = navElement == null ? null : getPropertyNameFromField(navElement);
    JsonPropertyDescriptor propertyDescriptor = null;

    Object classType = propertyType.myValue;
    if (classType instanceof Class) {
      propertyDescriptor = objectDescriptor.addProperty(propertyName, (Class<?>) classType);
    } else if (classType instanceof NativeArray) {
      propertyDescriptor = objectDescriptor.addProperty(propertyName, (NativeArray) classType);
    } else if (classType instanceof JsonObjectDescriptor) {
      propertyDescriptor = objectDescriptor.addProperty(propertyName, (JsonObjectDescriptor) classType);
    }

    if (propertyDescriptor != null && navElement != null) {
      propertyDescriptor.setNavigationElement(navElement);
      if (navElement.isDeprecated()) {
        propertyDescriptor.deprecated();
      }
      if (!propertyType.myNullable) {
        propertyDescriptor.notNull();
      }
    }
  }

  @Nonnull
  private static String getPropertyNameFromField(@Nonnull PsiField field) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(field, "com.google.gson.annotations.SerializedName");
    if (annotation != null) {
      String value = AnnotationUtil.getStringAttributeValue(annotation, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value != null) {
        return value;
      }
    }
    return field.getName();
  }
}
