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
package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPoint;
import consulo.component.extension.ExtensionPointName;

/**
 * @author anna
 * @since 2013-03-04
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class GetterSetterPrototypeProvider {
    public static final ExtensionPointName<GetterSetterPrototypeProvider> EP_NAME =
        ExtensionPointName.create(GetterSetterPrototypeProvider.class);

    public abstract boolean canGeneratePrototypeFor(PsiField field);

    public abstract PsiMethod[] generateGetters(PsiField field);

    public abstract PsiMethod[] generateSetters(PsiField field);

    public PsiMethod[] findGetters(PsiClass psiClass, String propertyName) {
        return null;
    }

    public String suggestGetterName(String propertyName) {
        return null;
    }

    public boolean isSimpleGetter(PsiMethod method, String oldPropertyName) {
        return false;
    }

    public abstract boolean isReadOnly(PsiField field);

    @RequiredWriteAction
    public static PsiMethod[] generateGetterSetters(PsiField field, boolean generateGetter) {
        return generateGetterSetters(field, generateGetter, true);
    }

    @RequiredWriteAction
    public static PsiMethod[] generateGetterSetters(PsiField field, boolean generateGetter, boolean invalidTemplate) {
        PsiMethod[] methods = field.getApplication().getExtensionPoint(GetterSetterPrototypeProvider.class).computeSafeIfAny(provider -> {
            if (provider.canGeneratePrototypeFor(field)) {
                return generateGetter ? provider.generateGetters(field) : provider.generateSetters(field);
            }
            return null;
        });
        if (methods != null) {
            return methods;
        }
        return new PsiMethod[]{
            generateGetter
                ? GenerateMembersUtil.generateGetterPrototype(field, invalidTemplate)
                : GenerateMembersUtil.generateSetterPrototype(field, invalidTemplate)
        };
    }

    public static boolean isReadOnlyProperty(PsiField field) {
        ExtensionPoint<GetterSetterPrototypeProvider> ep = field.getApplication().getExtensionPoint(GetterSetterPrototypeProvider.class);
        return ep.anyMatchSafe(provider -> provider.canGeneratePrototypeFor(field) && provider.isReadOnly(field)) || field.isFinal();
    }

    public static PsiMethod[] findGetters(PsiClass aClass, String propertyName, boolean isStatic) {
        if (!isStatic) {
            PsiMethod[] getterSetter = aClass.getApplication().getExtensionPoint(GetterSetterPrototypeProvider.class)
                .computeSafeIfAny(provider -> provider.findGetters(aClass, propertyName));
            if (getterSetter != null) {
                return getterSetter;
            }
        }
        PsiMethod propertyGetterSetter = PropertyUtil.findPropertyGetter(aClass, propertyName, isStatic, false);
        if (propertyGetterSetter != null) {
            return new PsiMethod[]{propertyGetterSetter};
        }
        return null;
    }

    public static String suggestNewGetterName(String oldPropertyName, String newPropertyName, PsiMethod method) {
        return method.getApplication().getExtensionPoint(GetterSetterPrototypeProvider.class).computeSafeIfAny(
            provider -> provider.isSimpleGetter(method, oldPropertyName) ? provider.suggestGetterName(newPropertyName) : null
        );
    }
}
