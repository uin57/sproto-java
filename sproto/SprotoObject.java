package sproto;

/**
 * Created by feelgo on 2017/6/2.
 */
public interface SprotoObject {

    void decode(Decoder decoder);
    void encode(Encoder encoder);
}
