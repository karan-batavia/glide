package com.bumptech.glide.load.resource.bitmap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.os.ParcelFileDescriptor;
import androidx.annotation.Nullable;
import com.bumptech.glide.load.ImageHeaderParser;
import com.bumptech.glide.load.ImageHeaderParser.ImageType;
import com.bumptech.glide.load.ImageHeaderParserUtils;
import com.bumptech.glide.load.data.DataRewinder;
import com.bumptech.glide.load.data.InputStreamRewinder;
import com.bumptech.glide.load.data.ParcelFileDescriptorRewinder;
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;
import com.bumptech.glide.util.ByteBufferUtil;
import com.bumptech.glide.util.Preconditions;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * This is a helper class for {@link Downsampler} that abstracts out image operations from the input
 * type wrapped into a {@link DataRewinder}.
 */
interface ImageReader {

  @Nullable
  Bitmap decodeBitmap(BitmapFactory.Options options) throws IOException;

  ImageHeaderParser.ImageType getImageType() throws IOException;

  int getImageOrientation() throws IOException;

  boolean hasJpegMpf() throws IOException;

  void stopGrowingBuffers();

  final class ByteArrayReader implements ImageReader {

    private final byte[] bytes;
    private final List<ImageHeaderParser> parsers;
    private final ArrayPool byteArrayPool;

    ByteArrayReader(byte[] bytes, List<ImageHeaderParser> parsers, ArrayPool byteArrayPool) {
      this.bytes = bytes;
      this.parsers = parsers;
      this.byteArrayPool = byteArrayPool;
    }

    @Nullable
    @Override
    public Bitmap decodeBitmap(Options options) {
      return GlideBitmapFactory.decodeByteArray(bytes, options, this);
    }

    @Override
    public ImageType getImageType() throws IOException {
      return ImageHeaderParserUtils.getType(parsers, ByteBuffer.wrap(bytes));
    }

    @Override
    public int getImageOrientation() throws IOException {
      return ImageHeaderParserUtils.getOrientation(parsers, ByteBuffer.wrap(bytes), byteArrayPool);
    }

    @Override
    public boolean hasJpegMpf() throws IOException {
      return ImageHeaderParserUtils.hasJpegMpf(parsers, ByteBuffer.wrap(bytes), byteArrayPool);
    }

    @Override
    public void stopGrowingBuffers() {}
  }

  final class FileReader implements ImageReader {

    private final File file;
    private final List<ImageHeaderParser> parsers;
    private final ArrayPool byteArrayPool;

    FileReader(File file, List<ImageHeaderParser> parsers, ArrayPool byteArrayPool) {
      this.file = file;
      this.parsers = parsers;
      this.byteArrayPool = byteArrayPool;
    }

    @Nullable
    @Override
    public Bitmap decodeBitmap(Options options) throws FileNotFoundException {
      InputStream is = null;
      try {
        is = new RecyclableBufferedInputStream(new FileInputStream(file), byteArrayPool);
        return GlideBitmapFactory.decodeStream(is, options, this);
      } finally {
        if (is != null) {
          try {
            is.close();
          } catch (IOException e) {
            // Ignored.
          }
        }
      }
    }

    @Override
    public ImageType getImageType() throws IOException {
      InputStream is = null;
      try {
        is = new RecyclableBufferedInputStream(new FileInputStream(file), byteArrayPool);
        return ImageHeaderParserUtils.getType(parsers, is, byteArrayPool);
      } finally {
        if (is != null) {
          try {
            is.close();
          } catch (IOException e) {
            // Ignored.
          }
        }
      }
    }

    @Override
    public int getImageOrientation() throws IOException {
      InputStream is = null;
      try {
        is = new RecyclableBufferedInputStream(new FileInputStream(file), byteArrayPool);
        return ImageHeaderParserUtils.getOrientation(parsers, is, byteArrayPool);
      } finally {
        if (is != null) {
          try {
            is.close();
          } catch (IOException e) {
            // Ignored.
          }
        }
      }
    }

    @Override
    public boolean hasJpegMpf() throws IOException {
      InputStream is = null;
      try {
        is = new FileInputStream(file);
        return ImageHeaderParserUtils.hasJpegMpf(parsers, is, byteArrayPool);
      } finally {
        if (is != null) {
          try {
            is.close();
          } catch (IOException e) {
            // Ignored.
          }
        }
      }
    }

    @Override
    public void stopGrowingBuffers() {}
  }

  final class ByteBufferReader implements ImageReader {

    private final ByteBuffer buffer;
    private final List<ImageHeaderParser> parsers;
    private final ArrayPool byteArrayPool;

    ByteBufferReader(ByteBuffer buffer, List<ImageHeaderParser> parsers, ArrayPool byteArrayPool) {
      this.buffer = buffer;
      this.parsers = parsers;
      this.byteArrayPool = byteArrayPool;
    }

    @Nullable
    @Override
    public Bitmap decodeBitmap(Options options) {
      InputStream inputStream = stream();
      return GlideBitmapFactory.decodeStream(inputStream, options, this);
    }

    @Override
    public ImageType getImageType() throws IOException {
      return ImageHeaderParserUtils.getType(parsers, ByteBufferUtil.rewind(buffer));
    }

    @Override
    public int getImageOrientation() throws IOException {
      return ImageHeaderParserUtils.getOrientation(
          parsers, ByteBufferUtil.rewind(buffer), byteArrayPool);
    }

    @Override
    public boolean hasJpegMpf() throws IOException {
      return ImageHeaderParserUtils.hasJpegMpf(
          parsers, ByteBufferUtil.rewind(buffer), byteArrayPool);
    }

    @Override
    public void stopGrowingBuffers() {}

    private InputStream stream() {
      return ByteBufferUtil.toStream(ByteBufferUtil.rewind(buffer));
    }
  }

  final class InputStreamImageReader implements ImageReader {
    private final InputStreamRewinder dataRewinder;
    private final ArrayPool byteArrayPool;
    private final List<ImageHeaderParser> parsers;

    InputStreamImageReader(
        InputStream is, List<ImageHeaderParser> parsers, ArrayPool byteArrayPool) {
      this.byteArrayPool = Preconditions.checkNotNull(byteArrayPool);
      this.parsers = Preconditions.checkNotNull(parsers);

      dataRewinder = new InputStreamRewinder(is, byteArrayPool);
    }

    @Nullable
    @Override
    public Bitmap decodeBitmap(BitmapFactory.Options options) throws IOException {
      InputStream inputStream = dataRewinder.rewindAndGet();
      return GlideBitmapFactory.decodeStream(inputStream, options, this);
    }

    @Override
    public ImageHeaderParser.ImageType getImageType() throws IOException {
      return ImageHeaderParserUtils.getType(parsers, dataRewinder.rewindAndGet(), byteArrayPool);
    }

    @Override
    public int getImageOrientation() throws IOException {
      return ImageHeaderParserUtils.getOrientation(
          parsers, dataRewinder.rewindAndGet(), byteArrayPool);
    }

    @Override
    public boolean hasJpegMpf() throws IOException {
      return ImageHeaderParserUtils.hasJpegMpf(parsers, dataRewinder.rewindAndGet(), byteArrayPool);
    }

    @Override
    public void stopGrowingBuffers() {
      dataRewinder.fixMarkLimits();
    }
  }

  final class ParcelFileDescriptorImageReader implements ImageReader {
    private final ArrayPool byteArrayPool;
    private final List<ImageHeaderParser> parsers;
    private final ParcelFileDescriptorRewinder dataRewinder;

    ParcelFileDescriptorImageReader(
        ParcelFileDescriptor parcelFileDescriptor,
        List<ImageHeaderParser> parsers,
        ArrayPool byteArrayPool) {
      this.byteArrayPool = Preconditions.checkNotNull(byteArrayPool);
      this.parsers = Preconditions.checkNotNull(parsers);

      dataRewinder = new ParcelFileDescriptorRewinder(parcelFileDescriptor);
    }

    @Nullable
    @Override
    public Bitmap decodeBitmap(BitmapFactory.Options options) throws IOException {
      ParcelFileDescriptor parcelFileDescriptor = dataRewinder.rewindAndGet();
      FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
      return GlideBitmapFactory.decodeFileDescriptor(fileDescriptor, options, this);
    }

    @Override
    public ImageHeaderParser.ImageType getImageType() throws IOException {
      return ImageHeaderParserUtils.getType(parsers, dataRewinder, byteArrayPool);
    }

    @Override
    public int getImageOrientation() throws IOException {
      return ImageHeaderParserUtils.getOrientation(parsers, dataRewinder, byteArrayPool);
    }

    @Override
    public boolean hasJpegMpf() throws IOException {
      return ImageHeaderParserUtils.hasJpegMpf(parsers, dataRewinder, byteArrayPool);
    }

    @Override
    public void stopGrowingBuffers() {
      // Nothing to do here.
    }
  }
}
