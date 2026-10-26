package com.forA.chatbot.chat.fault;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

class ChatFaultProbe {
    final AtomicReference<String> mode = new AtomicReference<>("NONE");
    volatile CountDownLatch entered = new CountDownLatch(1);
    volatile CountDownLatch release = new CountDownLatch(1);

    DataSource wrap(DataSource source) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
            (proxy, method, args) -> {
                try {
                    Object result = method.invoke(source, args);
                    return result instanceof Connection connection ? wrap(connection) : result;
                } catch (InvocationTargetException failure) { throw failure.getCause(); }
            });
    }

    private Connection wrap(Connection connection) {
        boolean[] profileWritten = {false};
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
                if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                        && sql.toLowerCase(Locale.ROOT).matches("(?s).*\\bupdate\\s+users\\s+set\\b.*")) profileWritten[0] = true;
                if (method.getName().equals("commit") && profileWritten[0]) {
                    profileWritten[0] = false;
                    String fault = mode.getAndSet("NONE");
                    if (fault.equals("HALT_BEFORE_SQL")) Runtime.getRuntime().halt(71);
                    if (fault.equals("ROLLBACK")) {
                        connection.rollback();
                        throw new SQLException("Injected rollback before commit", "08007");
                    }
                    if (fault.equals("PAUSE")) {
                        entered.countDown();
                        if (!release.await(15, TimeUnit.SECONDS)) throw new SQLException("Pause deadline exceeded");
                    }
                    connection.commit();
                    if (fault.equals("HALT_AFTER_SQL")) Runtime.getRuntime().halt(72);
                    if (fault.equals("ACK_LOST")) throw new SQLException("Injected acknowledgement loss AFTER real commit", "08007");
                    return null;
                }
                if (method.getName().equals("rollback")) profileWritten[0] = false;
                try { return method.invoke(connection, args); }
                catch (InvocationTargetException failure) { throw failure.getCause(); }
            });
    }
}
