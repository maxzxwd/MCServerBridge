package com.maxzxwd.msb;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class MinecraftClient implements Runnable {
  private final Object lock = new Object();
  private final String ip;
  private final int port;

  private boolean connected = true;
  private OutputStream out = null;

  public ReceiveDataHandler receiveDataHandler = null;
  public CloseConnectionHandler closeConnectionHandler = null;

  public MinecraftClient(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  public void sendData(byte[] data) {
    synchronized (lock) {
      while (connected && out == null) {
        try {
          lock.wait();
        } catch (Exception ignored) {
          return;
        }
      }
    }
    if (connected) {
      try {
        out.write(data, 0, data.length);
      } catch (IOException e) {
        e.printStackTrace();
        closeAndNotify();
      }
    }
  }

  @Override
  public void run() {
    try (Socket socket = new Socket(ip, port)) {
      synchronized (lock) {
        out = socket.getOutputStream();
        lock.notifyAll();
      }

      byte[] buffer = new byte[WebSocketServer.SETTINGS.perConnectionBufferSize];
      int readBytes;
      while (connected) {
        readBytes = socket.getInputStream().read(buffer);

        if (readBytes == -1) {
          continue;
        }
        if (readBytes > 0) {
          if (receiveDataHandler != null) {
            receiveDataHandler.onReceiveData(buffer, readBytes);
          }
        }

      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    closeAndNotify();
  }

  private void closeAndNotify() {
    close();

    if (closeConnectionHandler != null) {
      closeConnectionHandler.onCloseConnection();
      closeConnectionHandler = null;
    }
  }

  public void close() {
    synchronized (lock) {
      receiveDataHandler = null;
      connected = false;
      lock.notifyAll();
    }
  }

  public interface ReceiveDataHandler {
    void onReceiveData(byte[] data, int len);
  }

  public interface CloseConnectionHandler {
    void onCloseConnection();
  }
}