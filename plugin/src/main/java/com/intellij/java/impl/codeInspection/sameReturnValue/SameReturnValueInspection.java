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
package com.intellij.java.impl.codeInspection.sameReturnValue;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionTool;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author max
 */
@ExtensionImpl
public class SameReturnValueInspection extends GlobalJavaInspectionTool {
    @Override
    @Nullable
    public CommonProblemDescriptor[] checkElement(
        @Nonnull RefEntity refEntity,
        @Nonnull AnalysisScope scope,
        @Nonnull InspectionManager manager,
        @Nonnull GlobalInspectionContext globalContext,
        @Nonnull ProblemDescriptionsProcessor processor,
        @Nonnull Object state
    ) {
        if (refEntity instanceof RefMethod refMethod) {
            if (refMethod.isConstructor() || refMethod.hasSuperMethods()) {
                return null;
            }

            String returnValue = refMethod.getReturnValueIfSame();
            if (returnValue != null) {
                final LocalizeValue message;
                if (refMethod.getDerivedMethods().isEmpty()) {
                    message = InspectionLocalize.inspectionSameReturnValueProblemDescriptor("<code>" + returnValue + "</code>");
                }
                else if (refMethod.hasBody()) {
                    message = InspectionLocalize.inspectionSameReturnValueProblemDescriptor1("<code>" + returnValue + "</code>");
                }
                else {
                    message = InspectionLocalize.inspectionSameReturnValueProblemDescriptor2("<code>" + returnValue + "</code>");
                }

                return new ProblemDescriptor[]{
                    manager.createProblemDescriptor(
                        refMethod.getElement().getNavigationElement(),
                        message.get(),
                        false,
                        null,
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    )
                };
            }
        }

        return null;
    }


    @Override
    protected boolean queryExternalUsagesRequests(
        final RefManager manager,
        final GlobalJavaInspectionContext globalContext,
        final ProblemDescriptionsProcessor processor,
        Object state
    ) {
        manager.iterate(new RefJavaVisitor() {
            @Override
            public void visitElement(@Nonnull RefEntity refEntity) {
                if (refEntity instanceof RefElement && processor.getDescriptions(refEntity) != null) {
                    refEntity.accept(new RefJavaVisitor() {
                        @Override
                        public void visitMethod(@Nonnull final RefMethod refMethod) {
                            globalContext.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
                                processor.ignoreElement(refMethod);
                                return false;
                            });
                        }
                    });
                }
            }
        });

        return false;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionSameReturnValueDisplayName();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesDeclarationRedundancy();
    }

    @Override
    @Nonnull
    public String getShortName() {
        return "SameReturnValue";
    }
}
