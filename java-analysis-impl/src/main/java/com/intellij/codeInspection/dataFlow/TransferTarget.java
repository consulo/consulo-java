/*
 * Copyright 2013-2017 consulo.io
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

package com.intellij.codeInspection.dataFlow;

import org.checkerframework.checker.index.qual.NonNegative;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

/**
 * from kotlin
 */
public interface TransferTarget
{
	/**
	 * @return list of possible instruction offsets for given target
	 */
	@Nonnull
	default Collection<Integer> getPossibleTargets()
	{
		return List.of();
	}

	/**
	 * @return next instruction states assuming no traps
	 */
	@NonNegative
	default List<DfaInstructionState> dispatch(DfaMemoryState state, DataFlowRunner runner)
	{
		return List.of();
	}
}
