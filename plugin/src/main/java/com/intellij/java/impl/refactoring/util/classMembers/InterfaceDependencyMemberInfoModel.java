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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 15:18:10
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.refactoring.util.classMembers;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import consulo.language.editor.refactoring.classMember.DependencyMemberInfoModel;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import consulo.language.editor.refactoring.classMember.MemberInfoTooltipManager;

public class InterfaceDependencyMemberInfoModel<T extends PsiMember, M extends MemberInfoBase<T>> extends
		DependencyMemberInfoModel<T, M>
{

	public InterfaceDependencyMemberInfoModel(PsiClass aClass)
	{
		super(new InterfaceMemberDependencyGraph<T, M>(aClass), WARNING);
		setTooltipProvider(new MemberInfoTooltipManager.TooltipProvider<T, M>()
		{
			@Override
			public String getTooltip(M memberInfo)
			{
				return ((InterfaceMemberDependencyGraph) myMemberDependencyGraph).getElementTooltip(memberInfo
						.getMember());
			}
		});
	}

	@Override
	public boolean isCheckedWhenDisabled(M member)
	{
		return false;
	}

	@Override
	public Boolean isFixedAbstract(M member)
	{
		return null;
	}
}
