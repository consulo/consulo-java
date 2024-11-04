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
package com.intellij.java.impl.refactoring.introduceparameterobject.usageInfo;

import com.intellij.java.impl.refactoring.introduceparameterobject.IntroduceParameterObjectProcessor;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;

import java.util.List;
import java.util.Set;

/*
 * @author anna
 * @since 2009-11-02
 */
public class AppendAccessorsUsageInfo extends FixableUsageInfo {
    private final boolean myGenerateAccessors;
    private final Set<PsiParameter> paramsNeedingSetters;
    private final Set<PsiParameter> paramsNeedingGetters;
    private final List<IntroduceParameterObjectProcessor.ParameterChunk> parameters;
    private static final Logger LOGGER = Logger.getInstance(AppendAccessorsUsageInfo.class);

    public AppendAccessorsUsageInfo(
        PsiElement psiClass,
        boolean generateAccessors,
        Set<PsiParameter> paramsNeedingGetters,
        Set<PsiParameter> paramsNeedingSetters,
        List<IntroduceParameterObjectProcessor.ParameterChunk> parameters
    ) {
        super(psiClass);
        myGenerateAccessors = generateAccessors;
        this.paramsNeedingGetters = paramsNeedingGetters;
        this.paramsNeedingSetters = paramsNeedingSetters;
        this.parameters = parameters;
    }

    @Override
    public void fixUsage() throws IncorrectOperationException {
        if (myGenerateAccessors) {
            appendAccessors(paramsNeedingGetters, true);
            appendAccessors(paramsNeedingSetters, false);
        }
    }

    private void appendAccessors(final Set<PsiParameter> params, boolean isGetter) {
        final PsiElement element = getElement();
        if (element != null) {
            for (PsiParameter parameter : params) {
                final IntroduceParameterObjectProcessor.ParameterChunk parameterChunk =
                    IntroduceParameterObjectProcessor.ParameterChunk.getChunkByParameter(parameter, parameters);
                LOGGER.assertTrue(parameterChunk != null);
                final PsiField field = parameterChunk.getField();
                if (field != null) {
                    element.add(
                        isGetter
                            ? PropertyUtil.generateGetterPrototype(field)
                            : PropertyUtil.generateSetterPrototype(field)
                    );
                }
            }
        }
    }

    @Override
    public String getConflictMessage() {
        if (!myGenerateAccessors && (!paramsNeedingSetters.isEmpty() || !paramsNeedingGetters.isEmpty())) {
            StringBuffer buf = new StringBuffer();
            appendConflicts(buf, paramsNeedingGetters);
            appendConflicts(buf, paramsNeedingSetters);
            return RefactoringLocalize.cannotPerformRefactoringWithReason(buf).get();
        }
        return null;
    }

    private void appendConflicts(StringBuffer buf, final Set<PsiParameter> paramsNeeding) {
        if (!paramsNeeding.isEmpty()) {
            buf.append(LocalizeValue.localizeTODO(paramsNeeding == paramsNeedingGetters ? "Getters" : "Setters"));
            buf.append(LocalizeValue.localizeTODO(" for the following fields are required:\n"));
            buf.append(StringUtil.join(
                paramsNeeding,
                psiParameter -> {
                    final IntroduceParameterObjectProcessor.ParameterChunk chunk =
                        IntroduceParameterObjectProcessor.ParameterChunk.getChunkByParameter(psiParameter, parameters);
                    if (chunk != null) {
                        final PsiField field = chunk.getField();
                        if (field != null) {
                            return field.getName();
                        }
                    }
                    return psiParameter.getName();
                },
                ", "
            ));
            buf.append(".\n");
        }
    }
}