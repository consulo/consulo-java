/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.presentation.java;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.CodeInsightColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.component.util.Iconable;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiFile;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.ui.ex.ColoredItemPresentation;
import consulo.ui.image.Image;

/**
 * @author yole
 */
@ExtensionImpl
public class ClassPresentationProvider implements ItemPresentationProvider<PsiClass> {
    @Override
    public Class<PsiClass> getItemClass() {
        return PsiClass.class;
    }

    @Override
    public ItemPresentation getPresentation(final PsiClass psiClass) {
        return new ColoredItemPresentation() {
            @Override
            @RequiredReadAction
            public String getPresentableText() {
                return ClassPresentationUtil.getNameForClass(psiClass, false).get();
            }

            @Override
            public String getLocationString() {
                PsiFile file = psiClass.getContainingFile();
                if (file instanceof PsiClassOwner classOwner) {
                    String packageName = classOwner.getPackageName();
                    return packageName.isEmpty() ? null : "(" + packageName + ")";
                }
                return null;
            }

            @Override
            public TextAttributesKey getTextAttributesKey() {
                if (psiClass.isDeprecated()) {
                    return CodeInsightColors.DEPRECATED_ATTRIBUTES;
                }
                return null;
            }

            @Override
            @RequiredReadAction
            public Image getIcon() {
                return IconDescriptorUpdaters.getIcon(psiClass, Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
            }
        };
    }
}
