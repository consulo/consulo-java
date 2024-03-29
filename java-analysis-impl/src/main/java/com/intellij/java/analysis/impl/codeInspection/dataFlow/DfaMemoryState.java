/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfEphemeralReferenceType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaCondition;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.RelationType;
import com.intellij.java.language.psi.PsiType;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import java.util.EmptyStackException;
import java.util.Set;

public interface DfaMemoryState
{
	@Nonnull
	DfaMemoryState createCopy();

	@Nonnull
	DfaMemoryState createClosureState();

	/**
	 * Pops single value from the top of the stack and returns it
	 *
	 * @return popped value
	 * @throws EmptyStackException if stack is empty
	 */
	@Nonnull
	DfaValue pop();

	/**
	 * Reads a value from the top of the stack without popping it
	 *
	 * @return top of stack value
	 * @throws EmptyStackException if stack is empty
	 */
	@Nonnull
	DfaValue peek();

	/**
	 * Reads a value from the stack at given offset from the top without popping it
	 *
	 * @param offset value from the stack (0 = top of stack, 1 = the next one, etc.)
	 * @return stack value; null if stack does not deep enough
	 * @throws IndexOutOfBoundsException if offset is negative
	 */
	@Nullable
	DfaValue getStackValue(int offset);

	/**
	 * Pushes given value to the stack
	 *
	 * @param value to push
	 */
	void push(@Nonnull DfaValue value);

	void emptyStack();

	void setVarValue(DfaVariableValue var, DfaValue value);

	/**
	 * Returns a relation between given values within this state, if known
	 *
	 * @param left  first value
	 * @param right second value
	 * @return a relation (EQ, NE, GT, LT), or null if not known.
	 */
	@Nullable
	RelationType getRelation(DfaValue left, DfaValue right);

	boolean applyCondition(DfaCondition dfaCond);

	/**
	 * Returns true if given two values are known to be equal
	 *
	 * @param value1 first value to check
	 * @param value2 second value to check
	 * @return true if they are equal; false if not equal or not known
	 */
	boolean areEqual(@Nonnull DfaValue value1, @Nonnull DfaValue value2);

	boolean applyContractCondition(DfaCondition dfaCond);

	/**
	 * Updates value dfType if it's compatible with current value state.
	 * Depending on value passed and memory state implementation the new fact may or may not be memoized.
	 *
	 * @param value  value to update
	 * @param dfType wanted type
	 * @return true if update was successful. If false was returned the memory state may be in inconsistent state.
	 */
	boolean meetDfType(@Nonnull DfaValue value, @Nonnull DfType dfType);

	/**
	 * Forcibly sets the supplied dfType to given value if given value state can be memoized.
	 * This is necessary to override some knowledge about the variable state. In most of the cases
	 * {@link #meetDfType(DfaValue, DfType)} should be used as it narrows existing type.
	 *
	 * @param value  value to update.
	 * @param dfType type to assign to value. Note that type might be adjusted, e.g. to be compatible with value declared PsiType.
	 */
	void setDfType(@Nonnull DfaValue value, @Nonnull DfType dfType);

	/**
	 * @param value value to get the type of
	 * @return the DfType of the value within this memory state
	 */
	@Nonnull
	DfType getDfType(@Nonnull DfaValue value);

	/**
	 * @param value value to get the type of; if value is a primitive wrapper, it will be unboxed before fetching the DfType
	 * @return the DfType of the value within this memory state
	 */
	@Nonnull
	DfType getUnboxedDfType(@Nonnull DfaValue value);

	/**
	 * @param value value to get the type of
	 * @return the PsiType of given value, could be more precise than the declared type. May return null if not known.
	 */
	@Nullable
	PsiType getPsiType(@Nonnull DfaValue value);

	void flushFieldsQualifiedBy(@Nonnull Set<DfaValue> qualifiers);

	void flushFields();

	void flushVariable(@Nonnull DfaVariableValue variable);

	boolean isNull(DfaValue dfaVar);

	boolean checkNotNullable(@Nonnull DfaValue value);

	boolean isNotNull(DfaValue dfaVar);

	/**
	 * Mark this state as ephemeral. See {@link #isEphemeral()} for details.
	 */
	void markEphemeral();

	/**
	 * Ephemeral means a state that could be unreachable under normal program execution. Examples of ephemeral states include:
	 * <ul>
	 * <li>State created by method contract processing that checks for null (otherwise, if argument has unknown nullity, this would make it nullable)</li>
	 * <li>State that appears on VM exception path (e.g. catching NPE or CCE)</li>
	 * <li>State that appears when {@linkplain DfEphemeralReferenceType an ephemeral value} is stored on the stack</li>
	 * </ul>
	 * The "unsound" warnings (e.g. possible NPE) are not reported if they happen only in ephemeral states, and there's a non-ephemeral state
	 * where the same problem doesn't happen.
	 */
	boolean isEphemeral();

	boolean isEmptyStack();

	/**
	 * Returns true if two given values should be compared by content, rather than by reference.
	 *
	 * @param dfaLeft  left value
	 * @param dfaRight right value
	 * @return true if two given values should be compared by content, rather than by reference.
	 */
	boolean shouldCompareByEquals(DfaValue dfaLeft, DfaValue dfaRight);
}
