/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * created at Jan 8, 2002
 *
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import com.intellij.util.cls.ClsUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public abstract class MemberInfo
{
	public static final MemberInfo[] EMPTY_MEMBER_INFO_ARRAY = new MemberInfo[0];
	private static final int FLAG_INFO_UNAVAILABLE = 0x8000;
	private final int myName;
	private final int myDescriptor;
	private final int myGenericSignature;
	private final int myFlags;
	private final AnnotationConstantValue[] myRuntimeVisibleAnnotations;
	private final AnnotationConstantValue[] myRuntimeInvisibleAnnotations;

	protected MemberInfo(int name, int descriptor)
	{
		this(name, descriptor, -1, FLAG_INFO_UNAVAILABLE, AnnotationConstantValue.EMPTY_ARRAY, AnnotationConstantValue.EMPTY_ARRAY);
	}

	protected MemberInfo(int name,
						 int descriptor,
						 int genericSignature,
						 int flags,
						 final AnnotationConstantValue[] runtimeVisibleAnnotations,
						 final AnnotationConstantValue[] runtimeInvisibleAnnotations)
	{
		myDescriptor = descriptor;
		myGenericSignature = genericSignature;
		myName = name;
		myFlags = flags;
		myRuntimeVisibleAnnotations = runtimeVisibleAnnotations != null ? runtimeVisibleAnnotations : AnnotationConstantValue.EMPTY_ARRAY;
		myRuntimeInvisibleAnnotations = runtimeInvisibleAnnotations != null ? runtimeInvisibleAnnotations : AnnotationConstantValue.EMPTY_ARRAY;
	}

	protected MemberInfo(DataInput in) throws IOException
	{
		myName = in.readInt();
		myDescriptor = in.readInt();
		myGenericSignature = in.readInt();
		myFlags = in.readInt();
		myRuntimeVisibleAnnotations = loadAnnotations(in);
		myRuntimeInvisibleAnnotations = loadAnnotations(in);
	}

	public void save(DataOutput out) throws IOException
	{
		out.writeInt(myName);
		out.writeInt(myDescriptor);
		out.writeInt(myGenericSignature);
		out.writeInt(myFlags);
		saveAnnotations(out, myRuntimeVisibleAnnotations);
		saveAnnotations(out, myRuntimeInvisibleAnnotations);
	}

	public int getName()
	{
		return myName;
	}

	public int getDescriptor()
	{
		return myDescriptor;
	}

	public int getGenericSignature()
	{
		return myGenericSignature;
	}

	public boolean isFlagInfoAvailable()
	{
		return myFlags != FLAG_INFO_UNAVAILABLE;
	}

	public int getFlags()
	{
		return myFlags;
	}

	public boolean isPublic()
	{
		return ClsUtil.isPublic(myFlags);
	}

	public boolean isProtected()
	{
		return ClsUtil.isProtected(myFlags);
	}

	public boolean isFinal()
	{
		return ClsUtil.isFinal(myFlags);
	}

	public boolean isPrivate()
	{
		return ClsUtil.isPrivate(myFlags);
	}

	public boolean isPackageLocal()
	{
		return ClsUtil.isPackageLocal(myFlags);
	}

	public boolean isStatic()
	{
		return ClsUtil.isStatic(myFlags);
	}

	public AnnotationConstantValue[] getRuntimeVisibleAnnotations()
	{
		return myRuntimeVisibleAnnotations;
	}

	public AnnotationConstantValue[] getRuntimeInvisibleAnnotations()
	{
		return myRuntimeInvisibleAnnotations;
	}

	protected final void saveAnnotations(DataOutput out, final AnnotationConstantValue[] annotations) throws IOException
	{
		out.writeInt(annotations.length);
		for(AnnotationConstantValue annotation : annotations)
		{
			MemberInfoExternalizer.saveConstantValue(out, annotation);
		}
	}

	protected final AnnotationConstantValue[] loadAnnotations(DataInput in) throws IOException
	{
		final int size = in.readInt();
		if(size == 0)
		{
			return AnnotationConstantValue.EMPTY_ARRAY;
		}
		final AnnotationConstantValue[] annotations = new AnnotationConstantValue[size];
		for(int idx = 0; idx < size; idx++)
		{
			annotations[idx] = (AnnotationConstantValue) MemberInfoExternalizer.loadConstantValue(in);
		}
		return annotations;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}
		MemberInfo that = (MemberInfo) o;
		return myName == that.myName &&
				myDescriptor == that.myDescriptor &&
				myGenericSignature == that.myGenericSignature &&
				myFlags == that.myFlags &&
				Arrays.equals(myRuntimeVisibleAnnotations, that.myRuntimeVisibleAnnotations) &&
				Arrays.equals(myRuntimeInvisibleAnnotations, that.myRuntimeInvisibleAnnotations);
	}

	@Override
	public int hashCode()
	{
		return hashCode(myName, myDescriptor, myGenericSignature, myFlags, myRuntimeVisibleAnnotations, myRuntimeInvisibleAnnotations);
	}

	public boolean isNameAndDescriptorEqual(Object obj)
	{
		if(!(obj instanceof MemberInfo))
			return false;
		MemberInfo info = (MemberInfo) obj;
		return (myName == info.myName) && (myDescriptor == info.myDescriptor);
	}

	public static int hashCode(Object... values)
	{
		int hash = 0;
		for(Object value : values)
		{
			if(value == null)
			{
			}
			else
			{
				Class<?> aClass = value.getClass();
				if(aClass.isArray())
				{
					hash += Arrays.deepHashCode(new Object[]{value});
				}
				else
				{
					hash += Objects.hashCode(value);
				}
			}
		}
		return hash;
	}
}

