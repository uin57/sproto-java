package sproto;

import java.util.Arrays;
import java.util.function.IntConsumer;

import static java.util.Arrays.copyOf;

/**
 * Created by feelgo on 2017/6/2.
 */
public class Tags {
    int[] tags;
    int count;

    public Tags() {
    }

    private void init(){
        if (tags == null) {
            tags = new int[10];
            Arrays.fill(tags, Integer.MAX_VALUE);
        }
    }
    private void growCap() {
        if (count + 1 == tags.length) {
            int len = tags.length * 2;
            tags = copyOf(tags, len);
        }
    }

    public void setTag(int tag) {
        init();
        int index = Arrays.binarySearch(tags, tag);
        if (index == -1) {
            growCap();
            tags[count++] = tag;
        }
    }

    public boolean hasTag(int tag) {
        if (tags == null) {
            return false;
        }
        return Arrays.binarySearch(tags, tag) != -1;
    }

    public void each(IntConsumer con) {
        if (tags == null) {
            return;
        }
        Arrays.sort(tags);
        for (int i = 0; i < count; i++) {
            con.accept(tags[i]);
        }
    }

}
