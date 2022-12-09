// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import com.intellij.java.language.impl.JavaClassFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.component.ProcessCanceledException;
import consulo.index.io.DataIndexer;
import consulo.index.io.DifferentSerializableBytesImplyNonEqualityPolicy;
import consulo.index.io.ID;
import consulo.index.io.KeyDescriptor;
import consulo.index.io.data.DataExternalizer;
import consulo.index.io.data.DataInputOutputUtil;
import consulo.internal.org.objectweb.asm.*;
import consulo.language.psi.stub.DefaultFileTypeSpecificInputFilter;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.FileContent;
import consulo.language.psi.stub.ScalarIndexExtension;
import consulo.util.lang.Pair;
import one.util.streamex.StreamEx;

import javax.annotation.Nonnull;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;

import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis.LOG;

/**
 * @author lambdamix
 */
@ExtensionImpl
public class BytecodeAnalysisIndex extends ScalarIndexExtension<HMember> {
  static final ID<HMember, Void> NAME = ID.create("bytecodeAnalysis");

  @Nonnull
  @Override
  public ID<HMember, Void> getName() {
    return NAME;
  }

  @Nonnull
  @Override
  public DataIndexer<HMember, Void, FileContent> getIndexer() {
    return inputData -> {
      try {
        return collectKeys(inputData.getContent());
      } catch (ProcessCanceledException e) {
        throw e;
      } catch (Throwable e) {
        // incorrect bytecode may result in Runtime exceptions during analysis
        // so here we suppose that exception is due to incorrect bytecode
        LOG.debug("Unexpected Error during indexing of bytecode", e);
        return Collections.emptyMap();
      }
    };
  }

  @Nonnull
  private static Map<HMember, Void> collectKeys(byte[] content) {
    HashMap<HMember, Void> map = new HashMap<>();
    MessageDigest md = BytecodeAnalysisConverter.getMessageDigest();
    ClassReader reader = new ClassReader(content);
    String className = reader.getClassName();
    reader.accept(new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        if ((access & Opcodes.ACC_PRIVATE) == 0) {
          map.put(new Member(className, name, desc).hashed(md), null);
        }
        return null;
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        map.put(new Member(className, name, desc).hashed(md), null);
        return null;
      }
    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);
    return map;
  }

  @Nonnull
  @Override
  public KeyDescriptor<HMember> getKeyDescriptor() {
    return HKeyDescriptor.INSTANCE;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }

  @Nonnull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 10;
  }

  /**
   * Externalizer for primary method keys.
   */
  private static class HKeyDescriptor implements KeyDescriptor<HMember>, DifferentSerializableBytesImplyNonEqualityPolicy {
    static final HKeyDescriptor INSTANCE = new HKeyDescriptor();

    @Override
    public void save(@Nonnull DataOutput out, HMember value) throws IOException {
      out.write(value.asBytes());
    }

    @Override
    public HMember read(@Nonnull DataInput in) throws IOException {
      byte[] bytes = new byte[HMember.HASH_SIZE];
      in.readFully(bytes);
      return new HMember(bytes);
    }
  }

  /**
   * Externalizer for compressed equations.
   */
  public static class EquationsExternalizer implements DataExternalizer<Map<HMember, Equations>> {
    @Override
    public void save(@Nonnull DataOutput out, Map<HMember, Equations> value) throws IOException {
      DataInputOutputUtil.writeSeq(out, value.entrySet(), entry -> {
        HKeyDescriptor.INSTANCE.save(out, entry.getKey());
        saveEquations(out, entry.getValue());
      });
    }

    @Override
    public Map<HMember, Equations> read(@Nonnull DataInput in) throws IOException {
      return StreamEx.of(DataInputOutputUtil.readSeq(in, () -> Pair.create(HKeyDescriptor.INSTANCE.read(in), readEquations(in)))).
          toMap(p -> p.getFirst(), p -> p.getSecond(), ClassDataIndexer.MERGER);
    }

    private static void saveEquations(@Nonnull DataOutput out, Equations eqs) throws IOException {
      out.writeBoolean(eqs.stable);
      MessageDigest md = BytecodeAnalysisConverter.getMessageDigest();
      DataInputOutputUtil.writeINT(out, eqs.results.size());
      for (DirectionResultPair pair : eqs.results) {
        DataInputOutputUtil.writeINT(out, pair.directionKey);
        Result rhs = pair.result;
        if (rhs instanceof Value) {
          Value finalResult = (Value) rhs;
          out.writeBoolean(true); // final flag
          DataInputOutputUtil.writeINT(out, finalResult.ordinal());
        } else if (rhs instanceof Pending) {
          Pending pendResult = (Pending) rhs;
          out.writeBoolean(false); // pending flag
          DataInputOutputUtil.writeINT(out, pendResult.delta.length);

          for (Component component : pendResult.delta) {
            DataInputOutputUtil.writeINT(out, component.value.ordinal());
            EKey[] ids = component.ids;
            DataInputOutputUtil.writeINT(out, ids.length);
            for (EKey hKey : ids) {
              writeKey(out, hKey, md);
            }
          }
        } else if (rhs instanceof Effects) {
          Effects effects = (Effects) rhs;
          DataInputOutputUtil.writeINT(out, effects.effects.size());
          for (EffectQuantum effect : effects.effects) {
            writeEffect(out, effect, md);
          }
          writeDataValue(out, effects.returnValue, md);
        }
      }
    }

    private static Equations readEquations(@Nonnull DataInput in) throws IOException {
      boolean stable = in.readBoolean();
      int size = DataInputOutputUtil.readINT(in);
      ArrayList<DirectionResultPair> results = new ArrayList<>(size);
      for (int k = 0; k < size; k++) {
        int directionKey = DataInputOutputUtil.readINT(in);
        Direction direction = Direction.fromInt(directionKey);
        if (direction == Direction.Pure || direction == Direction.Volatile) {
          Set<EffectQuantum> effects = new HashSet<>();
          int effectsSize = DataInputOutputUtil.readINT(in);
          for (int i = 0; i < effectsSize; i++) {
            effects.add(readEffect(in));
          }
          DataValue returnValue = readDataValue(in);
          results.add(new DirectionResultPair(directionKey, new Effects(returnValue, effects)));
        } else {
          boolean isFinal = in.readBoolean(); // flag
          if (isFinal) {
            int ordinal = DataInputOutputUtil.readINT(in);
            Value value = Value.values()[ordinal];
            results.add(new DirectionResultPair(directionKey, value));
          } else {
            int sumLength = DataInputOutputUtil.readINT(in);
            Component[] components = new Component[sumLength];

            for (int i = 0; i < sumLength; i++) {
              int ordinal = DataInputOutputUtil.readINT(in);
              Value value = Value.values()[ordinal];
              int componentSize = DataInputOutputUtil.readINT(in);
              EKey[] ids = new EKey[componentSize];
              for (int j = 0; j < componentSize; j++) {
                ids[j] = readKey(in);
              }
              components[i] = new Component(value, ids);
            }
            results.add(new DirectionResultPair(directionKey, new Pending(components)));
          }
        }
      }
      return new Equations(results, stable);
    }

    @Nonnull
    private static EKey readKey(@Nonnull DataInput in) throws IOException {
      byte[] bytes = new byte[HMember.HASH_SIZE];
      in.readFully(bytes);
      int rawDirKey = DataInputOutputUtil.readINT(in);
      return new EKey(new HMember(bytes), Direction.fromInt(Math.abs(rawDirKey)), in.readBoolean(), rawDirKey < 0);
    }

    private static void writeKey(@Nonnull DataOutput out, EKey key, MessageDigest md) throws IOException {
      out.write(key.member.hashed(md).asBytes());
      int rawDirKey = key.negated ? -key.dirKey : key.dirKey;
      DataInputOutputUtil.writeINT(out, rawDirKey);
      out.writeBoolean(key.stable);
    }

    private static void writeEffect(@Nonnull DataOutput out, EffectQuantum effect, MessageDigest md) throws IOException {
      if (effect == EffectQuantum.TopEffectQuantum) {
        DataInputOutputUtil.writeINT(out, -1);
      } else if (effect == EffectQuantum.ThisChangeQuantum) {
        DataInputOutputUtil.writeINT(out, -2);
      } else if (effect instanceof EffectQuantum.CallQuantum) {
        DataInputOutputUtil.writeINT(out, -3);
        EffectQuantum.CallQuantum callQuantum = (EffectQuantum.CallQuantum) effect;
        writeKey(out, callQuantum.key, md);
        out.writeBoolean(callQuantum.isStatic);
        DataInputOutputUtil.writeINT(out, callQuantum.data.length);
        for (DataValue dataValue : callQuantum.data) {
          writeDataValue(out, dataValue, md);
        }
      } else if (effect instanceof EffectQuantum.ReturnChangeQuantum) {
        DataInputOutputUtil.writeINT(out, -4);
        writeKey(out, ((EffectQuantum.ReturnChangeQuantum) effect).key, md);
      } else if (effect instanceof EffectQuantum.FieldReadQuantum) {
        DataInputOutputUtil.writeINT(out, -5);
        writeKey(out, ((EffectQuantum.FieldReadQuantum) effect).key, md);
      } else if (effect instanceof EffectQuantum.ParamChangeQuantum) {
        DataInputOutputUtil.writeINT(out, ((EffectQuantum.ParamChangeQuantum) effect).n);
      }
    }

    private static EffectQuantum readEffect(@Nonnull DataInput in) throws IOException {
      int effectMask = DataInputOutputUtil.readINT(in);
      switch (effectMask) {
        case -1:
          return EffectQuantum.TopEffectQuantum;
        case -2:
          return EffectQuantum.ThisChangeQuantum;
        case -3:
          EKey key = readKey(in);
          boolean isStatic = in.readBoolean();
          int dataLength = DataInputOutputUtil.readINT(in);
          DataValue[] data = new DataValue[dataLength];
          for (int di = 0; di < dataLength; di++) {
            data[di] = readDataValue(in);
          }
          return new EffectQuantum.CallQuantum(key, data, isStatic);
        case -4:
          return new EffectQuantum.ReturnChangeQuantum(readKey(in));
        case -5:
          return new EffectQuantum.FieldReadQuantum(readKey(in));
        default:
          return new EffectQuantum.ParamChangeQuantum(effectMask);
      }
    }

    private static void writeDataValue(@Nonnull DataOutput out, DataValue dataValue, MessageDigest md) throws IOException {
      if (dataValue == DataValue.ThisDataValue) {
        DataInputOutputUtil.writeINT(out, -1);
      } else if (dataValue == DataValue.LocalDataValue) {
        DataInputOutputUtil.writeINT(out, -2);
      } else if (dataValue == DataValue.OwnedDataValue) {
        DataInputOutputUtil.writeINT(out, -3);
      } else if (dataValue == DataValue.UnknownDataValue1) {
        DataInputOutputUtil.writeINT(out, -4);
      } else if (dataValue == DataValue.UnknownDataValue2) {
        DataInputOutputUtil.writeINT(out, -5);
      } else if (dataValue instanceof DataValue.ReturnDataValue) {
        DataInputOutputUtil.writeINT(out, -6);
        writeKey(out, ((DataValue.ReturnDataValue) dataValue).key, md);
      } else if (dataValue instanceof DataValue.ParameterDataValue) {
        DataInputOutputUtil.writeINT(out, ((DataValue.ParameterDataValue) dataValue).n);
      }
    }

    private static DataValue readDataValue(@Nonnull DataInput in) throws IOException {
      int dataI = DataInputOutputUtil.readINT(in);
      switch (dataI) {
        case -1:
          return DataValue.ThisDataValue;
        case -2:
          return DataValue.LocalDataValue;
        case -3:
          return DataValue.OwnedDataValue;
        case -4:
          return DataValue.UnknownDataValue1;
        case -5:
          return DataValue.UnknownDataValue2;
        case -6:
          return new DataValue.ReturnDataValue(readKey(in));
        default:
          return DataValue.ParameterDataValue.create(dataI);
      }
    }
  }
}