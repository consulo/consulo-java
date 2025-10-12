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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.generation.*;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.ide.impl.idea.ide.util.MemberChooser;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;


/**
 * @author ven
 */
public class CreateConstructorMatchingSuperFix extends BaseIntentionAction implements SyntheticIntentionAction {
    private static final Logger LOG = Logger.getInstance(CreateConstructorMatchingSuperFix.class);

    private final PsiClass myClass;

    public CreateConstructorMatchingSuperFix(PsiClass aClass) {
        myClass = aClass;
        setText(JavaQuickFixLocalize.createConstructorMatchingSuper());
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!myClass.isValid() || !myClass.getManager().isInProject(myClass)) {
            return false;
        }
        setText(JavaQuickFixLocalize.createConstructorMatchingSuper());
        return true;
    }

    @Override
    public void invoke(@Nonnull final Project project, final Editor editor, PsiFile file) {
        if (!FileModificationService.getInstance().prepareFileForWrite(myClass.getContainingFile())) {
            return;
        }
        PsiClass baseClass = myClass.getSuperClass();
        LOG.assertTrue(baseClass != null);
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, myClass, PsiSubstitutor.EMPTY);
        List<PsiMethodMember> baseConstructors = new ArrayList<PsiMethodMember>();
        PsiMethod[] baseConstrs = baseClass.getConstructors();
        for (PsiMethod baseConstr : baseConstrs) {
            if (PsiUtil.isAccessible(baseConstr, myClass, myClass)) {
                baseConstructors.add(new PsiMethodMember(baseConstr, substitutor));
            }
        }

        chooseConstructor2Delegate(project, editor, substitutor, baseConstructors, baseConstrs, myClass);
    }

    public static void chooseConstructor2Delegate(final Project project,
                                                  final Editor editor,
                                                  PsiSubstitutor substitutor,
                                                  List<PsiMethodMember> baseConstructors,
                                                  PsiMethod[] baseConstrs,
                                                  final PsiClass targetClass) {
        PsiMethodMember[] constructors = baseConstructors.toArray(new PsiMethodMember[baseConstructors.size()]);
        if (constructors.length == 0) {
            constructors = new PsiMethodMember[baseConstrs.length];
            for (int i = 0; i < baseConstrs.length; i++) {
                constructors[i] = new PsiMethodMember(baseConstrs[i], substitutor);
            }
        }

        LOG.assertTrue(constructors.length >= 1); // Otherwise we won't have been messing with all this stuff
        boolean isCopyJavadoc = true;
        if (constructors.length > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
            MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(constructors, false, true, project);
            chooser.setTitle(JavaQuickFixLocalize.superClassConstructorsChooserTitle().get());
            chooser.show();
            if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
                return;
            }
            constructors = chooser.getSelectedElements(new PsiMethodMember[0]);
            isCopyJavadoc = chooser.isCopyJavadoc();
        }

        final PsiMethodMember[] constructors1 = constructors;
        final boolean isCopyJavadoc1 = isCopyJavadoc;
        ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
                @Override
                public void run() {
                    try {
                        if (targetClass.getLBrace() == null) {
                            PsiClass psiClass = JavaPsiFacade.getInstance(targetClass.getProject()).getElementFactory().createClass("X");
                            targetClass.addRangeAfter(psiClass.getLBrace(), psiClass.getRBrace(), targetClass.getLastChild());
                        }
                        JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), project);
                        CodeStyleManager formatter = CodeStyleManager.getInstance(project);
                        PsiMethod derived = null;
                        for (PsiMethodMember candidate : constructors1) {
                            PsiMethod base = candidate.getElement();
                            derived = GenerateMembersUtil.substituteGenericMethod(base, candidate.getSubstitutor(), targetClass);

                            if (!isCopyJavadoc1) {
                                final PsiDocComment docComment = derived.getDocComment();
                                if (docComment != null) {
                                    docComment.delete();
                                }
                            }

                            final String targetClassName = targetClass.getName();
                            LOG.assertTrue(targetClassName != null, targetClass);
                            derived.setName(targetClassName);

                            ConstructorBodyGenerator generator = ConstructorBodyGenerator.forLanguage(derived.getLanguage());
                            if (generator != null) {
                                StringBuilder buffer = new StringBuilder();
                                generator.start(buffer, derived.getName(), PsiParameter.EMPTY_ARRAY);
                                generator.generateSuperCallIfNeeded(buffer, derived.getParameterList().getParameters());
                                generator.finish(buffer);
                                PsiMethod stub = factory.createMethodFromText(buffer.toString(), targetClass);
                                derived.getBody().replace(stub.getBody());
                            }
                            derived = (PsiMethod) formatter.reformat(derived);
                            derived = (PsiMethod) JavaCodeStyleManager.getInstance(project).shortenClassReferences(derived);
                            PsiGenerationInfo<PsiMethod> info = OverrideImplementUtil.createGenerationInfo(derived);
                            info.insert(targetClass, null, true);
                            derived = info.getPsiMember();
                        }
                        if (derived != null) {
                            editor.getCaretModel().moveToOffset(derived.getTextRange().getStartOffset());
                            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
                        }
                    }
                    catch (IncorrectOperationException e) {
                        LOG.error(e);
                    }

                    LanguageUndoUtil.markPsiFileForUndo(targetClass.getContainingFile());
                }
            }
        );
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
