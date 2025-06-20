/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.rename;

import com.intellij.java.language.psi.targets.AliasingPsiTarget;
import com.intellij.java.language.psi.targets.AliasingPsiTargetMapper;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.rename.RenamePsiElementProcessor;
import consulo.language.pom.PomService;
import consulo.language.pom.PomTarget;
import consulo.language.pom.PomTargetPsiElement;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import java.util.Map;

@ExtensionImpl
public class RenameAliasingPomTargetProcessor extends RenamePsiElementProcessor {
    @Override
    public boolean canProcessElement(@Nonnull PsiElement element) {
        return element instanceof PomTarget || element instanceof PomTargetPsiElement;
    }

    @Override
    public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames) {
        PomTarget target;
        if (element instanceof PomTargetPsiElement targetPsiElement) {
            target = targetPsiElement.getTarget();
        }
        else if (element instanceof PomTarget pomTarget) {
            target = pomTarget;
        }
        else {
            return;
        }

        element.getApplication().getExtensionPoint(AliasingPsiTargetMapper.class).forEach(mapper -> {
            for (AliasingPsiTarget psiTarget : mapper.getTargets(target)) {
                PsiElement psiElement = PomService.convertToPsi(psiTarget);
                String name = psiTarget.getNameAlias(newName);

                String definedName = allRenames.put(psiElement, name);
                if (definedName != null) {
                    assert definedName.equals(name);
                }
                else {
                    prepareRenaming(psiElement, name, allRenames);
                }
            }
        });
    }
}
