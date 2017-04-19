package dataloader;

import java.io.Serializable;
import java.util.function.Function;

public class Foo {

    Function<Integer, String> lambda;

    void bar() {
        String tmp = "foo";
        lambda = (Function<Integer, String> & Serializable) i -> tmp;
    }
}
