/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.memberPullUp;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.project.Project;

import java.util.Set;

/**
 * Created by Max Medvedev on 10/3/13
 */
public interface PullUpData {
    PsiClass getSourceClass();

    PsiClass getTargetClass();

    DocCommentPolicy getDocCommentPolicy();

    Set<PsiMember> getMembersToMove();

    Set<PsiMember> getMovedMembers();

    Project getProject();
}
