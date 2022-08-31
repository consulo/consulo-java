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
package com.intellij.java.language.impl.psi.impl.cache;

import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.BitUtil;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;

/**
 * Constants used in Java stubs; may differ from ones used in a .class file format.
 *
 * @author max
 */
public final class ModifierFlags
{
	public static final int PUBLIC_MASK = 0x0001;
	public static final int PRIVATE_MASK = 0x0002;
	public static final int PROTECTED_MASK = 0x0004;
	public static final int STATIC_MASK = 0x0008;
	public static final int FINAL_MASK = 0x0010;
	public static final int SYNCHRONIZED_MASK = 0x0020;
	public static final int VOLATILE_MASK = 0x0040;
	public static final int TRANSIENT_MASK = 0x0080;
	public static final int NATIVE_MASK = 0x0100;
	public static final int DEFAULT_MASK = 0x0200;
	public static final int ABSTRACT_MASK = 0x0400;
	public static final int STRICTFP_MASK = 0x0800;
	public static final int PACKAGE_LOCAL_MASK = 0x1000;
	public static final int OPEN_MASK = 0x2000;
	public static final int TRANSITIVE_MASK = 0x4000;
	public static final int SEALED_MASK = 0x8000;
	public static final int NON_SEALED_MASK = 0x10000;

	public static final ObjectIntMap<String> NAME_TO_MODIFIER_FLAG_MAP = ObjectMaps.newObjectIntHashMap();
	public static final IntObjectMap<String> MODIFIER_FLAG_TO_NAME_MAP = IntMaps.newIntObjectHashMap();
	public static final ObjectIntMap<IElementType> KEYWORD_TO_MODIFIER_FLAG_MAP = ObjectMaps.newObjectIntHashMap();

	static
	{
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.PUBLIC, PUBLIC_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.PRIVATE, PRIVATE_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.PROTECTED, PROTECTED_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.STATIC, STATIC_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.FINAL, FINAL_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.SYNCHRONIZED, SYNCHRONIZED_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.VOLATILE, VOLATILE_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.TRANSIENT, TRANSIENT_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.NATIVE, NATIVE_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.DEFAULT, DEFAULT_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.ABSTRACT, ABSTRACT_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.STRICTFP, STRICTFP_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.PACKAGE_LOCAL, PACKAGE_LOCAL_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.OPEN, OPEN_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.TRANSITIVE, TRANSITIVE_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.SEALED, SEALED_MASK);
		NAME_TO_MODIFIER_FLAG_MAP.putInt(PsiModifier.NON_SEALED, NON_SEALED_MASK);

		for(String name : NAME_TO_MODIFIER_FLAG_MAP.keySet())
		{
			MODIFIER_FLAG_TO_NAME_MAP.put(NAME_TO_MODIFIER_FLAG_MAP.getInt(name), name);
		}

		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.PUBLIC_KEYWORD, PUBLIC_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.PRIVATE_KEYWORD, PRIVATE_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.PROTECTED_KEYWORD, PROTECTED_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.STATIC_KEYWORD, STATIC_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.FINAL_KEYWORD, FINAL_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.SYNCHRONIZED_KEYWORD, SYNCHRONIZED_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.VOLATILE_KEYWORD, VOLATILE_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.TRANSIENT_KEYWORD, TRANSIENT_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.NATIVE_KEYWORD, NATIVE_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.DEFAULT_KEYWORD, DEFAULT_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.ABSTRACT_KEYWORD, ABSTRACT_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.STRICTFP_KEYWORD, STRICTFP_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.OPEN_KEYWORD, OPEN_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.TRANSITIVE_KEYWORD, TRANSITIVE_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.SEALED_KEYWORD, SEALED_MASK);
		KEYWORD_TO_MODIFIER_FLAG_MAP.putInt(JavaTokenType.NON_SEALED_KEYWORD, NON_SEALED_MASK);
	}

	public static boolean hasModifierProperty(String name, int mask)
	{
		int flag = NAME_TO_MODIFIER_FLAG_MAP.getInt(name);
		assert flag != 0 : name;
		return BitUtil.isSet(mask, flag);
	}
}
