/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.appender.net;

import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.appender.SocketAppender;
import org.apache.logging.log4j.core.layout.JsonLayout;
import org.apache.logging.log4j.core.net.SslSocketManager;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.net.ssl.SslConfigurationTest;
import org.apache.logging.log4j.core.net.ssl.StoreConfigurationException;
import org.apache.logging.log4j.server.SecureTcpSocketServer;
import org.apache.logging.log4j.test.AvailablePortFinder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that a Secure Socket Appender starts when there is no broker and connect the broker when it is started later.
 * <p>
 * LOG4J2-1311 SocketAppender will lost first several logs after re-connection to log servers.
 * </p>
 * <p>
 * See also LOG4J2-1934 JMS Appender does not know how to recover from a broken connection. See
 * https://issues.apache.org/jira/browse/LOG4J2-1934
 * </p>
 * <p>
 * This test class' single test method performs the following:
 * </p>
 * <ol>
 * <li>Starts a SocketAppender (no reconnect thread is running)</li>
 * <li>Logs an event (fails and the manager starts its reconnect thread)</li>
 * <li>Starts Apache Socket Server</li>
 * <li>Logs an event successfully</li>
 * </ol>
 */
public class SecureSocketAppenderConnectPostStartupIT extends AbstractSocketAppenderReconnectIT {

    private SslConfiguration sslConfiguration;

    @Before
    public void initServerSocketFactory() throws StoreConfigurationException {
        sslConfiguration = SslConfigurationTest.createTestSslConfigurationResources();
    }

    @Test
    public void testConnectPostStartup() throws Exception {
        //
        // Start appender
        final int port = AvailablePortFinder.getNextAvailable();
        // Start appender, fails to connect and starts reconnect thread.
        // @formatter:off
        appender = SocketAppender.newBuilder()
                .withPort(port)
                .withReconnectDelayMillis(1000)
                .withName("test")
                .withLayout(JsonLayout.newBuilder().build())
                .withSslConfiguration(sslConfiguration)
                .build();
        // @formatter:on
        appender.start();
        //
        // Logging will fail but the socket manager is still running its reconnect thread, waiting for the server.
        try {
            appendEvent(appender);
            Assert.fail("Expected to catch a " + AppenderLoggingException.class.getName());
        } catch (final AppenderLoggingException e) {
            // Expected.
        }
        //
        // Start server
        server = SecureTcpSocketServer.createJsonServer(port, sslConfiguration);
        // Wait to allow the reconnect thread to connect
        startServer(((SslSocketManager) appender.getManager()).getReconnectionDelayMillis() * 2);
        //
        // Logging now succeeds.
        appendEvent(appender);
    }
}
