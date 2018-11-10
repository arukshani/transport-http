package org.wso2.transport.http.netty.message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.MaxMessagesRecvByteBufAllocator;
import io.netty.util.UncheckedBooleanSupplier;

/**
 * Created by rukshani on 11/10/18.
 */
public class NettyRecvHeapAllocator implements MaxMessagesRecvByteBufAllocator {
    private volatile int maxMessagesPerRead;
    private volatile boolean respectMaybeMoreData = true;
    @Override
    public int maxMessagesPerRead() {
        return maxMessagesPerRead;
    }

    @Override
    public MaxMessagesRecvByteBufAllocator maxMessagesPerRead(int i) {
        return this;
    }

    public NettyRecvHeapAllocator respectMaybeMoreData(boolean respectMaybeMoreData) {
        this.respectMaybeMoreData = respectMaybeMoreData;
        return this;
    }

    private final class HandleImpl extends MaxMessageHandle {

        @Override
        public int guess() {
            return 65536;
        }
    }

    @Override
    public Handle newHandle() {
        return new HandleImpl();
    }

    public abstract class MaxMessageHandle implements ExtendedHandle {
        private ChannelConfig config;
        private int maxMessagePerRead;
        private int totalMessages;
        private int totalBytesRead;
        private int attemptedBytesRead;
        private int lastBytesRead;
        private final boolean respectMaybeMoreData;
        private final UncheckedBooleanSupplier defaultMaybeMoreSupplier;

        public MaxMessageHandle() {
            this.respectMaybeMoreData = NettyRecvHeapAllocator.this.respectMaybeMoreData;
            this.defaultMaybeMoreSupplier = new UncheckedBooleanSupplier() {
                public boolean get() {
                    return NettyRecvHeapAllocator.MaxMessageHandle.this.attemptedBytesRead == NettyRecvHeapAllocator.MaxMessageHandle.this.lastBytesRead;
                }
            };
        }

        public void reset(ChannelConfig config) {
            this.config = config;
            this.maxMessagePerRead = NettyRecvHeapAllocator.this.maxMessagesPerRead();
            this.totalMessages = this.totalBytesRead = 0;
        }

        @Override
        public ByteBuf allocate(ByteBufAllocator alloc) {
            return alloc.heapBuffer(this.guess());
        }

        @Override
        public final void incMessagesRead(int amt) {
            this.totalMessages += amt;
        }

        @Override
        public void lastBytesRead(int bytes) {
            this.lastBytesRead = bytes;
            if(bytes > 0) {
                this.totalBytesRead += bytes;
            }

        }

        public final int lastBytesRead() {
            return this.lastBytesRead;
        }

        public boolean continueReading() {
            return this.continueReading(this.defaultMaybeMoreSupplier);
        }

        public boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier) {
            return this.config.isAutoRead() && (!this.respectMaybeMoreData || maybeMoreDataSupplier.get()) && this.totalMessages < this.maxMessagePerRead && this.totalBytesRead > 0;
        }

        public void readComplete() {
        }

        public int attemptedBytesRead() {
            return this.attemptedBytesRead;
        }

        public void attemptedBytesRead(int bytes) {
            this.attemptedBytesRead = bytes;
        }

        protected final int totalBytesRead() {
            return this.totalBytesRead < 0?2147483647:this.totalBytesRead;
        }
    }


//    @Override
//    public Handle newHandle() {
//        return new HandleImpl();
//    }
//
//    private final class HandleImpl extends MaxMessageHandle {
//
//        @Override
//        public ByteBuf allocate(ByteBufAllocator alloc) {
//            return alloc.heapBuffer(guess());
//        }
//
//        @Override
//        public int guess() {
//            return 65536;
//        }
//    }


}
