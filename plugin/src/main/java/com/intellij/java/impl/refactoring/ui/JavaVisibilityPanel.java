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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 07.06.2002
 * Time: 18:16:19
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.refactoring.ui;

import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.ui.VisibilityPanelBase;
import consulo.ui.Component;
import consulo.ui.RadioButton;
import consulo.ui.ValueComponent;
import consulo.ui.ValueGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.ComponentEventListener;
import consulo.ui.event.ValueComponentEvent;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class JavaVisibilityPanel extends VisibilityPanelBase<String> {
    private RadioButton myRbAsIs;
    private RadioButton myRbEscalate;
    private final RadioButton myRbPrivate;
    private final RadioButton myRbProtected;
    private final RadioButton myRbPackageLocal;
    private final RadioButton myRbPublic;

    private final LabeledLayout myLayout;

    public JavaVisibilityPanel(boolean hasAsIs, final boolean hasEscalate) {
        VerticalLayout layout = VerticalLayout.create();

        ValueGroup<Boolean> bg = ValueGroup.createBool();

        ComponentEventListener<ValueComponent<Boolean>, ValueComponentEvent<Boolean>> listener = e -> {
            myEventDispatcher.getMulticaster().visibilityChanged(this);
        };

        if (hasEscalate) {
            myRbEscalate = RadioButton.create(RefactoringLocalize.visibilityEscalate());
            myRbEscalate.addValueListener(listener);
            layout.add(myRbEscalate);
            bg.add(myRbEscalate);
        }

        if (hasAsIs) {
            myRbAsIs = RadioButton.create(RefactoringLocalize.visibilityAsIs());
            myRbAsIs.addValueListener(listener);
            layout.add(myRbAsIs);
            bg.add(myRbAsIs);
        }

        myRbPrivate = RadioButton.create(RefactoringLocalize.visibilityPrivate());
        myRbPrivate.addValueListener(listener);
        layout.add(myRbPrivate);
        bg.add(myRbPrivate);

        myRbPackageLocal = RadioButton.create(RefactoringLocalize.visibilityPackageLocal());
        myRbPackageLocal.addValueListener(listener);
        layout.add(myRbPackageLocal);
        bg.add(myRbPackageLocal);

        myRbProtected = RadioButton.create(RefactoringLocalize.visibilityProtected());
        myRbProtected.addValueListener(listener);
        layout.add(myRbProtected);
        bg.add(myRbProtected);

        myRbPublic = RadioButton.create(RefactoringLocalize.visibilityPublic());
        myRbPublic.addValueListener(listener);
        layout.add(myRbPublic);
        bg.add(myRbPublic);

        myLayout = LabeledLayout.create(RefactoringLocalize.visibilityBorderTitle(), layout);
    }

    @Override
    @Nullable
    @RequiredUIAccess
    public String getVisibility() {
        if (myRbPublic.getValueOrError()) {
            return PsiModifier.PUBLIC;
        }
        if (myRbPackageLocal.getValueOrError()) {
            return PsiModifier.PACKAGE_LOCAL;
        }
        if (myRbProtected.getValueOrError()) {
            return PsiModifier.PROTECTED;
        }
        if (myRbPrivate.getValueOrError()) {
            return PsiModifier.PRIVATE;
        }
        if (myRbEscalate != null && myRbEscalate.getValueOrError()) {
            return VisibilityUtil.ESCALATE_VISIBILITY;
        }

        return null;
    }

    @Override
    public void setVisibility(@Nullable String visibility) {
        if (PsiModifier.PUBLIC.equals(visibility)) {
            myRbPublic.setValue(true);
        }
        else if (PsiModifier.PROTECTED.equals(visibility)) {
            myRbProtected.setValue(true);
        }
        else if (PsiModifier.PACKAGE_LOCAL.equals(visibility)) {
            myRbPackageLocal.setValue(true);
        }
        else if (PsiModifier.PRIVATE.equals(visibility)) {
            myRbPrivate.setValue(true);
        }
        else if (myRbEscalate != null) {
            myRbEscalate.setValue(true);
        }
        else if (myRbAsIs != null) {
            myRbAsIs.setValue(true);
        }
    }

    public void disableAllButPublic() {
        myRbPrivate.setEnabled(false);
        myRbProtected.setEnabled(false);
        myRbPackageLocal.setEnabled(false);
        if (myRbEscalate != null) {
            myRbEscalate.setEnabled(false);
        }
        if (myRbAsIs != null) {
            myRbAsIs.setEnabled(false);
        }
        myRbPublic.setEnabled(true);
        myRbPublic.setValue(true);
    }

    @Nonnull
    @Override
    public Component getComponent() {
        return myLayout;
    }
}
