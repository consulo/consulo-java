import javax.annotation.Nonnull;

public class Infer {
    enum E {;
    }

    void trySwitchEnum(@Nonnull E e) {
        switch (e) {

        }
    }

}