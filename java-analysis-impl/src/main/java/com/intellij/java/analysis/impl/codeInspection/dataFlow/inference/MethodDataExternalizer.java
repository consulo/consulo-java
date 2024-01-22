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

package com.intellij.java.analysis.impl.codeInspection.dataFlow.inference;

import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.StandardMethodContract;
import consulo.util.lang.Pair;
import consulo.index.io.data.DataExternalizer;
import consulo.index.io.data.DataInputOutputUtil;

import jakarta.annotation.Nonnull;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * from kotlin
 */
class MethodDataExternalizer implements DataExternalizer<Map<Integer, MethodData>>
{
	public static final MethodDataExternalizer INSTANCE = new MethodDataExternalizer();

	@Override
	public void save(DataOutput out, Map<Integer, MethodData> value) throws IOException
	{
		DataInputOutputUtil.writeSeq(out, value.entrySet(), it ->
		{
			DataInputOutputUtil.writeINT(out, it.getKey());
			writeMethod(out, it.getValue());
		});
	}

	@Override
	public Map<Integer, MethodData> read(DataInput in) throws IOException
	{
		List<Pair<Integer, MethodData>> list = DataInputOutputUtil.readSeq(in, () -> Pair.create(DataInputOutputUtil.readINT(in), readMethod(in)));
		Map<Integer, MethodData> map = new LinkedHashMap<>();
		for(Pair<Integer, MethodData> pair : list)
		{
			map.put(pair.getFirst(), pair.getSecond());
		}
		return map;
	}

	@Nonnull
	private static MethodData readMethod(DataInput in) throws IOException
	{
		MethodReturnInferenceResult nullity = DataInputOutputUtil.readNullable(in, () -> readNullity(in));
		PurityInferenceResult purify = DataInputOutputUtil.readNullable(in, () -> readPurity(in));
		List<PreContract> contracts = DataInputOutputUtil.readSeq(in, () -> readContract(in));
		BitSet notNullParameters = readBitSet(in);
		return new MethodData(nullity, purify, contracts, notNullParameters, DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in));
	}

	private static PreContract readContract(DataInput in) throws IOException
	{
		switch(in.readByte())
		{
			case 0:
				return new DelegationContract(readRange(in), in.readBoolean());
			case 1:
				return new KnownContract(new StandardMethodContract(readContractArguments(in).stream().toArray(StandardMethodContract.ValueConstraint[]::new), readReturnValue(in)));
			case 2:
				return new MethodCallContract(readRange(in), DataInputOutputUtil.readSeq(in, () -> readContractArguments(in)));
			case 3:
				return new NegatingContract(readContract(in));
			default:
				return new SideEffectFilter(readRanges(in), DataInputOutputUtil.readSeq(in, () -> readContract(in)));
		}
	}

	private static List<StandardMethodContract.ValueConstraint> readContractArguments(DataInput in) throws IOException
	{
		return DataInputOutputUtil.readSeq(in, () -> readValueConstraint(in));
	}

	private static StandardMethodContract.ValueConstraint readValueConstraint(DataInput in) throws IOException
	{
		return StandardMethodContract.ValueConstraint.values()[in.readByte()];
	}

	private static ContractReturnValue readReturnValue(DataInput input) throws IOException
	{
		return ContractReturnValue.valueOf(input.readByte());
	}

	private static BitSet readBitSet(DataInput input) throws IOException
	{
		int size = input.readUnsignedByte();
		byte[] bytes = new byte[size];
		input.readFully(bytes);
		return BitSet.valueOf(bytes);
	}

	private static MethodReturnInferenceResult readNullity(DataInput in) throws IOException
	{
		switch(in.readByte())
		{
			case 0:
				return new MethodReturnInferenceResult.Predefined(Nullability.values()[in.readByte()]);
			default:
				return new MethodReturnInferenceResult.FromDelegate(Nullability.values()[in.readByte()], readRanges(in));
		}
	}

	private static PurityInferenceResult readPurity(DataInput in) throws IOException
	{
		List<ExpressionRange> mutableRefs = readRanges(in);
		ExpressionRange range = DataInputOutputUtil.readNullable(in, () -> readRange(in));

		return new PurityInferenceResult(mutableRefs, range);
	}

	private static void writePurity(DataOutput out, PurityInferenceResult purity) throws IOException
	{
		writeRanges(out, purity.getMutableRefs());

		DataInputOutputUtil.writeNullable(out, purity.getSingleCall(), it -> writeRange(out, it));
	}

	private static List<ExpressionRange> readRanges(DataInput in) throws IOException
	{
		return DataInputOutputUtil.readSeq(in, () -> readRange(in));
	}

	private static ExpressionRange readRange(DataInput in) throws IOException
	{
		return new ExpressionRange(DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in));
	}

	private static void writeMethod(DataOutput out, MethodData data) throws IOException
	{
		DataInputOutputUtil.writeNullable(out, data.getMethodReturn(), it -> writeNullity(out, it));
		DataInputOutputUtil.writeNullable(out, data.getPurity(), it -> writePurity(out, it));
		DataInputOutputUtil.writeSeq(out, data.getContracts(), it -> writeContract(out, it));
		writeBitSet(out, data.getNotNullParameters());
		DataInputOutputUtil.writeINT(out, data.getBodyStart());
		DataInputOutputUtil.writeINT(out, data.getBodyEnd());
	}

	private static void writeBitSet(DataOutput output, BitSet bitSet) throws IOException
	{
		byte[] bytes = bitSet.toByteArray();
		int size = bytes.length;
		assert size <= 255;
		output.writeByte(size);
		output.write(bytes);
	}

	private static void writeContract(DataOutput out, PreContract contract) throws IOException
	{
		if(contract instanceof DelegationContract)
		{
			out.writeByte(0);
			writeRange(out, ((DelegationContract) contract).getExpression());
			out.writeBoolean(((DelegationContract) contract).isNegated());
		}
		else if(contract instanceof KnownContract)
		{
			out.writeByte(1);
			writeContractArguments(out, ((KnownContract) contract).getContract().getConstraints());
			out.writeByte(((KnownContract) contract).getContract().getReturnValue().ordinal());
		}
		else if(contract instanceof MethodCallContract)
		{
			out.writeByte(2);
			writeRange(out, ((MethodCallContract) contract).getCall());
			DataInputOutputUtil.writeSeq(out, ((MethodCallContract) contract).getStates(), it -> writeContractArguments(out, it));
		}
		else if(contract instanceof NegatingContract)
		{
			out.writeByte(3);
			writeContract(out, ((NegatingContract) contract).getNegated());
		}
		else if(contract instanceof SideEffectFilter)
		{
			out.writeByte(4);
			writeRanges(out, ((SideEffectFilter) contract).getExpressionsToCheck());
			DataInputOutputUtil.writeSeq(out, ((SideEffectFilter) contract).getContracts(), it -> writeContract(out, it));
		}
		else
		{
			throw new IllegalArgumentException(contract.toString());
		}
	}

	private static void writeContractArguments(DataOutput out, List<StandardMethodContract.ValueConstraint> arguments) throws IOException
	{
		DataInputOutputUtil.writeSeq(out, arguments, it -> out.writeByte(it.ordinal()));
	}

	private static void writeNullity(DataOutput out, MethodReturnInferenceResult methodReturn) throws IOException
	{
		if(methodReturn instanceof MethodReturnInferenceResult.Predefined)
		{
			out.writeByte(0);
			out.writeByte(((MethodReturnInferenceResult.Predefined) methodReturn).getValue().ordinal());
		}
		else if(methodReturn instanceof MethodReturnInferenceResult.FromDelegate)
		{
			out.writeByte(1);
			out.writeByte(((MethodReturnInferenceResult.FromDelegate) methodReturn).getValue().ordinal());
			writeRanges(out, ((MethodReturnInferenceResult.FromDelegate) methodReturn).getDelegateCalls());
		}
		else
		{
			throw new IllegalArgumentException(methodReturn.getClass().getName());
		}
	}

	private static void writeRanges(DataOutput out, List<ExpressionRange> ranges) throws IOException
	{
		DataInputOutputUtil.writeSeq(out, ranges, expressionRange -> writeRange(out, expressionRange));
	}

	private static void writeRange(DataOutput out, ExpressionRange range) throws IOException
	{
		DataInputOutputUtil.writeINT(out, range.getStartOffset());
		DataInputOutputUtil.writeINT(out, range.getEndOffset());
	}
}
