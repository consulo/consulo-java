/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInspection.bytecodeAnalysis;

import javax.annotation.Nonnull;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;

class HResultUtil
{
	private static final HKey[] EMPTY_PRODUCT = new HKey[0];
	private static final ArrayFactory<HComponent> HCOMPONENT_ARRAY_FACTORY = new ArrayFactory<HComponent>()
	{
		@Nonnull
		@Override
		public HComponent[] create(int count)
		{
			return new HComponent[count];
		}
	};
	private final ELattice<Value> lattice;
	final Value top;

	HResultUtil(ELattice<Value> lattice)
	{
		this.lattice = lattice;
		top = lattice.top;
	}

	HResult join(HResult r1, HResult r2)
	{
		if(r1 instanceof HFinal && ((HFinal) r1).value == top)
		{
			return r1;
		}
		if(r2 instanceof HFinal && ((HFinal) r2).value == top)
		{
			return r2;
		}
		if(r1 instanceof HFinal && r2 instanceof HFinal)
		{
			return new HFinal(lattice.join(((HFinal) r1).value, ((HFinal) r2).value));
		}
		if(r1 instanceof HFinal && r2 instanceof HPending)
		{
			HFinal f1 = (HFinal) r1;
			HPending pending = (HPending) r2;
			HComponent[] delta = new HComponent[pending.delta.length + 1];
			delta[0] = new HComponent(f1.value, EMPTY_PRODUCT);
			System.arraycopy(pending.delta, 0, delta, 1, pending.delta.length);
			return new HPending(delta);
		}
		if(r1 instanceof HPending && r2 instanceof HFinal)
		{
			HFinal f2 = (HFinal) r2;
			HPending pending = (HPending) r1;
			HComponent[] delta = new HComponent[pending.delta.length + 1];
			delta[0] = new HComponent(f2.value, EMPTY_PRODUCT);
			System.arraycopy(pending.delta, 0, delta, 1, pending.delta.length);
			return new HPending(delta);
		}
		HPending pending1 = (HPending) r1;
		HPending pending2 = (HPending) r2;
		return new HPending(ArrayUtil.mergeArrays(pending1.delta, pending2.delta, HCOMPONENT_ARRAY_FACTORY));
	}
}
