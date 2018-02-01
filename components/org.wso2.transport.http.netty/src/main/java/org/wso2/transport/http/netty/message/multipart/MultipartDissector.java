package org.wso2.transport.http.netty.message.multipart;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpPostRequestDecoder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MultipartDissector implements InterfaceHttpPostRequestDecoder {

    private final HttpDataFactory factory;
    private final HttpRequest request;
    private Charset charset;
    private boolean isLastChunk;
    private final List<InterfaceHttpData> bodyListHttpData;
    private final Map<String, List<InterfaceHttpData>> bodyMapHttpData;
    private ByteBuf undecodedChunk;
    private int bodyListHttpDataRank;
    private String multipartDataBoundary;
    private String multipartMixedBoundary;
    private GenericMultipartDecoder.MultiPartStatus currentStatus;
    private Map<CharSequence, Attribute> currentFieldAttributes;
    private FileUpload currentFileUpload;
    private Attribute currentAttribute;
    private boolean destroyed;
    private int discardThreshold;
    private static final String FILENAME_ENCODED;

    public MultipartDissector(HttpRequest request) {
        this(new DefaultHttpDataFactory(16384L), request, HttpConstants.DEFAULT_CHARSET);
    }

    public MultipartDissector(HttpDataFactory factory, HttpRequest request) {
        this(factory, request, HttpConstants.DEFAULT_CHARSET);
    }

    public MultipartDissector(HttpDataFactory factory, HttpRequest request, Charset charset) {
        this.bodyListHttpData = new ArrayList();
        this.bodyMapHttpData = new TreeMap(CaseIgnoringComparator.INSTANCE);
        this.currentStatus = GenericMultipartDecoder.MultiPartStatus.NOTSTARTED;
        this.discardThreshold = 10485760;
        this.request = (HttpRequest) ObjectUtil.checkNotNull(request, "request");
        this.charset = (Charset) ObjectUtil.checkNotNull(charset, "charset");
        this.factory = (HttpDataFactory) ObjectUtil.checkNotNull(factory, "factory");
        this.setMultipart(this.request.headers().get(HttpHeaderNames.CONTENT_TYPE));
        if (request instanceof HttpContent) {
            this.offer((HttpContent) request);
        } else {
            this.undecodedChunk = Unpooled.buffer();
            this.parseBody();
        }

    }

    private void setMultipart(String contentType) {
        String[] dataBoundary = GenericMultipartDecoder.getMultipartDataBoundary(contentType);
        if (dataBoundary != null) {
            this.multipartDataBoundary = dataBoundary[0];
            if (dataBoundary.length > 1 && dataBoundary[1] != null) {
                this.charset = Charset.forName(dataBoundary[1]);
            }
        } else {
            this.multipartDataBoundary = null;
        }

        this.currentStatus = GenericMultipartDecoder.MultiPartStatus.HEADERDELIMITER;
    }

    private void checkDestroyed() {
        if (this.destroyed) {
            throw new IllegalStateException(HttpPostMultipartRequestDecoder.class.getSimpleName() + " was destroyed already");
        }
    }

    public boolean isMultipart() {
        this.checkDestroyed();
        return true;
    }

    public void setDiscardThreshold(int discardThreshold) {
        this.discardThreshold = ObjectUtil.checkPositiveOrZero(discardThreshold, "discardThreshold");
    }

    public int getDiscardThreshold() {
        return this.discardThreshold;
    }

    public List<InterfaceHttpData> getBodyHttpDatas() {
        this.checkDestroyed();
        if (!this.isLastChunk) {
            throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
        } else {
            return this.bodyListHttpData;
        }
    }

    public List<InterfaceHttpData> getBodyHttpDatas(String name) {
        this.checkDestroyed();
        if (!this.isLastChunk) {
            throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
        } else {
            return (List) this.bodyMapHttpData.get(name);
        }
    }

    public InterfaceHttpData getBodyHttpData(String name) {
        this.checkDestroyed();
        if (!this.isLastChunk) {
            throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
        } else {
            List list = (List) this.bodyMapHttpData.get(name);
            return list != null ? (InterfaceHttpData) list.get(0) : null;
        }
    }

    public MultipartDissector offer(HttpContent content) {
        this.checkDestroyed();
        ByteBuf buf = content.content();
        if (this.undecodedChunk == null) {
            this.undecodedChunk = buf.copy();
        } else {
            this.undecodedChunk.writeBytes(buf);
        }

        if (content instanceof LastHttpContent) {
            this.isLastChunk = true;
        }

        this.parseBody();
        if (this.undecodedChunk != null && this.undecodedChunk.writerIndex() > this.discardThreshold) {
            this.undecodedChunk.discardReadBytes();
        }

        return this;
    }

    public boolean hasNext() {
        this.checkDestroyed();
        if (this.currentStatus == GenericMultipartDecoder.MultiPartStatus.EPILOGUE && this.bodyListHttpDataRank >= this.bodyListHttpData.size()) {
            throw new HttpPostRequestDecoder.EndOfDataDecoderException();
        } else {
            return !this.bodyListHttpData.isEmpty() && this.bodyListHttpDataRank < this.bodyListHttpData.size();
        }
    }

    public InterfaceHttpData next() {
        this.checkDestroyed();
        return this.hasNext() ? (InterfaceHttpData) this.bodyListHttpData.get(this.bodyListHttpDataRank++) : null;
    }

    public InterfaceHttpData currentPartialHttpData() {
        return (InterfaceHttpData) (this.currentFileUpload != null ? this.currentFileUpload : this.currentAttribute);
    }

    private void parseBody() {
        if (this.currentStatus != GenericMultipartDecoder.MultiPartStatus.PREEPILOGUE && this.currentStatus != GenericMultipartDecoder.MultiPartStatus.EPILOGUE) {
            this.parseBodyMultipart();
        } else {
            if (this.isLastChunk) {
                this.currentStatus = GenericMultipartDecoder.MultiPartStatus.EPILOGUE;
            }

        }
    }

    /**
     * Utility function to add a new decoded data
     */
    protected void addHttpData(InterfaceHttpData data) {
        if (data == null) {
            return;
        }
        List<InterfaceHttpData> datas = bodyMapHttpData.get(data.getName());
        if (datas == null) {
            datas = new ArrayList<InterfaceHttpData>(1);
            bodyMapHttpData.put(data.getName(), datas);
        }
        datas.add(data);
        bodyListHttpData.add(data);
    }

    private void parseBodyMultipart() {
        if (this.undecodedChunk != null && this.undecodedChunk.readableBytes() != 0) {
            for (InterfaceHttpData data = this.decodeMultipart(this.currentStatus); data != null; data = this.decodeMultipart(this.currentStatus)) {
                this.addHttpData(data);
                if (this.currentStatus == GenericMultipartDecoder.MultiPartStatus.PREEPILOGUE || this.currentStatus == GenericMultipartDecoder.MultiPartStatus.EPILOGUE) {
                    break;
                }
            }

        }
    }

    private InterfaceHttpData decodeMultipart(GenericMultipartDecoder.MultiPartStatus state) {
        switch (state) {
            case NOTSTARTED:
                throw new HttpPostRequestDecoder.ErrorDataDecoderException("Should not be called with the current getStatus");
            case PREAMBLE:
                // Content-type: multipart/form-data, boundary=AaB03x
                throw new HttpPostRequestDecoder.ErrorDataDecoderException("Should not be called with the current getStatus");
            case HEADERDELIMITER: {
                // --AaB03x or --AaB03x--
                return findMultipartDelimiter(multipartDataBoundary, GenericMultipartDecoder.MultiPartStatus.DISPOSITION,
                        GenericMultipartDecoder.MultiPartStatus.PREEPILOGUE);
            }
            case DISPOSITION: {
                // content-disposition: form-data; name="field1"
                // content-disposition: form-data; name="pics"; filename="file1.txt"
                // and other immediate values like
                // Content-type: image/gif
                // Content-Type: text/plain
                // Content-Type: text/plain; charset=ISO-8859-1
                // Content-Transfer-Encoding: binary
                // The following line implies a change of mode (mixed mode)
                // Content-type: multipart/mixed, boundary=BbC04y
                return findMultipartDisposition();
            }
            case FIELD: {
                // Now get value according to Content-Type and Charset
                Charset localCharset = null;
                Attribute charsetAttribute = currentFieldAttributes.get(HttpHeaderValues.CHARSET);
                if (charsetAttribute != null) {
                    try {
                        localCharset = Charset.forName(charsetAttribute.getValue());
                    } catch (IOException e) {
                        throw new HttpPostRequestDecoder.ErrorDataDecoderException(e);
                    } catch (UnsupportedCharsetException e) {
                        throw new HttpPostRequestDecoder.ErrorDataDecoderException(e);
                    }
                }
                Attribute nameAttribute = currentFieldAttributes.get(HttpHeaderValues.NAME);
                if (currentAttribute == null) {
                    Attribute lengthAttribute = currentFieldAttributes
                            .get(HttpHeaderNames.CONTENT_LENGTH);
                    long size;
                    try {
                        size = lengthAttribute != null ? Long.parseLong(lengthAttribute
                                .getValue()) : 0L;
                    } catch (IOException e) {
                        throw new HttpPostRequestDecoder.ErrorDataDecoderException(e);
                    } catch (NumberFormatException ignored) {
                        size = 0;
                    }
                    try {
                        if (size > 0) {
                            currentAttribute = factory.createAttribute(request,
                                    cleanString(nameAttribute.getValue()), size);
                        } else {
                            currentAttribute = factory.createAttribute(request,
                                    cleanString(nameAttribute.getValue()));
                        }
                    } catch (NullPointerException e) {
                        throw new HttpPostRequestDecoder.ErrorDataDecoderException(e);
                    } catch (IllegalArgumentException e) {
                        throw new HttpPostRequestDecoder.ErrorDataDecoderException(e);
                    } catch (IOException e) {
                        throw new HttpPostRequestDecoder.ErrorDataDecoderException(e);
                    }
                    if (localCharset != null) {
                        currentAttribute.setCharset(localCharset);
                    }
                }
                // load data
                if (!loadDataMultipart(undecodedChunk, multipartDataBoundary, currentAttribute)) {
                    // Delimiter is not found. Need more chunks.
                    return null;
                }
                Attribute finalAttribute = currentAttribute;
                currentAttribute = null;
                currentFieldAttributes = null;
                // ready to load the next one
                currentStatus = GenericMultipartDecoder.MultiPartStatus.HEADERDELIMITER;
                return finalAttribute;
            }
            case FILEUPLOAD: {
                // eventually restart from existing FileUpload
                return getFileUpload(multipartDataBoundary);
            }
            case MIXEDDELIMITER: {
                // --AaB03x or --AaB03x--
                // Note that currentFieldAttributes exists
                return findMultipartDelimiter(multipartMixedBoundary, GenericMultipartDecoder.MultiPartStatus.MIXEDDISPOSITION,
                        GenericMultipartDecoder.MultiPartStatus.HEADERDELIMITER);
            }
            case MIXEDDISPOSITION: {
                return findMultipartDisposition();
            }
            case MIXEDFILEUPLOAD: {
                // eventually restart from existing FileUpload
                return getFileUpload(multipartMixedBoundary);
            }
            case PREEPILOGUE:
                return null;
            case EPILOGUE:
                return null;
            default:
                throw new HttpPostRequestDecoder.ErrorDataDecoderException("Shouldn't reach here.");
        }
    }

    private static void skipControlCharacters(ByteBuf undecodedChunk) {
        if (!undecodedChunk.hasArray()) {
            try {
                skipControlCharactersStandard(undecodedChunk);
            } catch (IndexOutOfBoundsException var3) {
                throw new HttpPostRequestDecoder.NotEnoughDataDecoderException(var3);
            }
        } else {
            PostBodyUtil.SeekAheadOptimize sao = new PostBodyUtil.SeekAheadOptimize(undecodedChunk);

            char c;
            do {
                if (sao.pos >= sao.limit) {
                    throw new HttpPostRequestDecoder.NotEnoughDataDecoderException("Access out of bounds");
                }

                c = (char) (sao.bytes[sao.pos++] & 255);
            } while (Character.isISOControl(c) || Character.isWhitespace(c));

            sao.setReadPosition(1);
        }
    }

    private static void skipControlCharactersStandard(ByteBuf undecodedChunk) {
        char c;
        do {
            c = (char) undecodedChunk.readUnsignedByte();
        } while (Character.isISOControl(c) || Character.isWhitespace(c));

        undecodedChunk.readerIndex(undecodedChunk.readerIndex() - 1);
    }

    private InterfaceHttpData findMultipartDelimiter(String delimiter, GenericMultipartDecoder.MultiPartStatus dispositionStatus, GenericMultipartDecoder.MultiPartStatus closeDelimiterStatus) {
        int readerIndex = this.undecodedChunk.readerIndex();

        try {
            skipControlCharacters(this.undecodedChunk);
        } catch (HttpPostRequestDecoder.NotEnoughDataDecoderException var8) {
            this.undecodedChunk.readerIndex(readerIndex);
            return null;
        }

        this.skipOneLine();

        String newline;
        try {
            newline = readDelimiter(this.undecodedChunk, delimiter);
        } catch (HttpPostRequestDecoder.NotEnoughDataDecoderException var7) {
            this.undecodedChunk.readerIndex(readerIndex);
            return null;
        }

        if (newline.equals(delimiter)) {
            this.currentStatus = dispositionStatus;
            return this.decodeMultipart(dispositionStatus);
        } else if (newline.equals(delimiter + "--")) {
            this.currentStatus = closeDelimiterStatus;
            if (this.currentStatus == GenericMultipartDecoder.MultiPartStatus.HEADERDELIMITER) {
                this.currentFieldAttributes = null;
                return this.decodeMultipart(GenericMultipartDecoder.MultiPartStatus.HEADERDELIMITER);
            } else {
                return null;
            }
        } else {
            this.undecodedChunk.readerIndex(readerIndex);
            throw new HttpPostRequestDecoder.ErrorDataDecoderException("No Multipart delimiter found");
        }
    }

    private InterfaceHttpData findMultipartDisposition() {
        int readerIndex = this.undecodedChunk.readerIndex();
        if (this.currentStatus == GenericMultipartDecoder.MultiPartStatus.DISPOSITION) {
            this.currentFieldAttributes = new TreeMap(CaseIgnoringComparator.INSTANCE);
        }

        while (!this.skipOneLine()) {
            String filenameAttribute;
            try {
                skipControlCharacters(this.undecodedChunk);
                filenameAttribute = readLine(this.undecodedChunk, this.charset);
            } catch (HttpPostRequestDecoder.NotEnoughDataDecoderException var19) {
                this.undecodedChunk.readerIndex(readerIndex);
                return null;
            }

            String[] contents = splitMultipartHeader(filenameAttribute);
            Attribute e;
            if (!HttpHeaderNames.CONTENT_DISPOSITION.contentEqualsIgnoreCase(contents[0])) {
                Attribute var24;
                if (HttpHeaderNames.CONTENT_TRANSFER_ENCODING.contentEqualsIgnoreCase(contents[0])) {
                    try {
                        var24 = this.factory.createAttribute(this.request, HttpHeaderNames.CONTENT_TRANSFER_ENCODING.toString(), cleanString(contents[1]));
                    } catch (NullPointerException var15) {
                        throw new HttpPostRequestDecoder.ErrorDataDecoderException(var15);
                    } catch (IllegalArgumentException var16) {
                        throw new HttpPostRequestDecoder.ErrorDataDecoderException(var16);
                    }

                    this.currentFieldAttributes.put(HttpHeaderNames.CONTENT_TRANSFER_ENCODING, var24);
                } else if (HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(contents[0])) {
                    try {
                        var24 = this.factory.createAttribute(this.request, HttpHeaderNames.CONTENT_LENGTH.toString(), cleanString(contents[1]));
                    } catch (NullPointerException var13) {
                        throw new HttpPostRequestDecoder.ErrorDataDecoderException(var13);
                    } catch (IllegalArgumentException var14) {
                        throw new HttpPostRequestDecoder.ErrorDataDecoderException(var14);
                    }

                    this.currentFieldAttributes.put(HttpHeaderNames.CONTENT_LENGTH, var24);
                } else {
                    if (!HttpHeaderNames.CONTENT_TYPE.contentEqualsIgnoreCase(contents[0])) {
                        throw new HttpPostRequestDecoder.ErrorDataDecoderException("Unknown Params: " + filenameAttribute);
                    }

                    if (HttpHeaderValues.MULTIPART_MIXED.contentEqualsIgnoreCase(contents[1])) {
                        if (this.currentStatus == GenericMultipartDecoder.MultiPartStatus.DISPOSITION) {
                            String var22 = StringUtil.substringAfter(contents[2], '=');
                            this.multipartMixedBoundary = "--" + var22;
                            this.currentStatus = GenericMultipartDecoder.MultiPartStatus.MIXEDDELIMITER;
                            return this.decodeMultipart(GenericMultipartDecoder.MultiPartStatus.MIXEDDELIMITER);
                        }

                        throw new HttpPostRequestDecoder.ErrorDataDecoderException("Mixed Multipart found in a previous Mixed Multipart");
                    }

                    for (int var21 = 1; var21 < contents.length; ++var21) {
                        String var23 = HttpHeaderValues.CHARSET.toString();
                        if (contents[var21].regionMatches(true, 0, var23, 0, var23.length())) {
                            String var25 = StringUtil.substringAfter(contents[var21], '=');

                            try {
                                e = this.factory.createAttribute(this.request, var23, cleanString(var25));
                            } catch (NullPointerException var11) {
                                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var11);
                            } catch (IllegalArgumentException var12) {
                                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var12);
                            }

                            this.currentFieldAttributes.put(HttpHeaderValues.CHARSET, e);
                        } else {
                            Attribute var26;
                            try {
                                var26 = this.factory.createAttribute(this.request, cleanString(contents[0]), contents[var21]);
                            } catch (NullPointerException var9) {
                                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var9);
                            } catch (IllegalArgumentException var10) {
                                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var10);
                            }

                            this.currentFieldAttributes.put(var26.getName(), var26);
                        }
                    }
                }
            } else {
                boolean i;
                if (this.currentStatus == GenericMultipartDecoder.MultiPartStatus.DISPOSITION) {
                    i = HttpHeaderValues.FORM_DATA.contentEqualsIgnoreCase(contents[1]);
                } else {
                    i = HttpHeaderValues.ATTACHMENT.contentEqualsIgnoreCase(contents[1]) || HttpHeaderValues.FILE.contentEqualsIgnoreCase(contents[1]);
                }

                if (i) {
                    for (int charsetHeader = 2; charsetHeader < contents.length; ++charsetHeader) {
                        String[] attribute = contents[charsetHeader].split("=", 2);

                        try {
                            e = this.getContentDispositionAttribute(attribute);
                        } catch (NullPointerException var17) {
                            throw new HttpPostRequestDecoder.ErrorDataDecoderException(var17);
                        } catch (IllegalArgumentException var18) {
                            throw new HttpPostRequestDecoder.ErrorDataDecoderException(var18);
                        }

                        this.currentFieldAttributes.put(e.getName(), e);
                    }
                }
            }
        }

        Attribute var20 = (Attribute) this.currentFieldAttributes.get(HttpHeaderValues.FILENAME);
        if (this.currentStatus == GenericMultipartDecoder.MultiPartStatus.DISPOSITION) {
            if (var20 != null) {
                this.currentStatus = GenericMultipartDecoder.MultiPartStatus.FILEUPLOAD;
                return this.decodeMultipart(GenericMultipartDecoder.MultiPartStatus.FILEUPLOAD);
            } else {
                this.currentStatus = GenericMultipartDecoder.MultiPartStatus.FIELD;
                return this.decodeMultipart(GenericMultipartDecoder.MultiPartStatus.FIELD);
            }
        } else if (var20 != null) {
            this.currentStatus = GenericMultipartDecoder.MultiPartStatus.MIXEDFILEUPLOAD;
            return this.decodeMultipart(GenericMultipartDecoder.MultiPartStatus.MIXEDFILEUPLOAD);
        } else {
            throw new HttpPostRequestDecoder.ErrorDataDecoderException("Filename not found");
        }
    }

    private Attribute getContentDispositionAttribute(String... values) {
        String name = cleanString(values[0]);
        String value = values[1];
        if (HttpHeaderValues.FILENAME.contentEquals(name)) {
            int e = value.length() - 1;
            if (e > 0 && value.charAt(0) == 34 && value.charAt(e) == 34) {
                value = value.substring(1, e);
            }
        } else if (FILENAME_ENCODED.equals(name)) {
            try {
                name = HttpHeaderValues.FILENAME.toString();
                String[] e1 = value.split("\'", 3);
                value = QueryStringDecoder.decodeComponent(e1[2], Charset.forName(e1[0]));
            } catch (ArrayIndexOutOfBoundsException var5) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var5);
            } catch (UnsupportedCharsetException var6) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var6);
            }
        } else {
            value = cleanString(value);
        }

        return this.factory.createAttribute(this.request, name, value);
    }

    protected InterfaceHttpData getFileUpload(String delimiter) {
        Attribute encoding = (Attribute) this.currentFieldAttributes.get(HttpHeaderNames.CONTENT_TRANSFER_ENCODING);
        Charset localCharset = this.charset;
        PostBodyUtil.TransferEncodingMechanism mechanism = PostBodyUtil.TransferEncodingMechanism.BIT7;
        if (encoding != null) {
            String charsetAttribute;
            try {
                charsetAttribute = encoding.getValue().toLowerCase();
            } catch (IOException var20) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var20);
            }

            if (charsetAttribute.equals(PostBodyUtil.TransferEncodingMechanism.BIT7.value())) {
                localCharset = CharsetUtil.US_ASCII;
            } else if (charsetAttribute.equals(PostBodyUtil.TransferEncodingMechanism.BIT8.value())) {
                localCharset = CharsetUtil.ISO_8859_1;
                mechanism = PostBodyUtil.TransferEncodingMechanism.BIT8;
            } else {
                if (!charsetAttribute.equals(PostBodyUtil.TransferEncodingMechanism.BINARY.value())) {
                    throw new HttpPostRequestDecoder.ErrorDataDecoderException("TransferEncoding Unknown: " + charsetAttribute);
                }

                mechanism = PostBodyUtil.TransferEncodingMechanism.BINARY;
            }
        }

        Attribute charsetAttribute1 = (Attribute) this.currentFieldAttributes.get(HttpHeaderValues.CHARSET);
        if (charsetAttribute1 != null) {
            try {
                localCharset = Charset.forName(charsetAttribute1.getValue());
            } catch (IOException var18) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var18);
            } catch (UnsupportedCharsetException var19) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var19);
            }
        }

        if (this.currentFileUpload == null) {
            Attribute fileUpload = (Attribute) this.currentFieldAttributes.get(HttpHeaderValues.FILENAME);
            Attribute nameAttribute = (Attribute) this.currentFieldAttributes.get(HttpHeaderValues.NAME);
            Attribute contentTypeAttribute = (Attribute) this.currentFieldAttributes.get(HttpHeaderNames.CONTENT_TYPE);
            Attribute lengthAttribute = (Attribute) this.currentFieldAttributes.get(HttpHeaderNames.CONTENT_LENGTH);

            long size;
            try {
                size = lengthAttribute != null ? Long.parseLong(lengthAttribute.getValue()) : 0L;
            } catch (IOException var16) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var16);
            } catch (NumberFormatException var17) {
                size = 0L;
            }

            try {
                String e;
                if (contentTypeAttribute != null) {
                    e = contentTypeAttribute.getValue();
                } else {
                    e = "application/octet-stream";
                }

                this.currentFileUpload = this.factory.createFileUpload(this.request, cleanString(nameAttribute.getValue()), cleanString(fileUpload.getValue()), e, mechanism.value(), localCharset, size);
            } catch (NullPointerException var13) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var13);
            } catch (IllegalArgumentException var14) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var14);
            } catch (IOException var15) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var15);
            }
        }

        if (!loadDataMultipart(this.undecodedChunk, delimiter, this.currentFileUpload)) {
            return null;
        } else if (this.currentFileUpload.isCompleted()) {
            if (this.currentStatus == GenericMultipartDecoder.MultiPartStatus.FILEUPLOAD) {
                this.currentStatus = GenericMultipartDecoder.MultiPartStatus.HEADERDELIMITER;
                this.currentFieldAttributes = null;
            } else {
                this.currentStatus = GenericMultipartDecoder.MultiPartStatus.MIXEDDELIMITER;
                this.cleanMixedAttributes();
            }

            FileUpload fileUpload1 = this.currentFileUpload;
            this.currentFileUpload = null;
            return fileUpload1;
        } else {
            return null;
        }
    }

    public void destroy() {
        this.checkDestroyed();
        this.cleanFiles();
        this.destroyed = true;
        if (this.undecodedChunk != null && this.undecodedChunk.refCnt() > 0) {
            this.undecodedChunk.release();
            this.undecodedChunk = null;
        }

        for (int i = this.bodyListHttpDataRank; i < this.bodyListHttpData.size(); ++i) {
            ((InterfaceHttpData) this.bodyListHttpData.get(i)).release();
        }

    }

    public void cleanFiles() {
        this.checkDestroyed();
        this.factory.cleanRequestHttpData(this.request);
    }

    public void removeHttpDataFromClean(InterfaceHttpData data) {
        this.checkDestroyed();
        this.factory.removeHttpDataFromClean(this.request, data);
    }

    private void cleanMixedAttributes() {
        this.currentFieldAttributes.remove(HttpHeaderValues.CHARSET);
        this.currentFieldAttributes.remove(HttpHeaderNames.CONTENT_LENGTH);
        this.currentFieldAttributes.remove(HttpHeaderNames.CONTENT_TRANSFER_ENCODING);
        this.currentFieldAttributes.remove(HttpHeaderNames.CONTENT_TYPE);
        this.currentFieldAttributes.remove(HttpHeaderValues.FILENAME);
    }

    private static String readLineStandard(ByteBuf undecodedChunk, Charset charset) {
        int readerIndex = undecodedChunk.readerIndex();

        try {
            ByteBuf e = Unpooled.buffer(64);

            while (undecodedChunk.isReadable()) {
                byte nextByte = undecodedChunk.readByte();
                if (nextByte == 13) {
                    nextByte = undecodedChunk.getByte(undecodedChunk.readerIndex());
                    if (nextByte == 10) {
                        undecodedChunk.readByte();
                        return e.toString(charset);
                    }

                    e.writeByte(13);
                } else {
                    if (nextByte == 10) {
                        return e.toString(charset);
                    }

                    e.writeByte(nextByte);
                }
            }
        } catch (IndexOutOfBoundsException var5) {
            undecodedChunk.readerIndex(readerIndex);
            throw new HttpPostRequestDecoder.NotEnoughDataDecoderException(var5);
        }

        undecodedChunk.readerIndex(readerIndex);
        throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
    }

    private static String readLine(ByteBuf undecodedChunk, Charset charset) {
        if (!undecodedChunk.hasArray()) {
            return readLineStandard(undecodedChunk, charset);
        } else {
            PostBodyUtil.SeekAheadOptimize sao = new PostBodyUtil.SeekAheadOptimize(undecodedChunk);
            int readerIndex = undecodedChunk.readerIndex();

            try {
                ByteBuf e = Unpooled.buffer(64);

                while (sao.pos < sao.limit) {
                    byte nextByte = sao.bytes[sao.pos++];
                    if (nextByte == 13) {
                        if (sao.pos < sao.limit) {
                            nextByte = sao.bytes[sao.pos++];
                            if (nextByte == 10) {
                                sao.setReadPosition(0);
                                return e.toString(charset);
                            }

                            --sao.pos;
                            e.writeByte(13);
                        } else {
                            e.writeByte(nextByte);
                        }
                    } else {
                        if (nextByte == 10) {
                            sao.setReadPosition(0);
                            return e.toString(charset);
                        }

                        e.writeByte(nextByte);
                    }
                }
            } catch (IndexOutOfBoundsException var6) {
                undecodedChunk.readerIndex(readerIndex);
                throw new HttpPostRequestDecoder.NotEnoughDataDecoderException(var6);
            }

            undecodedChunk.readerIndex(readerIndex);
            throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
        }
    }

    private static String readDelimiterStandard(ByteBuf undecodedChunk, String delimiter) {
        int readerIndex = undecodedChunk.readerIndex();

        try {
            StringBuilder e = new StringBuilder(64);
            int delimiterPos = 0;
            int len = delimiter.length();

            byte nextByte;
            while (undecodedChunk.isReadable() && delimiterPos < len) {
                nextByte = undecodedChunk.readByte();
                if (nextByte != delimiter.charAt(delimiterPos)) {
                    undecodedChunk.readerIndex(readerIndex);
                    throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
                }

                ++delimiterPos;
                e.append((char) nextByte);
            }

            if (undecodedChunk.isReadable()) {
                nextByte = undecodedChunk.readByte();
                if (nextByte == 13) {
                    nextByte = undecodedChunk.readByte();
                    if (nextByte == 10) {
                        return e.toString();
                    }

                    undecodedChunk.readerIndex(readerIndex);
                    throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
                }

                if (nextByte == 10) {
                    return e.toString();
                }

                if (nextByte == 45) {
                    e.append('-');
                    nextByte = undecodedChunk.readByte();
                    if (nextByte == 45) {
                        e.append('-');
                        if (undecodedChunk.isReadable()) {
                            nextByte = undecodedChunk.readByte();
                            if (nextByte == 13) {
                                nextByte = undecodedChunk.readByte();
                                if (nextByte == 10) {
                                    return e.toString();
                                }

                                undecodedChunk.readerIndex(readerIndex);
                                throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
                            }

                            if (nextByte == 10) {
                                return e.toString();
                            }

                            undecodedChunk.readerIndex(undecodedChunk.readerIndex() - 1);
                            return e.toString();
                        }

                        return e.toString();
                    }
                }
            }
        } catch (IndexOutOfBoundsException var7) {
            undecodedChunk.readerIndex(readerIndex);
            throw new HttpPostRequestDecoder.NotEnoughDataDecoderException(var7);
        }

        undecodedChunk.readerIndex(readerIndex);
        throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
    }

    private static String readDelimiter(ByteBuf undecodedChunk, String delimiter) {
        if (!undecodedChunk.hasArray()) {
            return readDelimiterStandard(undecodedChunk, delimiter);
        } else {
            PostBodyUtil.SeekAheadOptimize sao = new PostBodyUtil.SeekAheadOptimize(undecodedChunk);
            int readerIndex = undecodedChunk.readerIndex();
            int delimiterPos = 0;
            int len = delimiter.length();

            try {
                StringBuilder e = new StringBuilder(64);

                while (true) {
                    byte nextByte;
                    if (sao.pos >= sao.limit || delimiterPos >= len) {
                        if (sao.pos >= sao.limit) {
                            break;
                        }

                        nextByte = sao.bytes[sao.pos++];
                        if (nextByte == 13) {
                            if (sao.pos < sao.limit) {
                                nextByte = sao.bytes[sao.pos++];
                                if (nextByte == 10) {
                                    sao.setReadPosition(0);
                                    return e.toString();
                                }

                                undecodedChunk.readerIndex(readerIndex);
                                throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
                            }

                            undecodedChunk.readerIndex(readerIndex);
                            throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
                        }

                        if (nextByte == 10) {
                            sao.setReadPosition(0);
                            return e.toString();
                        }

                        if (nextByte == 45) {
                            e.append('-');
                            if (sao.pos < sao.limit) {
                                nextByte = sao.bytes[sao.pos++];
                                if (nextByte == 45) {
                                    e.append('-');
                                    if (sao.pos < sao.limit) {
                                        nextByte = sao.bytes[sao.pos++];
                                        if (nextByte == 13) {
                                            if (sao.pos < sao.limit) {
                                                nextByte = sao.bytes[sao.pos++];
                                                if (nextByte == 10) {
                                                    sao.setReadPosition(0);
                                                    return e.toString();
                                                }

                                                undecodedChunk.readerIndex(readerIndex);
                                                throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
                                            }

                                            undecodedChunk.readerIndex(readerIndex);
                                            throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
                                        }

                                        if (nextByte == 10) {
                                            sao.setReadPosition(0);
                                            return e.toString();
                                        }

                                        sao.setReadPosition(1);
                                        return e.toString();
                                    }

                                    sao.setReadPosition(0);
                                    return e.toString();
                                }
                            }
                        }
                        break;
                    }

                    nextByte = sao.bytes[sao.pos++];
                    if (nextByte != delimiter.charAt(delimiterPos)) {
                        undecodedChunk.readerIndex(readerIndex);
                        throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
                    }

                    ++delimiterPos;
                    e.append((char) nextByte);
                }
            } catch (IndexOutOfBoundsException var8) {
                undecodedChunk.readerIndex(readerIndex);
                throw new HttpPostRequestDecoder.NotEnoughDataDecoderException(var8);
            }

            undecodedChunk.readerIndex(readerIndex);
            throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
        }
    }

    private static boolean loadDataMultipartStandard(ByteBuf undecodedChunk, String delimiter, HttpData httpData) {
        int startReaderIndex = undecodedChunk.readerIndex();
        int delimeterLength = delimiter.length();
        int index = 0;
        int lastPosition = startReaderIndex;
        byte prevByte = 10;
        boolean delimiterFound = false;

        while (undecodedChunk.isReadable()) {
            byte content = undecodedChunk.readByte();
            if (prevByte == 10 && content == delimiter.codePointAt(index)) {
                ++index;
                if (delimeterLength == index) {
                    delimiterFound = true;
                    break;
                }
            } else {
                lastPosition = undecodedChunk.readerIndex();
                if (content == 10) {
                    index = 0;
                    lastPosition -= prevByte == 13 ? 2 : 1;
                }

                prevByte = content;
            }
        }

        if (prevByte == 13) {
            --lastPosition;
        }

        ByteBuf var12 = undecodedChunk.copy(startReaderIndex, lastPosition - startReaderIndex);

        try {
            httpData.addContent(var12, delimiterFound);
        } catch (IOException var11) {
            throw new HttpPostRequestDecoder.ErrorDataDecoderException(var11);
        }

        undecodedChunk.readerIndex(lastPosition);
        return delimiterFound;
    }

    private static boolean loadDataMultipart(ByteBuf undecodedChunk, String delimiter, HttpData httpData) {
        if (!undecodedChunk.hasArray()) {
            return loadDataMultipartStandard(undecodedChunk, delimiter, httpData);
        } else {
            PostBodyUtil.SeekAheadOptimize sao = new PostBodyUtil.SeekAheadOptimize(undecodedChunk);
            int startReaderIndex = undecodedChunk.readerIndex();
            int delimeterLength = delimiter.length();
            int index = 0;
            int lastRealPos = sao.pos;
            byte prevByte = 10;
            boolean delimiterFound = false;

            while (sao.pos < sao.limit) {
                byte lastPosition = sao.bytes[sao.pos++];
                if (prevByte == 10 && lastPosition == delimiter.codePointAt(index)) {
                    ++index;
                    if (delimeterLength == index) {
                        delimiterFound = true;
                        break;
                    }
                } else {
                    lastRealPos = sao.pos;
                    if (lastPosition == 10) {
                        index = 0;
                        lastRealPos -= prevByte == 13 ? 2 : 1;
                    }

                    prevByte = lastPosition;
                }
            }

            if (prevByte == 13) {
                --lastRealPos;
            }

            int var14 = sao.getReadPosition(lastRealPos);
            ByteBuf content = undecodedChunk.copy(startReaderIndex, var14 - startReaderIndex);

            try {
                httpData.addContent(content, delimiterFound);
            } catch (IOException var13) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException(var13);
            }

            undecodedChunk.readerIndex(var14);
            return delimiterFound;
        }
    }

    private static String cleanString(String field) {
        int size = field.length();
        StringBuilder sb = new StringBuilder(size);

        for (int i = 0; i < size; ++i) {
            char nextChar = field.charAt(i);
            switch (nextChar) {
                case '\t':
                case ',':
                case ':':
                case ';':
                case '=':
                    sb.append(' ');
                case '\"':
                    break;
                default:
                    sb.append(nextChar);
            }
        }

        return sb.toString().trim();
    }

    private boolean skipOneLine() {
        if (!this.undecodedChunk.isReadable()) {
            return false;
        } else {
            byte nextByte = this.undecodedChunk.readByte();
            if (nextByte == 13) {
                if (!this.undecodedChunk.isReadable()) {
                    this.undecodedChunk.readerIndex(this.undecodedChunk.readerIndex() - 1);
                    return false;
                } else {
                    nextByte = this.undecodedChunk.readByte();
                    if (nextByte == 10) {
                        return true;
                    } else {
                        this.undecodedChunk.readerIndex(this.undecodedChunk.readerIndex() - 2);
                        return false;
                    }
                }
            } else if (nextByte == 10) {
                return true;
            } else {
                this.undecodedChunk.readerIndex(this.undecodedChunk.readerIndex() - 1);
                return false;
            }
        }
    }

    private static String[] splitMultipartHeader(String sb) {
        ArrayList headers = new ArrayList(1);
        int nameStart = PostBodyUtil.findNonWhitespace(sb, 0);

        int nameEnd;
        for (nameEnd = nameStart; nameEnd < sb.length(); ++nameEnd) {
            char svalue = sb.charAt(nameEnd);
            if (svalue == 58 || Character.isWhitespace(svalue)) {
                break;
            }
        }

        int colonEnd;
        for (colonEnd = nameEnd; colonEnd < sb.length(); ++colonEnd) {
            if (sb.charAt(colonEnd) == 58) {
                ++colonEnd;
                break;
            }
        }

        int valueStart = PostBodyUtil.findNonWhitespace(sb, colonEnd);
        int valueEnd = PostBodyUtil.findEndOfString(sb);
        headers.add(sb.substring(nameStart, nameEnd));
        String var13 = sb.substring(valueStart, valueEnd);
        String[] values;
        if (var13.indexOf(59) >= 0) {
            values = splitMultipartHeaderValues(var13);
        } else {
            values = var13.split(",");
        }

        String[] array = values;
        int i = values.length;

        for (int var11 = 0; var11 < i; ++var11) {
            String value = array[var11];
            headers.add(value.trim());
        }

        array = new String[headers.size()];

        for (i = 0; i < headers.size(); ++i) {
            array[i] = (String) headers.get(i);
        }

        return array;
    }

    private static String[] splitMultipartHeaderValues(String svalue) {
        ArrayList values = InternalThreadLocalMap.get().arrayList(1);
        boolean inQuote = false;
        boolean escapeNext = false;
        int start = 0;

        for (int i = 0; i < svalue.length(); ++i) {
            char c = svalue.charAt(i);
            if (inQuote) {
                if (escapeNext) {
                    escapeNext = false;
                } else if (c == 92) {
                    escapeNext = true;
                } else if (c == 34) {
                    inQuote = false;
                }
            } else if (c == 34) {
                inQuote = true;
            } else if (c == 59) {
                values.add(svalue.substring(start, i));
                start = i + 1;
            }
        }

        values.add(svalue.substring(start));
        return (String[]) values.toArray(new String[values.size()]);
    }

    static {
        FILENAME_ENCODED = HttpHeaderValues.FILENAME.toString() + '*';
    }

}
