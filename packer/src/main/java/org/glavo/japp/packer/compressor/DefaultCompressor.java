package org.glavo.japp.packer.compressor;

import org.glavo.japp.TODO;
import org.glavo.japp.CompressionMethod;
import org.glavo.japp.packer.compressor.classfile.ClassFileCompressor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class DefaultCompressor implements Compressor {

    private final Map<String, CompressionMethod> map = new HashMap<>();

    public DefaultCompressor() {
        for (String ext : new String[]{
                "png", "apng", "jpg", "jpeg", "webp", "heic", "heif", "avif",
                "aac", "flac", "mp3",
                "mp4", "mkv", "webm",
                "gz", "tgz", "xz", "br", "zst", "bz2", "tbz2"
        }) {
            map.put(ext, CompressionMethod.NONE);
        }
    }

    @Override
    public CompressResult compress(byte[] source) throws IOException {
        return compress(source, (String) null);
    }

    @Override
    public CompressResult compress(byte[] source, String ext) throws IOException {
        if (source.length <= 16) {
            return new CompressResult(CompressionMethod.NONE, source);
        }

        CompressionMethod method = map.getOrDefault(ext, CompressionMethod.DEFLATE);
        CompressResult result;
        switch (method) {
            case NONE:
                result = new CompressResult(CompressionMethod.NONE, source);
                break;
            case CLASSFILE:
                try {
                    result = ClassFileCompressor.INSTANCE.compress(source);
                } catch (Throwable e) {
                    // Malformed class file
                    e.printStackTrace(); // TODO
                    result = Compressor.DEFLATE.compress(source);
                }
                break;
            case DEFLATE:
                result = Compressor.DEFLATE.compress(source);
                break;
            case LZ4:
                result = Compressor.LZ4.compress(source);
                break;
            default:
                throw new TODO("Method: " + method);
        }

        if (result.getLength() < source.length) {
            return result;
        }
        return new CompressResult(CompressionMethod.NONE, source);
    }
}
