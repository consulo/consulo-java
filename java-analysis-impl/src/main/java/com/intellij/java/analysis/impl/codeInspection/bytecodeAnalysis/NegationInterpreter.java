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

import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;
import consulo.internal.org.objectweb.asm.tree.InsnList;
import consulo.internal.org.objectweb.asm.tree.MethodInsnNode;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicInterpreter;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;

import java.util.List;

import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.FalseValue;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.TrueValue;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.CombinedData.ThisValue;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.CombinedData.TrackableCallValue;

final class NegationInterpreter extends BasicInterpreter
{
	private final InsnList insns;

	NegationInterpreter(InsnList insns)
	{
		super(Opcodes.API_VERSION);
		this.insns = insns;
	}

	@Override
	public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException
	{
		switch(insn.getOpcode())
		{
			case ICONST_0:
				return FalseValue;
			case ICONST_1:
				return TrueValue;
			default:
				return super.newOperation(insn);
		}
	}

	@Override
	public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException
	{
		int opCode = insn.getOpcode();
		int shift = opCode == INVOKESTATIC ? 0 : 1;
		int origin = insns.indexOf(insn);

		switch(opCode)
		{
			case INVOKESTATIC:
			case INVOKESPECIAL:
			case INVOKEVIRTUAL:
			case INVOKEINTERFACE:
				boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
				MethodInsnNode mNode = (MethodInsnNode) insn;
				Member method = new Member(mNode.owner, mNode.name, mNode.desc);
				Type retType = Type.getReturnType(mNode.desc);
				BasicValue receiver = null;
				if(shift == 1)
				{
					receiver = values.remove(0);
				}
				boolean thisCall = (opCode == INVOKEINTERFACE || opCode == INVOKEVIRTUAL) && receiver == ThisValue;
				return new TrackableCallValue(origin, retType, method, values, stable, thisCall);
			default:
				return super.naryOperation(insn, values);
		}
	}
}