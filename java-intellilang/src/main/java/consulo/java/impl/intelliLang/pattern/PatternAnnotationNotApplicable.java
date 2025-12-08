/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.impl.intelliLang.pattern;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.impl.intelliLang.util.PsiUtilEx;
import consulo.java.impl.intelliLang.util.RemoveAnnotationFix;
import consulo.language.Language;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.inject.advanced.Configuration;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class PatternAnnotationNotApplicable extends LocalInspectionTool {
    @Nullable
    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    @Nonnull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return PatternValidator.PATTERN_VALIDATION;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Pattern Annotation not applicable");
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            final String annotationName =
                Configuration.getProjectInstance(holder.getProject()).getAdvancedConfiguration().getPatternAnnotationClass();

            @Override
            public void visitAnnotation(@Nonnull PsiAnnotation annotation) {
                String name = annotation.getQualifiedName();
                if (annotationName.equals(name)) {
                    checkAnnotation(annotation, holder);
                }
                else if (name != null) {
                    PsiClass psiClass = JavaPsiFacade.getInstance(annotation.getProject()).findClass(name, annotation.getResolveScope());
                    if (psiClass != null && AnnotationUtil.isAnnotated(psiClass, annotationName, false, false)) {
                        checkAnnotation(annotation, holder);
                    }
                }
            }
        };
    }

    private void checkAnnotation(PsiAnnotation annotation, ProblemsHolder holder) {
        PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class);
        if (owner instanceof PsiVariable variable) {
            PsiType type = variable.getType();
            if (!PsiUtilEx.isString(type)) {
                registerProblem(annotation, holder);
            }
        }
        else if (owner instanceof PsiMethod method) {
            PsiType type = method.getReturnType();
            if (type != null && !PsiUtilEx.isString(type)) {
                registerProblem(annotation, holder);
            }
        }
    }

    private void registerProblem(PsiAnnotation annotation, ProblemsHolder holder) {
        holder.newProblem(LocalizeValue.localizeTODO("Pattern Annotation is only applicable to elements of type String"))
            .range(annotation)
            .withFix(new RemoveAnnotationFix(this))
            .create();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "PatternNotApplicable";
    }
}
