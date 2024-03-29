package com.siyeh.igtest.errorhandling.toobroadcatch;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.EOFException;
import java.io.IOException;
import java.net.URL;

public class TooBroadCatchBlock{
    public void foo(){
        try{
            if(bar()){
                throw new FileNotFoundException();
            } else{
                throw new EOFException();
            }
        } catch(FileNotFoundException e){
            e.printStackTrace();
        } catch(IOException e){
            e.printStackTrace();
        }
    }

    private boolean bar(){
        return false;
    }

    void foos() {
        try {
            new URL(null);
            throw new NullPointerException();
        } catch (IOException e) {

        } catch (RuntimeException e) {

        }
    }

  void bars(boolean a, boolean b, boolean c) {
    try {
      if (!a) {
        throw new NotActiveException(); // extends ObjectStreamException
      }
      if (b) {
        throw new StreamCorruptedException(); // extends ObjectStreamException
      }
      if (c) {
        throw new IOException();
      }
    } catch (ObjectStreamException e) {
      // Deal with ObjectStreamException (a subclass of IOException)...
    } catch (IOException e) {
      // Deal with IOException...
    }
  }

  class ObjectStreamException extends IOException {}
  class StreamCorruptedException extends ObjectStreamException {}
  class NotActiveException extends ObjectStreamException {}

  void test() {
    try {
      try (FileInputStream in = new FileInputStream("asdf")) {}
    } catch (IOException e) {}
  }
}
