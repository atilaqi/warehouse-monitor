package com.example.service;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public class UDPServer {
    private final int port;
    private final EventLoopGroup group;
    private final Sinks.Many<String> messageSink;

    public UDPServer(int port) {
        this.port = port;
        this.group = new NioEventLoopGroup();
        this.messageSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    public Flux<String> getMessageFlux() {
        return messageSink.asFlux();
    }

    public void start() {
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {

                                String reading = msg.content().toString(java.nio.charset.StandardCharsets.UTF_8);
                                System.out.println("reading " + reading);
                                messageSink.tryEmitNext(reading);
                            }
                        });
                    }
                });

        b.bind(port);
    }

    public void shutdown() {
        group.shutdownGracefully();
    }
}
