// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.java.language.psi.PsiAnonymousClass;
import consulo.application.AllIcons;
import consulo.application.Application;
import consulo.application.dumb.DumbAware;
import consulo.component.extension.ExtensionPoint;
import consulo.fileEditor.structureView.tree.ActionPresentation;
import consulo.fileEditor.structureView.tree.ActionPresentationData;
import consulo.fileEditor.structureView.tree.FileStructureNodeProvider;
import consulo.fileEditor.structureView.tree.TreeElement;
import consulo.java.analysis.codeInsight.JavaCodeInsightBundle;
import consulo.language.editor.structureView.PsiTreeElementBase;
import consulo.language.navigation.AnonymousElementProvider;
import consulo.language.psi.PsiElement;
import consulo.platform.Platform;
import consulo.ui.ex.action.KeyboardShortcut;
import consulo.ui.ex.action.Shortcut;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class JavaAnonymousClassesNodeProvider implements FileStructureNodeProvider<JavaAnonymousClassTreeElement>, DumbAware {
    public static final String ID = "SHOW_ANONYMOUS";
    public static final String JAVA_ANONYMOUS_PROPERTY_NAME = "java.anonymous.provider";

    @Nonnull
    @Override
    public Collection<JavaAnonymousClassTreeElement> provideNodes(@Nonnull TreeElement node) {
        if (node instanceof PsiMethodTreeElement || node instanceof PsiFieldTreeElement || node instanceof ClassInitializerTreeElement) {
            PsiElement el = ((PsiTreeElementBase) node).getElement();
            if (el != null) {
                Application application = el.getApplication();

                ExtensionPoint<AnonymousElementProvider> point = application.getExtensionPoint(AnonymousElementProvider.class);

                PsiElement[] anonymElements = point.computeSafeIfAny(provider -> {
                    PsiElement[] elements = provider.getAnonymousElements(el);
                    return elements.length > 0 ? elements : null;
                });

                if (anonymElements != null) {
                    List<JavaAnonymousClassTreeElement> result = new ArrayList<>(anonymElements.length);
                    for (PsiElement element : anonymElements) {
                        result.add(new JavaAnonymousClassTreeElement((PsiAnonymousClass) element));
                    }
                    return result;
                }
            }
        }
        return List.of();
    }

    @Nonnull
    @Override
    public String getCheckBoxText() {
        return JavaCodeInsightBundle.message("file.structure.toggle.show.anonymous.classes");
    }

    @Override
    public Shortcut[] getShortcut() {
        return new Shortcut[]{KeyboardShortcut.fromString(Platform.current().os().isMac() ? "meta I" : "control I")};
    }

    @Nonnull
    @Override
    public ActionPresentation getPresentation() {
        return new ActionPresentationData(getCheckBoxText(), null, AllIcons.Nodes.AnonymousClass);
    }

    @Nonnull
    @Override
    public String getName() {
        return ID;
    }

    @Nonnull
    @Override
    public String getSerializePropertyName() {
        return JAVA_ANONYMOUS_PROPERTY_NAME;
    }
}
