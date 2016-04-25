/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.leshan.benchmarks;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.eclipse.leshan.LwM2mId;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.object.Device;
import org.eclipse.leshan.client.object.Security;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.server.californium.LeshanServerBuilder;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.eclipse.leshan.server.impl.SecurityRegistryImpl;
import org.eclipse.leshan.server.security.NonUniqueSecurityInfoException;
import org.eclipse.leshan.server.security.SecurityInfo;
import org.eclipse.leshan.util.Charsets;
import org.eclipse.leshan.util.Hex;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

public class SecurityBenchmark {
    static {
        LogManager.getLogManager().reset();
        Logger globalLogger = Logger.getGlobal();
        globalLogger.setLevel(java.util.logging.Level.OFF);
        Handler[] handlers = globalLogger.getHandlers();
        for (Handler handler : handlers) {
            globalLogger.removeHandler(handler);
        }
    }

    public static final byte[] pskKey = Hex.decodeHex("73656372657450534b".toCharArray());
    private static int NON_SECURE_PORT = 9999;
    private static int SECURE_PORT = 9998;

    @State(Scope.Benchmark)
    public static class ServerState {
        LeshanServer server;

        @Setup(Level.Trial)
        public void doSetup() throws NonUniqueSecurityInfoException {
            LeshanServerBuilder builder = new LeshanServerBuilder();
            builder.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), NON_SECURE_PORT));
            builder.setLocalSecureAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), SECURE_PORT));
            builder.setSecurityRegistry(new SecurityRegistryImpl() {
                // TODO we should separate SecurityRegistryImpl in 2 registries :
                // InMemorySecurityRegistry and PersistentSecurityRegistry

                @Override
                protected void loadFromFile() {
                    // do not load From File
                }

                @Override
                protected void saveToFile() {
                    // do not save to file
                }
            });
            server = builder.build();
        }

        @TearDown(Level.Trial)
        public void doTearDown() {
            server.destroy();
        }
    }

    @State(Scope.Thread)
    public static class ClientState {

        static final String MODEL_NUMBER = "IT-TEST-123";
        static final long LIFETIME = 2;
        LeshanClient client;

        @Setup(Level.Invocation)
        public void doSetup(ServerState s) throws NonUniqueSecurityInfoException {
            // generate random endpoint and psk identity
            String endpoint = UUID.randomUUID().toString();
            String pskIdentity = UUID.randomUUID().toString();

            ObjectsInitializer initializer = new ObjectsInitializer();
            initializer.setInstancesForObject(LwM2mId.SECURITY, Security.psk("coaps://localhost:" + SECURE_PORT, 12345,
                    pskIdentity.getBytes(Charsets.UTF_8), pskKey));
            initializer.setInstancesForObject(LwM2mId.SERVER, new Server(12345, LIFETIME, BindingMode.U, false));
            initializer.setInstancesForObject(LwM2mId.DEVICE, new Device("Eclipse Leshan", MODEL_NUMBER, "12345", "U"));
            List<LwM2mObjectEnabler> objects = initializer.createMandatory();
            objects.add(initializer.create(2));

            InetSocketAddress clientAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            LeshanClientBuilder builder = new LeshanClientBuilder(endpoint);
            builder.setLocalAddress(clientAddress.getHostString(), clientAddress.getPort());
            builder.setObjects(objects);
            client = builder.build();

            s.server.getSecurityRegistry().add(SecurityInfo.newPreSharedKeyInfo(endpoint, pskIdentity, pskKey));
        }

        @TearDown(Level.Invocation)
        public void doTearDown() {
            client.destroy(false);
        }
    }

    @Benchmark
    @Threads(1)
    @Warmup(iterations = 5, batchSize = 10)
    @Measurement(iterations = 5, batchSize = 10)
    public void register_deregister_1_thread(ClientState s) {
        s.client.start();
    }

    @Benchmark
    @Threads(5)
    @Warmup(iterations = 5, batchSize = 10)
    @Measurement(iterations = 5, batchSize = 10)
    public void register_deregister_5_thread(ClientState s) {
        s.client.start();
    }
}