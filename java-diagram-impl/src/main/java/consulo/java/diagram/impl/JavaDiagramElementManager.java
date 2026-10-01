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

import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeParameterList;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.dataContext.DataContext;
import consulo.diagram.AbstractDiagramElementManager;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiQualifiedNamedElement;
import consulo.ui.ex.SimpleColoredText;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class JavaDiagramElementManager extends AbstractDiagramElementManager<PsiElement> {
    @Override
    public @Nullable PsiElement findInDataContext(DataContext context) {
        return context.getData(PsiElement.KEY);
    }

    @Override
    public boolean isAcceptableAsNode(Object element) {
        if (element instanceof PsiClass) {
            return !(element instanceof PsiAnonymousClass);
        }
        return element instanceof PsiJavaPackage;
    }

    @Override
    public Object[] getNodeItems(PsiElement parent) {
        if (parent instanceof PsiClass psiClass) {
            List<PsiElement> elements = new ArrayList<>();
            elements.addAll(Arrays.asList(psiClass.getFields()));
            elements.addAll(Arrays.asList(psiClass.getMethods()));
            return elements.isEmpty() ? PsiElement.EMPTY_ARRAY : elements.toArray(PsiElement.EMPTY_ARRAY);
        }
        return PsiElement.EMPTY_ARRAY;
    }

    @Override
    public @Nullable Image getNodeElementIcon(Object element) {
        return element instanceof PsiElement psiElement ? IconDescriptorUpdaters.getIcon(psiElement, 0) : null;
    }

    @Override
    public boolean canCollapse(PsiElement element) {
        return false;
    }

    @Override
    public boolean isContainerFor(PsiElement parent, PsiElement child) {
        if (parent instanceof PsiJavaPackage psiPackage && child instanceof PsiQualifiedNamedElement namedElement) {
            String fqn = namedElement.getQualifiedName();
            return fqn != null && fqn.startsWith(psiPackage.getQualifiedName());
        }
        return false;
    }

    @Override
    public @Nullable String getElementTitle(PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            return psiClass.getName();
        }
        return element instanceof PsiJavaPackage psiPackage ? "Package " + psiPackage.getName() : null;
    }

    @Override
    public @Nullable SimpleColoredText getPresentableName(Object element) {
        if (element instanceof PsiMethod method) {
            return getMethodPresentableName(method);
        }
        if (element instanceof PsiField field) {
            return getFieldPresentableName(field);
        }
        if (element instanceof PsiClass psiClass) {
            return getClassPresentableName(psiClass);
        }
        if (element instanceof PsiJavaPackage psiPackage) {
            return new SimpleColoredText(psiPackage.getQualifiedName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        return null;
    }

    private static SimpleColoredText getClassPresentableName(PsiClass psiClass) {
        int style = SimpleTextAttributes.STYLE_BOLD;
        if (psiClass.isDeprecated()) {
            style |= SimpleTextAttributes.STYLE_STRIKEOUT;
        }
        if (!psiClass.isPhysical()) {
            style |= SimpleTextAttributes.STYLE_ITALIC;
        }

        String label = String.valueOf(psiClass.getName());
        PsiTypeParameterList classParams = psiClass.getTypeParameterList();
        if (classParams != null) {
            String params = classParams.getText();
            label += params.length() > 7 ? "<...>" : params;
        }
        return new SimpleColoredText(label, SimpleTextAttributes.of(style, null));
    }

    private static SimpleColoredText getMethodPresentableName(PsiMethod method) {
        int style = SimpleTextAttributes.STYLE_PLAIN;
        if (method.isDeprecated()) {
            style |= SimpleTextAttributes.STYLE_STRIKEOUT;
        }
        if (!method.isPhysical()) {
            style |= SimpleTextAttributes.STYLE_ITALIC;
        }
        return new SimpleColoredText(getMethodSignature(method), SimpleTextAttributes.of(style, null));
    }

    private static SimpleColoredText getFieldPresentableName(PsiField field) {
        int style = SimpleTextAttributes.STYLE_PLAIN;
        if (field.isDeprecated()) {
            style |= SimpleTextAttributes.STYLE_STRIKEOUT;
        }
        if (!field.isPhysical()) {
            style |= SimpleTextAttributes.STYLE_ITALIC;
        }
        return new SimpleColoredText(field.getName(), SimpleTextAttributes.of(style, null));
    }

    @Override
    public @Nullable SimpleColoredText getPresentableType(Object element) {
        PsiType type = null;
        if (element instanceof PsiField field) {
            type = field.getType();
        }
        else if (element instanceof PsiMethod method) {
            type = method.getReturnType();
        }
        if (type == null) {
            return null;
        }

        PsiClass psiClass = PsiUtil.resolveClassInType(type);
        int style = SimpleTextAttributes.STYLE_PLAIN;
        if (psiClass != null && psiClass.isDeprecated()) {
            style |= SimpleTextAttributes.STYLE_STRIKEOUT;
        }
        return new SimpleColoredText(type.getPresentableText(), SimpleTextAttributes.of(style, SimpleTextAttributes.GRAYED_ATTRIBUTES.foreground()));
    }

    @Override
    public @Nullable String getElementDescription(PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            return psiClass.getQualifiedName();
        }
        if (element instanceof PsiJavaPackage psiPackage) {
            return psiPackage.getQualifiedName();
        }
        return null;
    }

    @Override
    public @Nullable String getNodeTooltip(PsiElement element) {
        return getElementDescription(element);
    }

    private static String getMethodSignature(PsiMethod method) {
        StringBuilder signature = new StringBuilder(method.getName());
        signature.append("(");
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                signature.append(", ");
            }
            signature.append(parameters[i].getType().getPresentableText());
        }
        signature.append(")");
        return signature.toString();
    }
}
