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

import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.HashSet;
import java.util.Set;

abstract class PResults
{
	// SoP = sum of products
	static Set<Set<EKey>> join(Set<? extends Set<EKey>> sop1, Set<? extends Set<EKey>> sop2)
	{
		Set<Set<EKey>> sop = new HashSet<>();
		sop.addAll(sop1);
		sop.addAll(sop2);
		return sop;
	}

	static Set<Set<EKey>> meet(Set<? extends Set<EKey>> sop1, Set<? extends Set<EKey>> sop2)
	{
		Set<Set<EKey>> sop = new HashSet<>();
		for(Set<EKey> prod1 : sop1)
		{
			for(Set<EKey> prod2 : sop2)
			{
				Set<EKey> prod = new HashSet<>();
				prod.addAll(prod1);
				prod.addAll(prod2);
				sop.add(prod);
			}
		}
		return sop;
	}

	/**
	 * 'P' stands for 'Partial'
	 */
	interface PResult
	{
	}

	static final PResult Identity = new PResult()
	{
		@Override
		public String toString()
		{
			return "Identity";
		}
	};
	// similar to top, maximal element
	static final PResult Return = new PResult()
	{
		@Override
		public String toString()
		{
			return "Return";
		}
	};
	// minimal element
	static final PResult NPE = new PResult()
	{
		@Override
		public String toString()
		{
			return "NPE";
		}
	};

	static final class ConditionalNPE implements PResult
	{
		final Set<Set<EKey>> sop;

		ConditionalNPE(Set<Set<EKey>> sop) throws AnalyzerException
		{
			this.sop = sop;
			checkLimit(sop);
		}

		ConditionalNPE(EKey key)
		{
			sop = new HashSet<>();
			Set<EKey> prod = new HashSet<>();
			prod.add(key);
			sop.add(prod);
		}

		static void checkLimit(Set<? extends Set<EKey>> sop) throws AnalyzerException
		{
			int size = sop.stream().mapToInt(Set::size).sum();
			if(size > Analysis.EQUATION_SIZE_LIMIT)
			{
				throw new AnalyzerException(null, "HEquation size is too big");
			}
		}
	}

	static PResult combineNullable(PResult r1, PResult r2) throws AnalyzerException
	{
		if(Identity == r1)
		{
			return r2;
		}
		if(Identity == r2)
		{
			return r1;
		}
		if(Return == r1)
		{
			return r2;
		}
		if(Return == r2)
		{
			return r1;
		}
		if(NPE == r1)
		{
			return NPE;
		}
		if(NPE == r2)
		{
			return NPE;
		}
		return new ConditionalNPE(join(((ConditionalNPE) r1).sop, ((ConditionalNPE) r2).sop));
	}

	static PResult join(PResult r1, PResult r2) throws AnalyzerException
	{
		if(Identity == r1)
		{
			return r2;
		}
		if(Identity == r2)
		{
			return r1;
		}
		if(Return == r1)
		{
			return Return;
		}
		if(Return == r2)
		{
			return Return;
		}
		if(NPE == r1)
		{
			return r2;
		}
		if(NPE == r2)
		{
			return r1;
		}
		return new ConditionalNPE(join(((ConditionalNPE) r1).sop, ((ConditionalNPE) r2).sop));
	}

	static PResult meet(PResult r1, PResult r2) throws AnalyzerException
	{
		if(Identity == r1)
		{
			return r2;
		}
		if(Return == r1)
		{
			return r2;
		}
		if(Return == r2)
		{
			return r1;
		}
		if(NPE == r1)
		{
			return NPE;
		}
		if(NPE == r2)
		{
			return NPE;
		}
		if(Identity == r2)
		{
			return Identity;
		}
		return new ConditionalNPE(meet(((ConditionalNPE) r1).sop, ((ConditionalNPE) r2).sop));
	}
}