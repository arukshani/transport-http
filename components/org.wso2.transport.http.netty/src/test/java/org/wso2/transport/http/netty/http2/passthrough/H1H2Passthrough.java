/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.transport.http.netty.http2.passthrough;

import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.transport.http.netty.contract.Constants;
import org.wso2.transport.http.netty.contract.HttpClientConnector;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;
import org.wso2.transport.http.netty.contract.config.ListenerConfiguration;
import org.wso2.transport.http.netty.contract.config.SenderConfiguration;
import org.wso2.transport.http.netty.contract.config.ServerBootstrapConfiguration;
import org.wso2.transport.http.netty.contract.config.TransportsConfiguration;
import org.wso2.transport.http.netty.contract.exceptions.ServerConnectorException;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.http2.listeners.Http2EchoListener;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;
import org.wso2.transport.http.netty.message.HttpConnectorUtil;
import org.wso2.transport.http.netty.message.HttpMessageDataStreamer;
import org.wso2.transport.http.netty.passthrough.PassthroughMessageProcessorListener;
import org.wso2.transport.http.netty.util.TestUtil;
import org.wso2.transport.http.netty.util.client.http2.MessageGenerator;
import org.wso2.transport.http.netty.util.client.http2.MessageSender;

import java.util.HashMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.wso2.transport.http.netty.util.Http2Util.getTestHttp2Client;
import static org.wso2.transport.http.netty.util.TestUtil.SERVER_CONNECTOR_PORT;

/**
 * Test case for HTTP/1-HTTP/2 passthrough scenario.
 */
public class H1H2Passthrough {
    private static final Logger LOG = LoggerFactory.getLogger(H2H2Passthrough.class);

    private HttpWsConnectorFactory httpWsConnectorFactory;
    private ServerConnector serverConnector, backEndServer;
    private static final String MSG_PAYLOAD = "HTTP/1-HTTP/2";

    @BeforeClass
    public void setup() {

        httpWsConnectorFactory = new DefaultHttpWsConnectorFactory(1, 2, 2);
        ListenerConfiguration listenerConfiguration = getListenerConfiguration(SERVER_CONNECTOR_PORT,
                                                                               Constants.HTTP_1_1_VERSION);
        serverConnector = httpWsConnectorFactory
                .createServerConnector(new ServerBootstrapConfiguration(new HashMap<>()), listenerConfiguration);
        ServerConnectorFuture serverConnectorFuture = serverConnector.start();
        TransportsConfiguration transportsConfiguration = new TransportsConfiguration();
        SenderConfiguration h2cSenderConfiguration = HttpConnectorUtil.getSenderConfiguration(transportsConfiguration,
                                                                                              Constants.HTTP_SCHEME);
        h2cSenderConfiguration.setForceHttp2(true);
        h2cSenderConfiguration.setHttpVersion(Constants.HTTP_2_0);
        serverConnectorFuture.setHttpConnectorListener(
                new PassthroughMessageProcessorListener(h2cSenderConfiguration, true));
        try {
            serverConnectorFuture.sync();
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while waiting for server connector to start");
        }

        startBackEndServer();
    }

    private void startBackEndServer() {
        ListenerConfiguration listenerConfiguration = getListenerConfiguration(TestUtil.HTTP_SERVER_PORT,
                                                                               Constants.HTTP_2_0);
        backEndServer = httpWsConnectorFactory.createServerConnector(new ServerBootstrapConfiguration(new HashMap<>()),
                                                                     listenerConfiguration);
        ServerConnectorFuture serverConnectorFuture = backEndServer.start();
        serverConnectorFuture.setHttpConnectorListener(new Http2EchoListener());
        try {
            serverConnectorFuture.sync();
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while waiting for server connector to start");
        }
    }

    private ListenerConfiguration getListenerConfiguration(int serverConnectorPort, String httpVersion) {
        ListenerConfiguration listenerConfiguration = new ListenerConfiguration();
        listenerConfiguration.setPort(serverConnectorPort);
        listenerConfiguration.setScheme(Constants.HTTP_SCHEME);
        listenerConfiguration.setVersion(httpVersion);
        return listenerConfiguration;
    }

    @Test
    public void testH1H2Passthrough() {
        HttpClientConnector client = getTestHttp2Client(httpWsConnectorFactory, false);
        String response = getResponse(client);
        assertEquals(response, MSG_PAYLOAD);
    }

    private String getResponse(HttpClientConnector h1FallBackClient) {
        HttpCarbonMessage httpCarbonMessage = MessageGenerator.generateRequest(HttpMethod.POST, MSG_PAYLOAD,
                                                                               SERVER_CONNECTOR_PORT, "http://");
        HttpCarbonMessage response = new MessageSender(h1FallBackClient).sendMessage(httpCarbonMessage);
        assertNotNull(response);
        return TestUtil.getStringFromInputStream(new HttpMessageDataStreamer(response).getInputStream());
    }

    @AfterClass
    public void cleanUp() throws ServerConnectorException {
        try {
            serverConnector.stop();
            backEndServer.stop();
            httpWsConnectorFactory.shutdown();
        } catch (Exception e) {
            LOG.warn("Resource clean up is interrupted", e);
        }
    }
}
