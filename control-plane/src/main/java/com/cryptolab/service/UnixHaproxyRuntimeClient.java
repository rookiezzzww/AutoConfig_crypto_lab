package com.cryptolab.service;

import com.cryptolab.config.LabProperties;
import com.cryptolab.exception.ProxySwitchException;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * UnixHaproxyRuntimeClient 是 HaproxyRuntimeClient 接口的实现类，
 * 用于通过 Unix 域套接字与 HAProxy 运行时进行交互。它提供了 execute 方法，
 * 用于执行指定的命令并返回结果。
 */
/** 使用 Java 17 Unix Domain Socket API 向 HAProxy Runtime Socket 收发命令。 */
@Component
public class UnixHaproxyRuntimeClient implements HaproxyRuntimeClient {
    private final LabProperties properties;

    /** 注入 Runtime Socket 路径配置。 */
    public UnixHaproxyRuntimeClient(LabProperties properties) {
        this.properties = properties;
    }

    /** 写入命令、关闭输出流并读取 HAProxy 的完整返回内容。 */
    @Override
    public String execute(String command) {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(properties.getHaproxySocket()));
            channel.write(StandardCharsets.UTF_8.encode(command.endsWith("\n") ? command : command + "\n"));
            channel.shutdownOutput();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (channel.read(buffer) > 0) {
                buffer.flip();
                output.write(buffer.array(), 0, buffer.remaining());
                buffer.clear();
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ProxySwitchException("HAProxy Runtime API unavailable: " + e.getMessage(), e);
        }
    }
}
