package com.maxzxwd.msb;

import java.nio.ByteBuffer;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

public class MessagingAdapter extends WebSocketAdapter {
  private MinecraftClient minecraftClient;
  private long lastReceived = System.currentTimeMillis();

  @Override
  public void onWebSocketConnect(Session session) {
    if (WebSocketServer.IP_BANS.contains(session.getRemoteAddress().getAddress().getHostAddress())) {
      session.close();
      return;
    }

    super.onWebSocketConnect(session);

    this.minecraftClient = new MinecraftClient(WebSocketServer.SETTINGS.minecraftServerAddress,
        WebSocketServer.SETTINGS.minecraftServerPort);
    minecraftClient.closeConnectionHandler = () -> {
      if (getSession() != null) {
        getSession().close();
      }
    };
    minecraftClient.receiveDataHandler = (data, len) -> {
      if (getSession() != null && getSession().isOpen()) {
        if (System.currentTimeMillis() - lastReceived > 5000) {
          getSession().close();
        } else {
          getSession().getRemote().sendBytesByFuture(ByteBuffer.wrap(data, 0, len));
        }
      }
    };
    new Thread(minecraftClient).start();
  }

  @Override
  public void onWebSocketClose(int statusCode, String reason) {
    minecraftClient.close();

    super.onWebSocketClose(statusCode, reason);
  }

  @Override
  public void onWebSocketBinary(byte[] payload, int offset, int len) {
    if (payload.length > 0) {
      minecraftClient.sendData(payload);
    }
    lastReceived = System.currentTimeMillis();
  }
}