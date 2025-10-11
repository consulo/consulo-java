/*
 * Copyright 2006-2016 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.ig.InspectionGadgetsFix;
import consulo.annotation.component.ExtensionImpl;

@ExtensionImpl
public class BeforeClassOrAfterClassIsPublicStaticVoidNoArgInspection extends BeforeClassOrAfterClassIsPublicStaticVoidNoArgInspectionBase {
    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiMethod method = (PsiMethod) infos[0];
        String targetModifier = isJunit4Annotation((String) infos[1]) ? PsiModifier.PUBLIC : PsiModifier.PACKAGE_LOCAL;
        return new MakePublicStaticVoidFix(method, true, targetModifier);
    }
}
