/*
 * Copyright 2020 MariaDB Ab.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mariadb.r2dbc.message.server;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.mariadb.r2dbc.util.Assert;
import reactor.util.Logger;
import reactor.util.Loggers;

public final class ErrorPacket implements ServerMessage {
  private static final Logger logger = Loggers.getLogger(ErrorPacket.class);
  private final short errorCode;
  private final String message;
  private final String sqlState;
  private Sequencer sequencer;

  private ErrorPacket(Sequencer sequencer, short errorCode, String sqlState, String message) {
    this.sequencer = sequencer;
    this.errorCode = errorCode;
    this.message = message;
    this.sqlState = sqlState;
  }

  public static ErrorPacket decode(Sequencer sequencer, ByteBuf buf) {
    Assert.requireNonNull(buf, "buffer must not be null");
    buf.skipBytes(1);
    short errorCode = buf.readShortLE();
    byte next = buf.getByte(buf.readerIndex());
    String sqlState;
    String msg;
    if (next == (byte) '#') {
      buf.skipBytes(1); // skip '#'
      sqlState = buf.readCharSequence(5, StandardCharsets.UTF_8).toString();
      msg = buf.readCharSequence(buf.readableBytes(), StandardCharsets.UTF_8).toString();
    } else {
      // Pre-4.1 message, still can be output in newer versions (e.g with 'Too many connections')
      msg = buf.readCharSequence(buf.readableBytes(), StandardCharsets.UTF_8).toString();
      sqlState = "HY000";
    }
    ErrorPacket err = new ErrorPacket(sequencer, errorCode, sqlState, msg);
    logger.warn("Error: '{}' sqlState='{}' code={} ", msg, sqlState, errorCode);
    return err;
  }

  public short getErrorCode() {
    return errorCode;
  }

  public String getMessage() {
    return message;
  }

  public String getSqlState() {
    return sqlState;
  }

  @Override
  public boolean ending() {
    return true;
  }
}
