package org.wso2.transport.http.netty.util.client.http2.nettyclient;

import io.netty.handler.codec.http.FullHttpResponse;

public class Http2CompleteResponse {

    private FullHttpResponse continueResponse;
    private FullHttpResponse finalResponse;
    private String finalResponsePayload;

    public FullHttpResponse getContinueResponse() {
        return continueResponse;
    }

    public void setContinueResponse(FullHttpResponse continueResponse) {
        this.continueResponse = continueResponse;
    }

    public FullHttpResponse getFinalResponse() {
        return finalResponse;
    }

    public void setFinalResponse(FullHttpResponse finalResponse) {
        this.finalResponse = finalResponse;
    }

    public String getFinalResponsePayload() {
        return finalResponsePayload;
    }

    public void setFinalResponsePayload(String finalResponsePayload) {
        this.finalResponsePayload = finalResponsePayload;
    }
}
