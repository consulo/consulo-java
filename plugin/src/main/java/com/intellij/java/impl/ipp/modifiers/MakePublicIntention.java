/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.java.impl.ipp.modifiers;

import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * @author Bas Leijdekkers
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.MakePublicIntention", fileExtensions = "java", categories = {"Java", "Modifiers"})
public class MakePublicIntention extends ModifierIntention {

    @Override
    protected String getModifier() {
        return PsiModifier.PUBLIC;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.makePublicIntentionName();
    }
}
