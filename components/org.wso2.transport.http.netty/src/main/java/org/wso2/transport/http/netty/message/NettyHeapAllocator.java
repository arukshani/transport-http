package org.wso2.transport.http.netty.message;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;

/**
 * Created by rukshani on 11/10/18.
 */
public class NettyHeapAllocator extends AbstractByteBufAllocator{
    @Override
    protected ByteBuf newHeapBuffer(int i, int i1) {
        return null;
    }

    @Override
    protected ByteBuf newDirectBuffer(int i, int i1) {
        return null;
    }

    @Override
    public boolean isDirectBufferPooled() {
        return false;
    }
}
