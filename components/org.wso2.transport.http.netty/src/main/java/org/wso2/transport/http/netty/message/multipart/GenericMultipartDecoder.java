package org.wso2.transport.http.netty.message.multipart;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpPostRequestDecoder;
import io.netty.util.internal.StringUtil;

import java.nio.charset.Charset;
import java.util.List;

public class GenericMultipartDecoder implements InterfaceHttpPostRequestDecoder {

    static final int DEFAULT_DISCARD_THRESHOLD = 10485760;
    private final InterfaceHttpPostRequestDecoder decoder;

    public GenericMultipartDecoder(HttpRequest request) {
        this(new DefaultHttpDataFactory(16384L), request, HttpConstants.DEFAULT_CHARSET);
    }

    public GenericMultipartDecoder(HttpDataFactory factory, HttpRequest request) {
        this(factory, request, HttpConstants.DEFAULT_CHARSET);
    }

    public GenericMultipartDecoder(HttpDataFactory factory, HttpRequest request, Charset charset) {
        if (factory == null) {
            throw new NullPointerException("factory");
        } else if (request == null) {
            throw new NullPointerException("request");
        } else if (charset == null) {
            throw new NullPointerException("charset");
        } else {
            if (isMultipart(request)) {
                this.decoder = new MultipartDissector(factory, request, charset);
            } else {
                this.decoder = null;
            }
        }
    }

    public static boolean isMultipart(HttpRequest request) {
        return request.headers().contains(HttpHeaderNames.CONTENT_TYPE) ? getMultipartDataBoundary(request.headers().get(HttpHeaderNames.CONTENT_TYPE)) != null : false;
    }

    protected static String[] getMultipartDataBoundary(String contentType) {
        String[] headerContentType = splitHeaderContentType(contentType);
        String multiPartHeader = headerContentType[0];
        if (headerContentType[0].regionMatches(true, 0, multiPartHeader, 0, multiPartHeader.length())) {
            String boundaryHeader = HttpHeaderValues.BOUNDARY.toString();
            byte mrank;
            byte crank;
            if (headerContentType[1].regionMatches(true, 0, boundaryHeader, 0, boundaryHeader.length())) {
                mrank = 1;
                crank = 2;
            } else {
                if (!headerContentType[2].regionMatches(true, 0, boundaryHeader, 0, boundaryHeader.length())) {
                    return null;
                }

                mrank = 2;
                crank = 1;
            }

            String boundary = StringUtil.substringAfter(headerContentType[mrank], '=');
            if (boundary == null) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException("Needs a boundary value");
            } else {
                String charsetHeader;
                if (boundary.charAt(0) == 34) {
                    charsetHeader = boundary.trim();
                    int charset = charsetHeader.length() - 1;
                    if (charsetHeader.charAt(charset) == 34) {
                        boundary = charsetHeader.substring(1, charset);
                    }
                }

                charsetHeader = HttpHeaderValues.CHARSET.toString();
                if (headerContentType[crank].regionMatches(true, 0, charsetHeader, 0, charsetHeader.length())) {
                    String charset1 = StringUtil.substringAfter(headerContentType[crank], '=');
                    if (charset1 != null) {
                        return new String[]{"--" + boundary, charset1};
                    }
                }

                return new String[]{"--" + boundary};
            }
        } else {
            return null;
        }
    }

    public boolean isMultipart() {
        return this.decoder.isMultipart();
    }

    public void setDiscardThreshold(int discardThreshold) {
        this.decoder.setDiscardThreshold(discardThreshold);
    }

    public int getDiscardThreshold() {
        return this.decoder.getDiscardThreshold();
    }

    public List<InterfaceHttpData> getBodyHttpDatas() {
        return this.decoder.getBodyHttpDatas();
    }

    public List<InterfaceHttpData> getBodyHttpDatas(String name) {
        return this.decoder.getBodyHttpDatas(name);
    }

    public InterfaceHttpData getBodyHttpData(String name) {
        return this.decoder.getBodyHttpData(name);
    }

    public InterfaceHttpPostRequestDecoder offer(HttpContent content) {
        return this.decoder.offer(content);
    }

    public boolean hasNext() {
        return this.decoder.hasNext();
    }

    public InterfaceHttpData next() {
        return this.decoder.next();
    }

    public InterfaceHttpData currentPartialHttpData() {
        return this.decoder.currentPartialHttpData();
    }

    public void destroy() {
        this.decoder.destroy();
    }

    public void cleanFiles() {
        this.decoder.cleanFiles();
    }

    public void removeHttpDataFromClean(InterfaceHttpData data) {
        this.decoder.removeHttpDataFromClean(data);
    }

    private static String[] splitHeaderContentType(String sb) {
        int aStart = PostBodyUtil.findNonWhitespace(sb, 0);
        int aEnd = sb.indexOf(59);
        if (aEnd == -1) {
            return new String[]{sb, "", ""};
        } else {
            int bStart = PostBodyUtil.findNonWhitespace(sb, aEnd + 1);
            if (sb.charAt(aEnd - 1) == 32) {
                --aEnd;
            }

            int bEnd = sb.indexOf(59, bStart);
            if (bEnd == -1) {
                bEnd = PostBodyUtil.findEndOfString(sb);
                return new String[]{sb.substring(aStart, aEnd), sb.substring(bStart, bEnd), ""};
            } else {
                int cStart = PostBodyUtil.findNonWhitespace(sb, bEnd + 1);
                if (sb.charAt(bEnd - 1) == 32) {
                    --bEnd;
                }

                int cEnd = PostBodyUtil.findEndOfString(sb);
                return new String[]{sb.substring(aStart, aEnd), sb.substring(bStart, bEnd), sb.substring(cStart, cEnd)};
            }
        }
    }

    public static class ErrorDataDecoderException extends DecoderException {
        private static final long serialVersionUID = 5020247425493164465L;

        public ErrorDataDecoderException() {
        }

        public ErrorDataDecoderException(String msg) {
            super(msg);
        }

        public ErrorDataDecoderException(Throwable cause) {
            super(cause);
        }

        public ErrorDataDecoderException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    public static class EndOfDataDecoderException extends DecoderException {
        private static final long serialVersionUID = 1336267941020800769L;

        public EndOfDataDecoderException() {
        }
    }

    public static class NotEnoughDataDecoderException extends DecoderException {
        private static final long serialVersionUID = -7846841864603865638L;

        public NotEnoughDataDecoderException() {
        }

        public NotEnoughDataDecoderException(String msg) {
            super(msg);
        }

        public NotEnoughDataDecoderException(Throwable cause) {
            super(cause);
        }

        public NotEnoughDataDecoderException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    protected static enum MultiPartStatus {
        NOTSTARTED,
        PREAMBLE,
        HEADERDELIMITER,
        DISPOSITION,
        FIELD,
        FILEUPLOAD,
        MIXEDPREAMBLE,
        MIXEDDELIMITER,
        MIXEDDISPOSITION,
        MIXEDFILEUPLOAD,
        MIXEDCLOSEDELIMITER,
        CLOSEDELIMITER,
        PREEPILOGUE,
        EPILOGUE;

        private MultiPartStatus() {
        }
    }
}
