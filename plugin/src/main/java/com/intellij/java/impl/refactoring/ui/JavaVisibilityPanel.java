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
import consulo.ui.RadioGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

public class JavaVisibilityPanel extends VisibilityPanelBase<String> {
    private static final String AS_IS = "AS_IS";

    private RadioButton myRbAsIs;
    private RadioButton myRbEscalate;
    private final RadioButton myRbPrivate;
    private final RadioButton myRbProtected;
    private final RadioButton myRbPackageLocal;
    private final RadioButton myRbPublic;

    private final LabeledLayout myLayout;

    private final RadioGroup<String> myVisibilityGroup;

    public JavaVisibilityPanel(boolean hasAsIs, boolean hasEscalate) {
        VerticalLayout layout = VerticalLayout.create();

        myVisibilityGroup = RadioGroup.create();
        myVisibilityGroup.addValueListener(s -> myEventDispatcher.getMulticaster().visibilityChanged(this));

        if (hasEscalate) {
            myRbEscalate = myVisibilityGroup.newButton(RefactoringLocalize.visibilityEscalate(), VisibilityUtil.ESCALATE_VISIBILITY);
            layout.add(myRbEscalate);
        }

        if (hasAsIs) {
            myRbAsIs = myVisibilityGroup.newButton(RefactoringLocalize.visibilityAsIs(), AS_IS);
            layout.add(myRbAsIs);
        }

        myRbPrivate = myVisibilityGroup.newButton(RefactoringLocalize.visibilityPrivate(), PsiModifier.PRIVATE);
        layout.add(myRbPrivate);

        myRbPackageLocal = myVisibilityGroup.newButton(RefactoringLocalize.visibilityPackageLocal(), PsiModifier.PACKAGE_LOCAL);
        layout.add(myRbPackageLocal);

        myRbProtected = myVisibilityGroup.newButton(RefactoringLocalize.visibilityProtected(), PsiModifier.PROTECTED);
        layout.add(myRbProtected);

        myRbPublic = myVisibilityGroup.newButton(RefactoringLocalize.visibilityPublic(), PsiModifier.PUBLIC);
        layout.add(myRbPublic);

        myVisibilityGroup.setValue(PsiModifier.PUBLIC);

        myLayout = LabeledLayout.create(RefactoringLocalize.visibilityBorderTitle(), layout);
    }

    @Override
    @Nullable
    @RequiredUIAccess
    public String getVisibility() {
        String value = myVisibilityGroup.getValueOrError();
        return switch (value) {
            case AS_IS -> null;
            default -> value;
        };
    }

    @Override
    public void setVisibility(@Nullable String visibility) {
        if (visibility != null) {
            myVisibilityGroup.setValue(visibility);
        }
        else if (myRbEscalate != null) {
            myVisibilityGroup.setValue(VisibilityUtil.ESCALATE_VISIBILITY);
        }
        else if (myRbAsIs != null) {
            myVisibilityGroup.setValue(AS_IS);
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

    @Override
    public Component getComponent() {
        return myLayout;
    }
}
