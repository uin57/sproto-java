package sproto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class Decoder {

    private static final Charset UTF8 = Charset.forName("utf-8");
    private int fn;
    private final ByteBuffer data;
    private List<SprotoField> fields;

    public Decoder(ByteBuffer data) {
        this.data = data;
        fields = new LinkedList<>();
        data.order(ByteOrder.LITTLE_ENDIAN);
        readHeader();
    }

    class SprotoField implements Comparable<SprotoField> {
        final int tag;
        final boolean isChunk;
        final long val;

        SprotoField(int tag, boolean isChunk, long val) {
            this.tag = tag;
            this.isChunk = isChunk;
            this.val = val;
        }

        @Override
        public int compareTo(SprotoField o) {
            return Integer.compare(tag, o.tag);
        }
    }


    public List<SprotoField> readHeader() {
        fn = data.getShort() & 0x0000ffff;
        fields = new ArrayList<>(fn);
        int tag = 0;
        for (int i = 0; i < fn; i++) {
            int v = data.getShort() & 0x0000ffff;
            if (v % 2 != 0) {
                tag += (v + 1) / 2;
                continue;
            }
            int val = 0;
            if (v != 0) {
                val = v / 2 - 1;
            }
            fields.add(new SprotoField(tag, v == 0, val));
            tag++;
        }
        fields.sort(SprotoField::compareTo);
        return fields;
    }


    public <T extends SprotoObject> List<T> readList(List<T> list, Supplier<T> factory) {
        int cSize = data.getInt();
        ByteBuffer listBuffer = slice(data, data.position(), cSize);
        data.position(data.position() + cSize);
        while (listBuffer.hasRemaining()) {
            int eleSize = listBuffer.getInt();
            Decoder dec = new Decoder(slice(listBuffer, listBuffer.position(), eleSize));
            listBuffer.position(listBuffer.position()+eleSize);
            T ins = factory.get();
            ins.decode(dec);
            list.add(ins);
        }
        return list;
    }

    public <K, T extends SprotoObject> Map<K, T> readMap(Map<K, T> map, Supplier<T> factory, Function<T, K> mapFunc) {
        int cSize = data.getInt();
        ByteBuffer listBuffer = slice(data, data.position(), cSize);
        data.position(data.position() + cSize);
        while (listBuffer.hasRemaining()) {
            int eleSize = listBuffer.getInt();
            Decoder dec = new Decoder(slice(listBuffer, listBuffer.position(), eleSize));
            T ins = factory.get();
            ins.decode(dec);
            K key = mapFunc.apply(ins);
            map.put(key, ins);
        }
        return map;
    }

    public String readString() {
        int cSize = data.getInt();
        byte[] bytes = new byte[cSize];
        data.get(bytes);
        return new String(bytes, UTF8);
    }

    public long readNumber() {
        int cSize = data.getInt();
        switch (cSize) {
            case 4:
                return data.getInt();
            case 8:
                return data.getLong();
            default:
                throw new IllegalStateException("不能解析数字");
        }
    }

    public boolean readBool(int tag) {
        for (SprotoField field : fields) {
            if (field.tag == tag) {
                return field.val != 0;
            }
        }
        return false;
    }

    public long readNumber(int tag) {
        for (SprotoField field : fields) {
            if (field.tag == tag) {
                if (field.isChunk) {
                    return readNumber();
                }
                return field.val;
            }
        }
        return 0;
    }

    public boolean[] readBooleanArray() {
        int cSize = data.getInt();
        ByteBuffer listBuffer = slice(data, data.position(), cSize);
        data.position(data.position() + cSize);
        int arrSize = listBuffer.remaining();
        boolean[] array = new boolean[arrSize];
        for (int i = 0; i < arrSize; i++) {
            array[i] = listBuffer.get() > 0;
        }
        if (listBuffer.hasRemaining()) {
            throw new IllegalStateException("解码有误");
        }
        return array;
    }
    public String[] readStringArray() {
        int cSize = data.getInt();
        ByteBuffer listBuffer = slice(data, data.position(), cSize);
        data.position(data.position() + cSize);
        List<String> list=new LinkedList<>();
        while(listBuffer.hasRemaining()){
            int strLen=listBuffer.getInt();
            byte[] bytes = new byte[strLen];
            listBuffer.get(bytes);
            list.add(new String(bytes, UTF8));
        }
        return list.toArray(new String[list.size()]);
    }

    public int[] readIntArray() {
        int cSize = data.getInt();
        ByteBuffer listBuffer = slice(data, data.position(), cSize);
        data.position(data.position() + cSize);
        int typeLen = listBuffer.get();
        if (typeLen != 4) {
            throw new IllegalStateException("不能解码");
        }
        int arrSize = listBuffer.remaining() / typeLen;
        int[] array = new int[arrSize];
        for (int i = 0; i < arrSize; i++) {
            array[i] = listBuffer.getInt();
        }
        if (listBuffer.hasRemaining()) {
            throw new IllegalStateException("解码有误");
        }
        return array;
    }

    public long[] readLongArray() {
        int cSize = data.getInt();
        ByteBuffer listBuffer = slice(data, data.position(), cSize);
        data.position(data.position() + cSize);
        int typeLen = listBuffer.get();
        if (typeLen != 8) {
            throw new IllegalStateException("不能解码");
        }
        int arrSize = listBuffer.remaining() / typeLen;
        long[] array = new long[arrSize];
        for (int i = 0; i < arrSize; i++) {
            array[i] = listBuffer.getLong();
        }
        if (listBuffer.hasRemaining()) {
            throw new IllegalStateException("解码有误");
        }
        return array;
    }

    public ByteBuffer readBinary() {
        int cSize = data.getInt();
        ByteBuffer buffer = ByteBuffer.allocate(cSize);
        data.get(buffer.array(), 0, cSize);
        return data;
    }

    public <T extends SprotoObject> T readObject(Supplier<T> factory){
        int cSize = data.getInt();
        ByteBuffer objBuffer = slice(data, data.position(), cSize);
        T obj=factory.get();
        Decoder dec = new Decoder(objBuffer);
        obj.decode(dec);
         return obj;
    }


    private static ByteBuffer slice(ByteBuffer src, int pos, int len) {
        int oldPosition = src.position();
        int oldLimit = src.limit();
        src.position(pos);
        src.limit(pos + len);
        ByteBuffer dst = src.slice();
        dst.order(ByteOrder.LITTLE_ENDIAN);
        src.position(oldPosition);
        src.limit(oldLimit);
        return dst;
    }

    public int[] tags(){
        int[] tags=new int[fields.size()];
        for (int i = 0; i < tags.length; i++) {
            tags[i]=fields.get(i).tag;
        }
        return tags;
    }

}
