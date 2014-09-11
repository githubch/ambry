package com.github.ambry.messageformat;

import com.github.ambry.store.MessageInfo;
import com.github.ambry.store.MessageWriteSet;
import com.github.ambry.store.Write;

import com.github.ambry.utils.ByteBufferInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A message write set that writes to the underlying write interface
 */
public class MessageFormatWriteSet implements MessageWriteSet {

  private final InputStream streamToWrite;
  private final long maxWriteTimeInMs;
  private long sizeToWrite;
  private List<MessageInfo> streamInfo;
  private Logger logger = LoggerFactory.getLogger(getClass());

  public MessageFormatWriteSet(InputStream stream, List<MessageInfo> streamInfo, long maxWriteTimeInMs,
      boolean materializeStream)
      throws IOException {
    sizeToWrite = 0;
    for (MessageInfo info : streamInfo) {
      sizeToWrite += info.getSize();
    }
    this.streamInfo = streamInfo;
    this.maxWriteTimeInMs = maxWriteTimeInMs;
    if (materializeStream) {
      ByteBufferInputStream byteBufferInputStream =
          new ByteBufferInputStream(stream, (int) sizeToWrite, maxWriteTimeInMs);
      streamToWrite = byteBufferInputStream;
    } else {
      streamToWrite = stream;
    }
  }

  @Override
  public long writeTo(Write writeChannel)
      throws IOException {
    long sizeWritten = 0;
    ReadableByteChannel readableByteChannel = Channels.newChannel(streamToWrite);
    long writeStartTimeInMs = System.currentTimeMillis();
    while (sizeWritten < sizeToWrite) {
      sizeWritten += writeChannel.appendFrom(readableByteChannel, sizeToWrite);
      logger.trace("MessageFormatWriteSet : SizeWritten {} SizeToWrite {} isOpen {} ", sizeWritten, sizeToWrite,
          readableByteChannel.isOpen());
      if (sizeWritten < sizeToWrite && (System.currentTimeMillis() - writeStartTimeInMs) > maxWriteTimeInMs) {
        throw new IOException("Time taken to write is more than maxWriteTimeInMs " + maxWriteTimeInMs);
      }
    }
    return sizeWritten;
  }

  @Override
  public List<MessageInfo> getMessageSetInfo() {
    return streamInfo;
  }
}
