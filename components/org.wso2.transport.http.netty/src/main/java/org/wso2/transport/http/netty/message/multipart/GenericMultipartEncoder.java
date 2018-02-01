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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.stream.ChunkedInput;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

/**
 * Encode a given body part for multipart/subtype.
 */
public class GenericMultipartEncoder implements ChunkedInput<HttpContent> {

    private final HttpDataFactory factory;
    private final HttpRequest request;
    private final Charset charset;
    private final String mediaType;
    private boolean isChunked;
    private final List<InterfaceHttpData> bodyListDatas;
    final List<InterfaceHttpData> multipartHttpDatas;
    private final boolean isMultipart;
    String multipartDataBoundary;
    String multipartMixedBoundary;
    private boolean headerFinalized;
    private final HttpPostRequestEncoder.EncoderMode encoderMode;
    private boolean isLastChunk;
    private boolean isLastChunkSent;
    private FileUpload currentFileUpload;
    private boolean duringMixedMode;
    private long globalBodySize;
    private long globalProgress;
    private ListIterator<InterfaceHttpData> iterator;
    private ByteBuf currentBuffer;
    private InterfaceHttpData currentData;
    private boolean isKey;

    public GenericMultipartEncoder(HttpRequest request, boolean multipart) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        this(new DefaultHttpDataFactory(16384L), request, multipart, HttpConstants.DEFAULT_CHARSET, HttpPostRequestEncoder.EncoderMode.RFC1738);
    }

    public GenericMultipartEncoder(HttpDataFactory factory, HttpRequest request, boolean multipart) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        this(factory, request, multipart, HttpConstants.DEFAULT_CHARSET, HttpPostRequestEncoder.EncoderMode.RFC1738);
    }

    public GenericMultipartEncoder(HttpDataFactory factory, HttpRequest request, boolean multipart, Charset charset, HttpPostRequestEncoder.EncoderMode encoderMode) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        this.isKey = true;
        this.request = (HttpRequest) ObjectUtil.checkNotNull(request, "request");
        this.charset = (Charset) ObjectUtil.checkNotNull(charset, "charset");
        this.factory = (HttpDataFactory) ObjectUtil.checkNotNull(factory, "factory");
        if (HttpMethod.TRACE.equals(request.method())) {
            throw new HttpPostRequestEncoder.ErrorDataEncoderException("Cannot create a Encoder if request is a TRACE");
        } else {
            this.bodyListDatas = new ArrayList();
            this.isLastChunk = false;
            this.isLastChunkSent = false;
            this.isMultipart = multipart;
            this.multipartHttpDatas = new ArrayList();
            this.encoderMode = encoderMode;
            if (this.isMultipart) {
                this.initDataMultipart();
            }
        }
        if (request.headers().get("Content-Type") != null) {
            this.mediaType = request.headers().get("Content-Type");
        } else{
            this.mediaType = HttpHeaderValues.MULTIPART_MIXED.toString();
        }
    }

    private void initDataMultipart() {
        this.multipartDataBoundary = getNewMultipartDelimiter();
    }

    private void initMixedMultipart() {
        this.multipartMixedBoundary = getNewMultipartDelimiter();
    }

    private static String getNewMultipartDelimiter() {
        return Long.toHexString(PlatformDependent.threadLocalRandom().nextLong());
    }

    public void addBodyAttribute(String name, String value) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        String svalue = value != null?value:"";
        Attribute data = this.factory.createAttribute(this.request, (String)ObjectUtil.checkNotNull(name, "name"), svalue);
        this.addBodyHttpData(data);
    }

    public void addBodyFileUpload(String name, File file, String contentType, boolean isText) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        this.addBodyFileUpload(name, file.getName(), file, contentType, isText);
    }

    public void addBodyFileUpload(String name, String filename, File file, String contentType, boolean isText) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        ObjectUtil.checkNotNull(name, "name");
        ObjectUtil.checkNotNull(file, "file");
        if(filename == null) {
            filename = "";
        }

        String scontentType = contentType;
        String contentTransferEncoding = null;
        if(contentType == null) {
            if(isText) {
                scontentType = "text/plain";
            } else {
                scontentType = "application/octet-stream";
            }
        }

        if(!isText) {
            contentTransferEncoding = PostBodyUtil.TransferEncodingMechanism.BINARY.value();
        }

        FileUpload fileUpload = this.factory.createFileUpload(this.request, name, filename, scontentType, contentTransferEncoding, (Charset)null, file.length());

        try {
            fileUpload.setContent(file);
        } catch (IOException var10) {
            throw new HttpPostRequestEncoder.ErrorDataEncoderException(var10);
        }

        this.addBodyHttpData(fileUpload);
    }

    public void addBodyFileUploads(String name, File[] file, String[] contentType, boolean[] isText) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        if(file.length != contentType.length && file.length != isText.length) {
            throw new IllegalArgumentException("Different array length");
        } else {
            for(int i = 0; i < file.length; ++i) {
                this.addBodyFileUpload(name, file[i], contentType[i], isText[i]);
            }

        }
    }

    public void addBodyHttpData(InterfaceHttpData data) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        if (this.headerFinalized) {
            throw new HttpPostRequestEncoder.ErrorDataEncoderException("Cannot add value once finalized");
        } else {
            this.bodyListDatas.add(ObjectUtil.checkNotNull(data, "data"));
            FileUpload fileUpload1;
            if (this.isMultipart) {
                if (data instanceof Attribute) {
                    MultipartAttribute fileUpload;
                    if (this.duringMixedMode) {
                        fileUpload = new MultipartAttribute(this.charset);
                        fileUpload.addValue("\r\n--" + this.multipartMixedBoundary + "--");
                        this.multipartHttpDatas.add(fileUpload);
                        this.multipartMixedBoundary = null;
                        this.currentFileUpload = null;
                        this.duringMixedMode = false;
                    }

                    fileUpload = new MultipartAttribute(this.charset);
                    if (!this.multipartHttpDatas.isEmpty()) {
                        fileUpload.addValue("\r\n");
                    }

                    fileUpload.addValue("--" + this.multipartDataBoundary + "\r\n");
                    Attribute internal = (Attribute) data;
                    try {
                        MimeType mimeType = new MimeType(this.mediaType);
                        if (HttpHeaderValues.MULTIPART_FORM_DATA.toString().equals(mimeType.getBaseType())) {
                            fileUpload.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": " + HttpHeaderValues.FORM_DATA + "; " + HttpHeaderValues.NAME + "=\"" + internal.getName() + "\"\r\n");
                        } else {
                            fileUpload.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": " + "inline" + "; " + "\r\n");
                        }
                    } catch (MimeTypeParseException e) {
                        e.printStackTrace();
                    }

                    fileUpload.addValue(HttpHeaderNames.CONTENT_LENGTH + ": " + internal.length() + "\r\n");
                    Charset localMixed = internal.getCharset();
                    if (localMixed != null) {
                        fileUpload.addValue(HttpHeaderNames.CONTENT_TYPE + ": " + "text/plain" + "; " + HttpHeaderValues.CHARSET + '=' + localMixed.name() + "\r\n");
                    }

                    fileUpload.addValue("\r\n");
                    this.multipartHttpDatas.add(fileUpload);
                    this.multipartHttpDatas.add(data);
                    this.globalBodySize += internal.length() + (long) fileUpload.size();
                } else if (data instanceof FileUpload) {
                    fileUpload1 = (FileUpload) data;
                    MultipartAttribute internal1 = new MultipartAttribute(this.charset);
                    if (!this.multipartHttpDatas.isEmpty()) {
                        internal1.addValue("\r\n");
                    }

                    boolean localMixed1;
                    if (this.duringMixedMode) {
                        if (this.currentFileUpload != null && this.currentFileUpload.getName().equals(fileUpload1.getName())) {
                            localMixed1 = true;
                        } else {
                            internal1.addValue("--" + this.multipartMixedBoundary + "--");
                            this.multipartHttpDatas.add(internal1);
                            this.multipartMixedBoundary = null;
                            internal1 = new MultipartAttribute(this.charset);
                            internal1.addValue("\r\n");
                            localMixed1 = false;
                            this.currentFileUpload = fileUpload1;
                            this.duringMixedMode = false;
                        }
                    } else if (this.encoderMode != HttpPostRequestEncoder.EncoderMode.HTML5 && this.currentFileUpload != null && this.currentFileUpload.getName().equals(fileUpload1.getName())) {
                        this.initMixedMultipart();
                        MultipartAttribute contentTransferEncoding = (MultipartAttribute) this.multipartHttpDatas.get(this.multipartHttpDatas.size() - 2);
                        this.globalBodySize -= (long) contentTransferEncoding.size();
                        StringBuilder replacement = (new StringBuilder(139 + this.multipartDataBoundary.length() + this.multipartMixedBoundary.length() * 2 + fileUpload1.getFilename().length() + fileUpload1.getName().length())).append("--").append(this.multipartDataBoundary).append("\r\n").append(HttpHeaderNames.CONTENT_DISPOSITION).append(": ").append(HttpHeaderValues.FORM_DATA).append("; ").append(HttpHeaderValues.NAME).append("=\"").append(fileUpload1.getName()).append("\"\r\n").append(HttpHeaderNames.CONTENT_TYPE).append(": ").append(HttpHeaderValues.MULTIPART_MIXED).append("; ").append(HttpHeaderValues.BOUNDARY).append('=').append(this.multipartMixedBoundary).append("\r\n\r\n").append("--").append(this.multipartMixedBoundary).append("\r\n").append(HttpHeaderNames.CONTENT_DISPOSITION).append(": ").append(HttpHeaderValues.ATTACHMENT);
                        if (!fileUpload1.getFilename().isEmpty()) {
                            replacement.append("; ").append(HttpHeaderValues.FILENAME).append("=\"").append(fileUpload1.getFilename()).append('\"');
                        }

                        replacement.append("\r\n");
                        contentTransferEncoding.setValue(replacement.toString(), 1);
                        contentTransferEncoding.setValue("", 2);
                        this.globalBodySize += (long) contentTransferEncoding.size();
                        localMixed1 = true;
                        this.duringMixedMode = true;
                    } else {
                        localMixed1 = false;
                        this.currentFileUpload = fileUpload1;
                        this.duringMixedMode = false;
                    }

                    if (localMixed1) {
                        internal1.addValue("--" + this.multipartMixedBoundary + "\r\n");
                        if (fileUpload1.getFilename().isEmpty()) {
                            internal1.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": " + HttpHeaderValues.ATTACHMENT + "\r\n");
                        } else {
                            internal1.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": " + HttpHeaderValues.ATTACHMENT + "; " + HttpHeaderValues.FILENAME + "=\"" + fileUpload1.getFilename() + "\"\r\n");
                        }
                    } else {
                        internal1.addValue("--" + this.multipartDataBoundary + "\r\n");
                        try {
                            MimeType mimeType = new MimeType(this.mediaType);
                            if (HttpHeaderValues.MULTIPART_FORM_DATA.toString().equals(mimeType.getBaseType())) {
                                if (fileUpload1.getFilename().isEmpty()) {
                                    internal1.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": " + HttpHeaderValues.FORM_DATA + "; " + HttpHeaderValues.NAME + "=\"" + fileUpload1.getName() + "\"\r\n");
                                } else {
                                    internal1.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": " + HttpHeaderValues.FORM_DATA + "; " + HttpHeaderValues.NAME + "=\"" + fileUpload1.getName() + "\"; " + HttpHeaderValues.FILENAME + "=\"" + fileUpload1.getFilename() + "\"\r\n");
                                }
                            } else {
                                if (fileUpload1.getFilename().isEmpty()) {
                                    internal1.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": " + HttpHeaderValues.ATTACHMENT + "\r\n");
                                } else {
                                    internal1.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": " + HttpHeaderValues.ATTACHMENT + "; " + HttpHeaderValues.FILENAME + "=\"" + fileUpload1.getFilename() + "\"\r\n");
                                }
                            }
                        } catch (MimeTypeParseException e) {
                            e.printStackTrace();
                        }
                    }

                    internal1.addValue(HttpHeaderNames.CONTENT_LENGTH + ": " + fileUpload1.length() + "\r\n");
                    internal1.addValue(HttpHeaderNames.CONTENT_TYPE + ": " + fileUpload1.getContentType());
                    String contentTransferEncoding1 = fileUpload1.getContentTransferEncoding();
                    if (contentTransferEncoding1 != null && contentTransferEncoding1.equals("binary")) {
                        internal1.addValue("\r\n" + HttpHeaderNames.CONTENT_TRANSFER_ENCODING + ": " + "binary" + "\r\n\r\n");
                    } else if (fileUpload1.getCharset() != null) {
                        internal1.addValue("; " + HttpHeaderValues.CHARSET + '=' + fileUpload1.getCharset().name() + "\r\n\r\n");
                    } else {
                        internal1.addValue("\r\n\r\n");
                    }

                    this.multipartHttpDatas.add(internal1);
                    this.multipartHttpDatas.add(data);
                    this.globalBodySize += fileUpload1.length() + (long) internal1.size();
                }

            }
        }
    }

    public HttpRequest finalizeRequest() throws HttpPostRequestEncoder.ErrorDataEncoderException {
        if (!this.headerFinalized) {
            if (this.isMultipart) {
                MultipartAttribute headers = new MultipartAttribute(this.charset);
                if (this.duringMixedMode) {
                    headers.addValue("\r\n--" + this.multipartMixedBoundary + "--");
                }

                headers.addValue("\r\n--" + this.multipartDataBoundary + "--\r\n");
                this.multipartHttpDatas.add(headers);
                this.multipartMixedBoundary = null;
                this.currentFileUpload = null;
                this.duringMixedMode = false;
                this.globalBodySize += (long) headers.size();
            }

            this.headerFinalized = true;
            HttpHeaders var9 = this.request.headers();
            List contentTypes = var9.getAll(HttpHeaderNames.CONTENT_TYPE);
            List transferEncoding = var9.getAll(HttpHeaderNames.TRANSFER_ENCODING);
            if (contentTypes != null) {
                var9.remove(HttpHeaderNames.CONTENT_TYPE);
                Iterator realSize = contentTypes.iterator();

                while (realSize.hasNext()) {
                    String contentType = (String) realSize.next();
                    String chunk = contentType.toLowerCase();
                    if (!chunk.startsWith(HttpHeaderValues.MULTIPART_MIXED.toString()) && !chunk.startsWith(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString())) {
                        var9.add(HttpHeaderNames.CONTENT_TYPE, contentType);
                    }
                }
            }

            if (this.isMultipart) {
                try {
                    MimeType mimeType = new MimeType(this.mediaType);
                    if (HttpHeaderValues.MULTIPART_FORM_DATA.toString().equals(mimeType.getBaseType())) {
                        String var10 = HttpHeaderValues.MULTIPART_FORM_DATA + "; " + HttpHeaderValues.BOUNDARY + '=' + this.multipartDataBoundary;
                        var9.add(HttpHeaderNames.CONTENT_TYPE, var10);
                    } else {
                        String var10 = HttpHeaderValues.MULTIPART_MIXED + "; " + HttpHeaderValues.BOUNDARY + '=' + this.multipartDataBoundary;
                        var9.add(HttpHeaderNames.CONTENT_TYPE, var10);
                    }
                } catch (MimeTypeParseException e) {
                    e.printStackTrace();
                }
            } else {
                var9.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED);
            }

            long var11 = this.globalBodySize;
            if (this.isMultipart) {
                this.iterator = this.multipartHttpDatas.listIterator();
            } else {
                --var11;
                this.iterator = this.multipartHttpDatas.listIterator();
            }

            var9.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(var11));
            if (var11 <= 8096L && !this.isMultipart) {
                HttpContent var13 = this.nextChunk();
                if (this.request instanceof FullHttpRequest) {
                    FullHttpRequest var14 = (FullHttpRequest) this.request;
                    ByteBuf chunkContent = var13.content();
                    if (var14.content() != chunkContent) {
                        var14.content().clear().writeBytes(chunkContent);
                        chunkContent.release();
                    }

                    return var14;
                } else {
                    return new GenericMultipartEncoder.WrappedFullHttpRequest(this.request, var13);
                }
            } else {
                this.isChunked = true;
                if (transferEncoding != null) {
                    var9.remove(HttpHeaderNames.TRANSFER_ENCODING);
                    Iterator var12 = transferEncoding.iterator();

                    while (var12.hasNext()) {
                        CharSequence fullRequest = (CharSequence) var12.next();
                        if (!HttpHeaderValues.CHUNKED.contentEqualsIgnoreCase(fullRequest)) {
                            var9.add(HttpHeaderNames.TRANSFER_ENCODING, fullRequest);
                        }
                    }
                }

                HttpUtil.setTransferEncodingChunked(this.request, true);
                return new GenericMultipartEncoder.WrappedHttpRequest(this.request);
            }
        } else {
            throw new HttpPostRequestEncoder.ErrorDataEncoderException("Header already encoded");
        }
    }

    private static final class WrappedFullHttpRequest extends GenericMultipartEncoder.WrappedHttpRequest implements FullHttpRequest {
        private final HttpContent content;

        private WrappedFullHttpRequest(HttpRequest request, HttpContent content) {
            super(request);
            this.content = content;
        }

        public FullHttpRequest setProtocolVersion(HttpVersion version) {
            super.setProtocolVersion(version);
            return this;
        }

        public FullHttpRequest setMethod(HttpMethod method) {
            super.setMethod(method);
            return this;
        }

        public FullHttpRequest setUri(String uri) {
            super.setUri(uri);
            return this;
        }

        public FullHttpRequest copy() {
            return this.replace(this.content().copy());
        }

        public FullHttpRequest duplicate() {
            return this.replace(this.content().duplicate());
        }

        public FullHttpRequest retainedDuplicate() {
            return this.replace(this.content().retainedDuplicate());
        }

        public FullHttpRequest replace(ByteBuf content) {
            DefaultFullHttpRequest duplicate = new DefaultFullHttpRequest(this.protocolVersion(), this.method(), this.uri(), content);
            duplicate.headers().set(this.headers());
            duplicate.trailingHeaders().set(this.trailingHeaders());
            return duplicate;
        }

        public FullHttpRequest retain(int increment) {
            this.content.retain(increment);
            return this;
        }

        public FullHttpRequest retain() {
            this.content.retain();
            return this;
        }

        public FullHttpRequest touch() {
            this.content.touch();
            return this;
        }

        public FullHttpRequest touch(Object hint) {
            this.content.touch(hint);
            return this;
        }

        public ByteBuf content() {
            return this.content.content();
        }

        public HttpHeaders trailingHeaders() {
            return (HttpHeaders) (this.content instanceof LastHttpContent ? ((LastHttpContent) this.content).trailingHeaders() : EmptyHttpHeaders.INSTANCE);
        }

        public int refCnt() {
            return this.content.refCnt();
        }

        public boolean release() {
            return this.content.release();
        }

        public boolean release(int decrement) {
            return this.content.release(decrement);
        }
    }

    private static class WrappedHttpRequest implements HttpRequest {
        private final HttpRequest request;

        WrappedHttpRequest(HttpRequest request) {
            this.request = request;
        }

        public HttpRequest setProtocolVersion(HttpVersion version) {
            this.request.setProtocolVersion(version);
            return this;
        }

        public HttpRequest setMethod(HttpMethod method) {
            this.request.setMethod(method);
            return this;
        }

        public HttpRequest setUri(String uri) {
            this.request.setUri(uri);
            return this;
        }

        public HttpMethod getMethod() {
            return this.request.method();
        }

        public HttpMethod method() {
            return this.request.method();
        }

        public String getUri() {
            return this.request.uri();
        }

        public String uri() {
            return this.request.uri();
        }

        public HttpVersion getProtocolVersion() {
            return this.request.protocolVersion();
        }

        public HttpVersion protocolVersion() {
            return this.request.protocolVersion();
        }

        public HttpHeaders headers() {
            return this.request.headers();
        }

        public DecoderResult decoderResult() {
            return this.request.decoderResult();
        }

        /**
         * @deprecated
         */
        @Deprecated
        public DecoderResult getDecoderResult() {
            return this.request.getDecoderResult();
        }

        public void setDecoderResult(DecoderResult result) {
            this.request.setDecoderResult(result);
        }
    }


    @Override
    public void close() throws Exception {

    }


    /**
     * @deprecated
     */
    @Deprecated
    public HttpContent readChunk(ChannelHandlerContext ctx) throws Exception {
        return this.readChunk(ctx.alloc());
    }

    public HttpContent readChunk(ByteBufAllocator allocator) throws Exception {
        if (this.isLastChunkSent) {
            return null;
        } else {
            HttpContent nextChunk = this.nextChunk();
            this.globalProgress += (long) nextChunk.content().readableBytes();
            return nextChunk;
        }
    }

    private HttpContent nextChunk() throws HttpPostRequestEncoder.ErrorDataEncoderException {
        if (this.isLastChunk) {
            this.isLastChunkSent = true;
            return LastHttpContent.EMPTY_LAST_CONTENT;
        } else {
            int size = this.calculateRemainingSize();
            if (size <= 0) {
                ByteBuf chunk1 = this.fillByteBuf();
                return new DefaultHttpContent(chunk1);
            } else {
                HttpContent chunk;
                if (this.currentData != null) {
                    if (this.isMultipart) {
                        chunk = this.encodeNextChunkMultipart(size);
                    } else {
                        chunk = this.encodeNextChunkUrlEncoded(size);
                    }

                    if (chunk != null) {
                        return chunk;
                    }

                    size = this.calculateRemainingSize();
                }

                if (!this.iterator.hasNext()) {
                    return this.lastChunk();
                } else {
                    while (size > 0 && this.iterator.hasNext()) {
                        this.currentData = (InterfaceHttpData) this.iterator.next();
                        if (this.isMultipart) {
                            chunk = this.encodeNextChunkMultipart(size);
                        } else {
                            chunk = this.encodeNextChunkUrlEncoded(size);
                        }

                        if (chunk != null) {
                            return chunk;
                        }

                        size = this.calculateRemainingSize();
                    }

                    return this.lastChunk();
                }
            }
        }
    }

    private int calculateRemainingSize() {
        int size = 8096;
        if (this.currentBuffer != null) {
            size -= this.currentBuffer.readableBytes();
        }

        return size;
    }

    private HttpContent lastChunk() {
        this.isLastChunk = true;
        if (this.currentBuffer == null) {
            this.isLastChunkSent = true;
            return LastHttpContent.EMPTY_LAST_CONTENT;
        } else {
            ByteBuf buffer = this.currentBuffer;
            this.currentBuffer = null;
            return new DefaultHttpContent(buffer);
        }
    }

    public boolean isEndOfInput() throws Exception {
        return this.isLastChunkSent;
    }

    public long length() {
        return this.isMultipart ? this.globalBodySize : this.globalBodySize - 1L;
    }

    public long progress() {
        return this.globalProgress;
    }

    private ByteBuf fillByteBuf() {
        int length = this.currentBuffer.readableBytes();
        if (length > 8096) {
            return this.currentBuffer.readRetainedSlice(8096);
        } else {
            ByteBuf slice = this.currentBuffer;
            this.currentBuffer = null;
            return slice;
        }
    }

    private HttpContent encodeNextChunkMultipart(int sizeleft) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        if (this.currentData == null) {
            return null;
        } else {
            ByteBuf buffer;
            if (this.currentData instanceof MultipartAttribute) {
                buffer = ((MultipartAttribute) this.currentData).toByteBuf();
                this.currentData = null;
            } else {
                try {
                    buffer = ((HttpData) this.currentData).getChunk(sizeleft);
                } catch (IOException var4) {
                    throw new HttpPostRequestEncoder.ErrorDataEncoderException(var4);
                }

                if (buffer.capacity() == 0) {
                    this.currentData = null;
                    return null;
                }
            }

            if (this.currentBuffer == null) {
                this.currentBuffer = buffer;
            } else {
                this.currentBuffer = Unpooled.wrappedBuffer(new ByteBuf[]{this.currentBuffer, buffer});
            }

            if (this.currentBuffer.readableBytes() < 8096) {
                this.currentData = null;
                return null;
            } else {
                buffer = this.fillByteBuf();
                return new DefaultHttpContent(buffer);
            }
        }
    }

    private HttpContent encodeNextChunkUrlEncoded(int sizeleft) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        if (this.currentData == null) {
            return null;
        } else {
            int size = sizeleft;
            ByteBuf buffer;
            if (this.isKey) {
                String delimiter = this.currentData.getName();
                buffer = Unpooled.wrappedBuffer(delimiter.getBytes());
                this.isKey = false;
                if (this.currentBuffer == null) {
                    this.currentBuffer = Unpooled.wrappedBuffer(new ByteBuf[]{buffer, Unpooled.wrappedBuffer("=".getBytes())});
                    size = sizeleft - (buffer.readableBytes() + 1);
                } else {
                    this.currentBuffer = Unpooled.wrappedBuffer(new ByteBuf[]{this.currentBuffer, buffer, Unpooled.wrappedBuffer("=".getBytes())});
                    size = sizeleft - (buffer.readableBytes() + 1);
                }

                if (this.currentBuffer.readableBytes() >= 8096) {
                    buffer = this.fillByteBuf();
                    return new DefaultHttpContent(buffer);
                }
            }

            try {
                buffer = ((HttpData) this.currentData).getChunk(size);
            } catch (IOException var5) {
                throw new HttpPostRequestEncoder.ErrorDataEncoderException(var5);
            }

            ByteBuf delimiter1 = null;
            if (buffer.readableBytes() < size) {
                this.isKey = true;
                delimiter1 = this.iterator.hasNext() ? Unpooled.wrappedBuffer("&".getBytes()) : null;
            }

            if (buffer.capacity() == 0) {
                this.currentData = null;
                if (this.currentBuffer == null) {
                    this.currentBuffer = delimiter1;
                } else if (delimiter1 != null) {
                    this.currentBuffer = Unpooled.wrappedBuffer(new ByteBuf[]{this.currentBuffer, delimiter1});
                }

                if (this.currentBuffer.readableBytes() >= 8096) {
                    buffer = this.fillByteBuf();
                    return new DefaultHttpContent(buffer);
                } else {
                    return null;
                }
            } else {
                if (this.currentBuffer == null) {
                    if (delimiter1 != null) {
                        this.currentBuffer = Unpooled.wrappedBuffer(new ByteBuf[]{buffer, delimiter1});
                    } else {
                        this.currentBuffer = buffer;
                    }
                } else if (delimiter1 != null) {
                    this.currentBuffer = Unpooled.wrappedBuffer(new ByteBuf[]{this.currentBuffer, buffer, delimiter1});
                } else {
                    this.currentBuffer = Unpooled.wrappedBuffer(new ByteBuf[]{this.currentBuffer, buffer});
                }

                if (this.currentBuffer.readableBytes() < 8096) {
                    this.currentData = null;
                    this.isKey = true;
                    return null;
                } else {
                    buffer = this.fillByteBuf();
                    return new DefaultHttpContent(buffer);
                }
            }
        }
    }

    public void cleanFiles() {
        this.factory.cleanRequestHttpData(this.request);
    }

    public static class ErrorDataEncoderException extends Exception {
        private static final long serialVersionUID = 5020247425493164465L;

        public ErrorDataEncoderException() {
        }

        public ErrorDataEncoderException(String msg) {
            super(msg);
        }

        public ErrorDataEncoderException(Throwable cause) {
            super(cause);
        }

        public ErrorDataEncoderException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    public static enum EncoderMode {
        RFC1738,
        RFC3986,
        HTML5;

        private EncoderMode() {
        }
    }
}
