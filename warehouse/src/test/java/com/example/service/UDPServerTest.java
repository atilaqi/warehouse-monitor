package com.example.service;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class UDPServerTest {
    private UDPServer udpServer;
    private int port = 9999;

    @BeforeEach
    public void setUp() {
        udpServer = new UDPServer(port);
        udpServer.start();
    }

    @AfterEach
    public void tearDown() {
        udpServer.shutdown();
    }

    @Test
    public void testUDPMessageReception() throws InterruptedException {
        Flux<String> messageFlux = udpServer.getMessageFlux();

        // Send a test message via UDP
        String testMessage = "Hello, UDP!";
        sendUDPMessage("localhost", port, testMessage);

        // Verify the message is received correctly
        StepVerifier.create(messageFlux)
                .expectNext(testMessage)
                .thenCancel()//cancel the infinite stream after receiving testMessage.
                .verify();
    }

    private void sendUDPMessage(String host, int port, String message) throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            // No need to add handlers since we're just sending messages
                        }
                    });

            Channel channel = bootstrap.bind(0).sync().channel();
            DatagramPacket packet = new DatagramPacket(
                    Unpooled.copiedBuffer(message, StandardCharsets.UTF_8),
                    new InetSocketAddress(host, port)
            );
            channel.writeAndFlush(packet).sync();
            channel.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}

