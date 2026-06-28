/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import consulo.fileEditor.structureView.tree.ActionPresentation;
import consulo.fileEditor.structureView.tree.Sorter;
import consulo.ide.localize.IdeLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;

import java.util.Comparator;

public class VisibilitySorter implements Sorter {
    public static final Sorter INSTANCE = new VisibilitySorter();

    private static final ActionPresentation PRESENTATION = new ActionPresentation() {
        @Override
        public LocalizeValue getText() {
            return IdeLocalize.actionStructureviewSortByVisibility();
        }

        @Override
        public LocalizeValue getDescription() {
            return LocalizeValue.empty();
        }

        @Override
        public Image getIcon() {
            return PlatformIconGroup.objectbrowserVisibilitysort();
        }
    };

    public static final String ID = "VISIBILITY_SORTER";

    @Override
    public Comparator getComparator() {
        return VisibilityComparator.IMSTANCE;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public ActionPresentation getPresentation() {
        return PRESENTATION;
    }

    @Override
    public String getName() {
        return ID;
    }
}
