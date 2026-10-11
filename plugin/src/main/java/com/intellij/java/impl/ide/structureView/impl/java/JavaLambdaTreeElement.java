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
package com.intellij.java.impl.ide.structureView.impl.java;

import consulo.annotation.access.RequiredReadAction;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import com.intellij.java.language.util.PsiLambdaNameHelper;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.DumbService;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.ui.image.Image;

import java.util.Collection;
import java.util.Collections;

public class JavaLambdaTreeElement extends JavaClassTreeElementBase<PsiLambdaExpression> {
    public static final JavaLambdaTreeElement[] EMPTY_ARRAY = {};

    private String myName;
    private String myFunctionalName;

    public JavaLambdaTreeElement(PsiLambdaExpression lambdaExpression) {
        super(false, lambdaExpression);
    }

    @Override
    public boolean isPublic() {
        return false;
    }

    @Override
    @RequiredReadAction
    public LocalizeValue getPresentableText() {
        if (myName != null) {
            return LocalizeValue.of(myName);
        }
        PsiLambdaExpression element = getElement();

        if (element != null) {
            myName = PsiLambdaNameHelper.getVMName(element);
            return LocalizeValue.of(myName);
        }
        return LocalizeValue.localizeTODO("Lambda");
    }

    @Override
    public boolean isSearchInLocationString() {
        return true;
    }

    @Override
    public String getLocationString() {
        if (myFunctionalName == null) {
            PsiLambdaExpression lambdaExpression = getElement();
            if (lambdaExpression != null && !DumbService.isDumb(lambdaExpression.getProject())) {
                PsiType interfaceType = lambdaExpression.getFunctionalInterfaceType();
                if (interfaceType != null) {
                    myFunctionalName = interfaceType.getPresentableText();
                }
            }
        }
        return myFunctionalName;
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return super.toString() + (myFunctionalName == null ? "" : " (" + getLocationString() + ")");
    }

    @Override
    public Collection<StructureViewTreeElement> getChildrenBase() {
        return Collections.emptyList();
    }

    @Override
    @RequiredReadAction
    public Image getIcon() {
        return PlatformIconGroup.nodesLambda();
    }
}
