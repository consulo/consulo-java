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
package com.intellij.java.language.impl.psi.scope;

import java.util.List;

import jakarta.annotation.Nonnull;
import com.intellij.java.language.psi.infos.CandidateInfo;
import jakarta.annotation.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 31.03.2003
 * Time: 13:20:20
 * To change this template use Options | File Templates.
 */
public interface PsiConflictResolver
{
	@Nullable
	CandidateInfo resolveConflict(@Nonnull List<CandidateInfo> conflicts);
}
