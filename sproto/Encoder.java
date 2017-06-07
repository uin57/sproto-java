package sproto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;

public class Encoder {
    private static final Charset UTF8 = Charset.forName("utf-8");
    private static final long NumberInHeader=0x7fffff-1;
    ByteBuffer header;
    ByteBuffer chunk;
    private int lastTag=-1;

    public Encoder() {
        this.header = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
        this.chunk = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
        this.header.position(2);
    }

    public int size(){
        return header.position()+chunk.position();
    }

    public void writeTo(ByteBuffer dst){
        int hPos=header.position();
        int hLimit=header.position();
        header.putShort(0,(short)(hPos/2-1));
        header.flip();
        dst.put(header);
        header.position(hPos).limit(hLimit);
        int cPos=chunk.position();
        int cLimit=chunk.position();
        chunk.flip();
        dst.put(chunk);
        chunk.position(cPos).limit(cLimit);
    }

    private void growBuffer(int headerLen, int chunkLen){
        if (header.remaining()<headerLen){
            headerLen-=header.remaining();
            int newSzie=header.capacity()+headerLen;
            ByteBuffer a=header.isDirect()?ByteBuffer.allocateDirect(newSzie):ByteBuffer.allocate(newSzie);
            header.clear();
            int r=header.remaining();
            for (int i = 0; i < r; i++) {
                a.put(header.get());
            }
            header=a;
        }
        if (chunk.remaining()<chunkLen){
            chunkLen-=chunk.remaining();
            int newSzie=chunk.capacity()+chunkLen;
            ByteBuffer a=chunk.isDirect()?ByteBuffer.allocateDirect(newSzie):ByteBuffer.allocate(newSzie);
            chunk.clear();
            int r=chunk.remaining();
            for (int i = 0; i < r; i++) {
                a.put(chunk.get());
            }
            chunk=a;
        }
    }

    private void checkAndFillTag(int nextTag){
        int span = nextTag - lastTag - 1;
        if (span>0){
            span=span*2-1;
            growBuffer(2,0);
            header.putShort((short)span);

        }else if(span<0){
            throw new IllegalStateException();
        }
        this.lastTag=nextTag;


    }

    public void writeNumber(int tag,int val){
        checkAndFillTag(tag);
        if (val>NumberInHeader){
            growBuffer(2,8);
            header.putShort((short)0);
            chunk.putInt(4).putInt(val);
        }else{
            val=(val+1)*2;
            growBuffer(2,0);
            header.putShort((short)(val&0xffff));
        }
    }

    public void writeNumber(int tag,long val){
        checkAndFillTag(tag);
        if (val>NumberInHeader){
            growBuffer(2,12);
            header.putShort((short)0);
            chunk.putInt(8).putLong(val);
        }else{
            val=(val+1)*2;
            growBuffer(2,0);
            header.putShort((short)(val&0xffff));
        }
    }
    public void writeString(int tag,String str){
        checkAndFillTag(tag);
        byte[] bytes=str.getBytes(UTF8);
        growBuffer(2,bytes.length+4);
        header.putShort((short)0);
        chunk.putInt(bytes.length).put(bytes);
    }

    public void writeNumberArray(int tag,int[] array){
        checkAndFillTag(tag);
        growBuffer(2,array.length*4+1);
        header.putShort((short)0);
        chunk.put((byte)4);
        for (int i = 0; i < array.length; i++) {
            chunk.putInt(array[i]);
        }
    }

    public void writeNumberArray(int tag,long[] array){
        checkAndFillTag(tag);
        growBuffer(2,array.length*8+1);
        header.putShort((short)0);
        chunk.put((byte)8);
        for (int i = 0; i < array.length; i++) {
            chunk.putLong(array[i]);
        }
    }
    public void writeBooleanArray(int tag,boolean[] array){
        checkAndFillTag(tag);
        growBuffer(2,array.length*2);
        header.putShort((short)0);
        for (int i = 0; i < array.length; i++) {
            chunk.put((byte)(array[i]?1:0));
        }
    }

    public void writeStringArray(int tag,String[] array){
        checkAndFillTag(tag);
        growBuffer(2,4);
        int position=chunk.position();
        int total=0;
        chunk.putInt(0);
        for (int i = 0; i < array.length; i++) {
            String str = array[i];
            byte[] bytes=str.getBytes(UTF8);
            total+=bytes.length;
            growBuffer(0,bytes.length+4);
            chunk.putInt(bytes.length).put(bytes);
        }
        chunk.putInt(position,total);
    }

    public void writeBinary(int tag,ByteBuffer buf){
        checkAndFillTag(tag);
        growBuffer(2,buf.remaining()+4);
        header.putShort((short)0);
        chunk.putInt(buf.remaining()).put(buf);
    }
    public void writeBinaryArray(int tag,ByteBuffer[] array){
        checkAndFillTag(tag);
        growBuffer(2,0);
        int position=chunk.position();
        int total=0;
        chunk.putInt(0);
        for (int i = 0; i < array.length; i++) {
            ByteBuffer buf = array[i];
            growBuffer(0,buf.remaining()+4);
            chunk.putInt(buf.remaining()).put(buf);
        }
        chunk.putInt(position,total);
    }
    public void writeObject(int tag,SprotoObject obj){
        checkAndFillTag(tag);
        Encoder enc=new Encoder();
        obj.encode(enc);
        growBuffer(2,enc.size()+4);
        header.putShort((short)0);
        chunk.putInt(enc.size());
        enc.writeTo(chunk);
    }

    public void writeObjectList(int tag,Collection<? extends SprotoObject> list){
        checkAndFillTag(tag);
        growBuffer(2,4);
        header.putShort((short)0);
        int position=chunk.position();
        int total=0;
        chunk.putInt(0);
        for (SprotoObject sprotoObject : list) {
            Encoder enc=new Encoder();
            sprotoObject.encode(enc);
            total+=enc.size();
            growBuffer(0,enc.size()+4);
            chunk.putInt(enc.size());
            enc.writeTo(chunk);
        }
        chunk.putInt(position,total);
    }

}
