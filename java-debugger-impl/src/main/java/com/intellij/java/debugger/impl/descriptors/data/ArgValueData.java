/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.descriptors.data;

import javax.annotation.Nonnull;
import com.intellij.java.debugger.impl.jdi.DecompiledLocalVariable;
import com.intellij.java.debugger.impl.ui.impl.watch.ArgumentValueDescriptorImpl;
import consulo.project.Project;
import consulo.internal.com.sun.jdi.Value;

public class ArgValueData extends DescriptorData<ArgumentValueDescriptorImpl>
{
	private final DecompiledLocalVariable myVariable;
	private final Value myValue;

	public ArgValueData(DecompiledLocalVariable variable, Value value)
	{
		myVariable = variable;
		myValue = value;
	}

	@Override
	protected ArgumentValueDescriptorImpl createDescriptorImpl(@Nonnull Project project)
	{
		return new ArgumentValueDescriptorImpl(project, myVariable, myValue);
	}

	@Override
	public boolean equals(Object object)
	{
		if(!(object instanceof ArgValueData))
		{
			return false;
		}

		return myVariable.getSlot() == ((ArgValueData) object).myVariable.getSlot();
	}

	@Override
	public int hashCode()
	{
		return myVariable.getSlot();
	}

	@Override
	public DisplayKey<ArgumentValueDescriptorImpl> getDisplayKey()
	{
		return new SimpleDisplayKey<ArgumentValueDescriptorImpl>(myVariable.getSlot());
	}
}