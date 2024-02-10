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

package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

final class Effects implements Result
{
	static final Set<EffectQuantum> TOP_EFFECTS = Collections.singleton(EffectQuantum.TopEffectQuantum);
	static final Effects VOLATILE_EFFECTS = new Effects(DataValue.UnknownDataValue2, Collections.singleton(EffectQuantum.TopEffectQuantum));

	@Nonnull
	final DataValue returnValue;
	@Nonnull
	final Set<EffectQuantum> effects;

	Effects(@Nonnull DataValue returnValue, @Nonnull Set<EffectQuantum> effects)
	{
		this.returnValue = returnValue;
		this.effects = effects;
	}

	Effects combine(Effects other)
	{
		if(this.equals(other))
			return this;
		Set<EffectQuantum> newEffects = new HashSet<>(this.effects);
		newEffects.addAll(other.effects);
		if(newEffects.contains(EffectQuantum.TopEffectQuantum))
		{
			newEffects = TOP_EFFECTS;
		}
		DataValue newReturnValue = this.returnValue.equals(other.returnValue) ? this.returnValue : DataValue.UnknownDataValue1;
		return new Effects(newReturnValue, newEffects);
	}

	@Override
	public Stream<EKey> dependencies()
	{
		return Stream.concat(returnValue.dependencies(), effects.stream().flatMap(EffectQuantum::dependencies));
	}

	public boolean isTop()
	{
		return returnValue == DataValue.UnknownDataValue1 && effects.equals(TOP_EFFECTS);
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;
		Effects that = (Effects) o;
		return this.returnValue.equals(that.returnValue) && this.effects.equals(that.effects);
	}

	@Override
	public int hashCode()
	{
		return effects.hashCode() * 31 + returnValue.hashCode();
	}

	@Override
	public String toString()
	{
		Object effectsPresentation = effects.isEmpty() ? "Pure" : effects.size() == 1 ? effects.iterator().next() : effects.size();
		return "Effects[" + effectsPresentation + "|" + returnValue + "]";
	}
}