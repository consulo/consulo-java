/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package consulo.java.diagram.impl;

import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.diagram.AbstractDiagramVisibilityManager;
import consulo.diagram.VisibilityLevel;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/**
 * @author Konstantin Bulenkov
 */
public class JavaDiagramVisibilityManager extends AbstractDiagramVisibilityManager {
    private static final VisibilityLevel[] LEVELS = {
        new VisibilityLevel(PsiModifier.PUBLIC),
        new VisibilityLevel(PsiModifier.PACKAGE_LOCAL, "package"),
        new VisibilityLevel(PsiModifier.PROTECTED),
        new VisibilityLevel(PsiModifier.PRIVATE, "All")
    };

    private static final Comparator<VisibilityLevel> COMPARATOR = (o1, o2) -> {
        int ind1 = ArrayUtil.indexOf(LEVELS, o1);
        int ind2 = ArrayUtil.indexOf(LEVELS, o2);
        if (ind1 == ind2) {
            return 0;
        }
        return ind1 < 0 ? 1 : ind1 - ind2;
    };

    @Override
    public VisibilityLevel[] getVisibilityLevels() {
        return LEVELS;
    }

    @Override
    public @Nullable VisibilityLevel getVisibilityLevel(Object element) {
        if (element instanceof PsiModifierListOwner owner) {
            if (owner.hasModifierProperty(PsiModifier.PUBLIC)) {
                return LEVELS[0];
            }
            if (owner.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
                return LEVELS[1];
            }
            if (owner.hasModifierProperty(PsiModifier.PROTECTED)) {
                return LEVELS[2];
            }
            if (owner.hasModifierProperty(PsiModifier.PRIVATE)) {
                return LEVELS[3];
            }
        }
        return null;
    }

    @Override
    public Comparator<VisibilityLevel> getComparator() {
        return COMPARATOR;
    }
}
