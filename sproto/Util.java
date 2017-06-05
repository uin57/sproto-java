package sproto;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

public class Util {
    public static <T extends Enum<?>> List<T> enums(int[] array, IntFunction<T> func) {
        List<T> list = new ArrayList<>(array.length);
        for (int i = 0; i < array.length; i++) {
            int ele = array[i];
            T e = func.apply(ele);
            if (e != null) {
                list.add(e);
            }
        }
        return list;
    }

    public static <T extends Enum<?>> int[] enums(List<T> list, ToIntFunction<T> func) {
        int[] array = new int[list.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = func.applyAsInt(list.get(i));
        }
        return array;
    }


}
