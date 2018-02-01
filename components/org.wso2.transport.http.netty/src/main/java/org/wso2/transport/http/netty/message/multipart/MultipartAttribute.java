/*
*  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/

package org.wso2.transport.http.netty.message.multipart;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.AbstractReferenceCounted;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 */
public class MultipartAttribute extends AbstractReferenceCounted implements InterfaceHttpData {
    private final List<ByteBuf> value = new ArrayList();
    private final Charset charset;
    private int size;

    MultipartAttribute(Charset charset) {
        this.charset = charset;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public HttpDataType getHttpDataType() {
        return null;
    }

    @Override
    public InterfaceHttpData touch(Object o) {
        return null;
    }

    @Override
    protected void deallocate() {

    }

    @Override
    public int compareTo(InterfaceHttpData o) {
        return 0;
    }

    public InterfaceHttpData retain() {
        Iterator var1 = this.value.iterator();

        while (var1.hasNext()) {
            ByteBuf buf = (ByteBuf) var1.next();
            buf.retain();
        }

        return this;
    }

    public InterfaceHttpData retain(int increment) {
        Iterator var2 = this.value.iterator();

        while (var2.hasNext()) {
            ByteBuf buf = (ByteBuf) var2.next();
            buf.retain(increment);
        }

        return this;
    }

    public InterfaceHttpData touch() {
        Iterator var1 = this.value.iterator();

        while (var1.hasNext()) {
            ByteBuf buf = (ByteBuf) var1.next();
            buf.touch();
        }

        return this;
    }

    public void addValue(String value, int rank) {
        if(value == null) {
            throw new NullPointerException("value");
        } else {
            ByteBuf buf = Unpooled.copiedBuffer(value, this.charset);
            this.value.add(rank, buf);
            this.size += buf.readableBytes();
        }
    }

    public void addValue(String value) {
        if(value == null) {
            throw new NullPointerException("value");
        } else {
            ByteBuf buf = Unpooled.copiedBuffer(value, this.charset);
            this.value.add(buf);
            this.size += buf.readableBytes();
        }
    }

    public void setValue(String value, int rank) {
        if(value == null) {
            throw new NullPointerException("value");
        } else {
            ByteBuf buf = Unpooled.copiedBuffer(value, this.charset);
            ByteBuf old = (ByteBuf)this.value.set(rank, buf);
            if(old != null) {
                this.size -= old.readableBytes();
                old.release();
            }

            this.size += buf.readableBytes();
        }
    }

    public int size() {
        return this.size;
    }

    public ByteBuf toByteBuf() {
        return Unpooled.compositeBuffer().addComponents(this.value).writerIndex(this.size()).readerIndex(0);
    }

}
