package com.siyeh.igtest.serialization.non_serializable_with_serialization_methods;


import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class NonSerializableWithSerializationMethods
{
    private static final long serialVersionUID = 1;

    private void readObject(ObjectInputStream str)
    {

    }

    private void writeObject(ObjectOutputStream str)
    {
        new Object() {
            void readObject(ObjectInputStream x) {}
        };
    }
}
