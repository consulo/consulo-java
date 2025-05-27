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
package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.usage.UsageInfo;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl(id = "java")
public class MoveJavaClassHandler implements MoveClassHandler {
    private static final Logger LOG = Logger.getInstance(MoveJavaClassHandler.class);

    @Override
    public void finishMoveClass(@Nonnull PsiClass aClass) {
        if (aClass.getContainingFile() instanceof PsiJavaFile) {
            ChangeContextUtil.decodeContextInfo(aClass, null, null);
        }
    }

    @Override
    public void prepareMove(@Nonnull PsiClass aClass) {
        if (aClass.getContainingFile() instanceof PsiJavaFile) {
            ChangeContextUtil.encodeContextInfo(aClass, true);
        }
    }

    @Override
    @RequiredWriteAction
    public PsiClass doMoveClass(@Nonnull PsiClass aClass, @Nonnull PsiDirectory moveDestination) throws IncorrectOperationException {
        PsiFile file = aClass.getContainingFile();
        PsiJavaPackage newPackage = JavaDirectoryService.getInstance().getPackage(moveDestination);

        PsiClass newClass = null;
        if (file instanceof PsiJavaFile javaFile) {
            if (!moveDestination.equals(javaFile.getContainingDirectory()) && moveDestination.findFile(file.getName()) != null) {
                // moving second of two classes which were in the same file to a different directory (IDEADEV-3089)
                correctSelfReferences(aClass, newPackage);
                PsiFile newFile = moveDestination.findFile(file.getName());
                LOG.assertTrue(newFile != null);
                newClass = (PsiClass)newFile.add(aClass);
                correctOldClassReferences(newClass, aClass);
                aClass.delete();
            }
            else if (javaFile.getClasses().length > 1) {
                correctSelfReferences(aClass, newPackage);
                PsiClass created = JavaDirectoryService.getInstance().createClass(moveDestination, aClass.getName());
                if (aClass.getDocComment() == null) {
                    PsiDocComment createdDocComment = created.getDocComment();
                    if (createdDocComment != null) {
                        aClass.addAfter(createdDocComment, null);
                    }
                }
                newClass = (PsiClass)created.replace(aClass);
                correctOldClassReferences(newClass, aClass);
                aClass.delete();
            }
        }
        return newClass;
    }

    private static void correctOldClassReferences(final PsiClass newClass, final PsiClass oldClass) {
        final Set<PsiImportStatementBase> importsToDelete = new HashSet<>();
        newClass.getContainingFile().accept(new JavaRecursiveElementVisitor() {
            @Override
            @RequiredWriteAction
            public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference) {
                if (reference.isValid() && reference.isReferenceTo(oldClass)) {
                    PsiImportStatementBase importStatement = PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class);
                    if (importStatement != null) {
                        importsToDelete.add(importStatement);
                        return;
                    }
                    try {
                        reference.bindToElement(newClass);
                    }
                    catch (IncorrectOperationException e) {
                        LOG.error(e);
                    }
                }
                super.visitReferenceElement(reference);
            }
        });
        for (PsiImportStatementBase importStatement : importsToDelete) {
            importStatement.delete();
        }
    }

    private static void correctSelfReferences(final PsiClass aClass, final PsiJavaPackage newContainingPackage) {
        final PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(aClass.getContainingFile().getContainingDirectory());
        if (aPackage != null) {
            aClass.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                @RequiredWriteAction
                public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference) {
                    if (reference.isQualified() && reference.isReferenceTo(aClass)) {
                        if (reference.getQualifier() instanceof PsiJavaCodeReferenceElement codeRefElem
                            && codeRefElem.isReferenceTo(aPackage)) {
                            try {
                                codeRefElem.bindToElement(newContainingPackage);
                            }
                            catch (IncorrectOperationException e) {
                                LOG.error(e);
                            }
                        }
                    }
                    super.visitReferenceElement(reference);
                }
            });
        }
    }

    @Override
    @RequiredReadAction
    public String getName(PsiClass clazz) {
        if (!(clazz.getContainingFile() instanceof PsiJavaFile javaFile)) {
            return null;
        }
        return javaFile.getClasses().length > 1
            ? clazz.getName() + "." + JavaFileType.INSTANCE.getDefaultExtension()
            : javaFile.getName();
    }

    @Override
    public void preprocessUsages(Collection<UsageInfo> results) {
    }
}
