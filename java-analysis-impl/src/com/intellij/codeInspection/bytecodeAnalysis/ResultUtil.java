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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;

class ResultUtil
{
	private final ELattice<Value> lattice;
	final Value top;

	ResultUtil(ELattice<Value> lattice)
	{
		this.lattice = lattice;
		top = lattice.top;
	}

	Result join(Result r1, Result r2) throws AnalyzerException
	{
		if(r1 instanceof Final && ((Final) r1).value == top)
		{
			return r1;
		}
		if(r2 instanceof Final && ((Final) r2).value == top)
		{
			return r2;
		}
		if(r1 instanceof Final && r2 instanceof Final)
		{
			return new Final(lattice.join(((Final) r1).value, ((Final) r2).value));
		}
		if(r1 instanceof Final && r2 instanceof Pending)
		{
			Final f1 = (Final) r1;
			Pending pending = (Pending) r2;
			Set<Product> sum1 = new HashSet<Product>(pending.sum);
			sum1.add(new Product(f1.value, Collections.<Key>emptySet()));
			return new Pending(sum1);
		}
		if(r1 instanceof Pending && r2 instanceof Final)
		{
			Final f2 = (Final) r2;
			Pending pending = (Pending) r1;
			Set<Product> sum1 = new HashSet<Product>(pending.sum);
			sum1.add(new Product(f2.value, Collections.<Key>emptySet()));
			return new Pending(sum1);
		}
		Pending pending1 = (Pending) r1;
		Pending pending2 = (Pending) r2;
		Set<Product> sum = new HashSet<Product>();
		sum.addAll(pending1.sum);
		sum.addAll(pending2.sum);
		checkLimit(sum);
		return new Pending(sum);
	}

	private static void checkLimit(Set<Product> sum) throws AnalyzerException
	{
		int size = 0;
		for(Product prod : sum)
		{
			size += prod.ids.size();
		}
		if(size > Analysis.EQUATION_SIZE_LIMIT)
		{
			throw new AnalyzerException(null, "Equation size is too big");
		}
	}
}
