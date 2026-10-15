package me.desukillz.antichunk;

import java.util.Arrays;

/**
 * Đọc và thay thế các chunk section trong mảng byte dữ liệu chunk của gói MAP_CHUNK.
 * Lớp này không phụ thuộc Bukkit hay ProtocolLib để có thể kiểm thử riêng.
 *
 * <p>Định dạng chunk section thay đổi theo phiên bản Minecraft nên lớp này thử lần lượt
 * nhiều định dạng và chỉ chấp nhận định dạng nào đọc hết đúng từng byte của mảng dữ liệu
 * với đúng số section mong đợi.
 */
final class SectionCodec {

    /** header = số byte đầu mỗi section; lengthPrefix = có VarInt độ dài trước mảng long. */
    enum Layout {
        NO_PREFIX(2, false),
        NO_PREFIX_FLUID(4, false),
        PREFIX(2, true),
        PREFIX_FLUID(4, true);

        final int header;
        final boolean lengthPrefix;

        Layout(int header, boolean lengthPrefix) {
            this.header = header;
            this.lengthPrefix = lengthPrefix;
        }
    }

    private static volatile Layout[] order = {
            Layout.NO_PREFIX, Layout.NO_PREFIX_FLUID, Layout.PREFIX, Layout.PREFIX_FLUID};
    private static volatile Layout detected;

    private SectionCodec() {
    }

    /** Sắp xếp thứ tự thử định dạng theo phiên bản Minecraft (chỉ để nhanh, kết quả vẫn được kiểm chứng). */
    static void configure(String minecraftVersion) {
        detected = null;
        if (noLengthPrefix(minecraftVersion)) {
            order = new Layout[]{Layout.NO_PREFIX, Layout.NO_PREFIX_FLUID, Layout.PREFIX, Layout.PREFIX_FLUID};
        } else {
            order = new Layout[]{Layout.PREFIX, Layout.PREFIX_FLUID, Layout.NO_PREFIX, Layout.NO_PREFIX_FLUID};
        }
    }

    static Layout detectedLayout() {
        return detected;
    }

    /** Từ 1.21.5 trở đi mảng long không còn VarInt độ dài ở phía trước. */
    static boolean noLengthPrefix(String version) {
        try {
            String[] parts = version.split("[.\\-]");
            int major = Integer.parseInt(parts[0]);
            if (major != 1) {
                return true; // kiểu đánh số mới (ví dụ 26.1)
            }
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return minor > 21 || (minor == 21 && patch >= 5);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * Thay {@code hiddenSections} section đầu tiên (thấp nhất) bằng section rỗng toàn không khí.
     *
     * @param expectedSections tổng số section của chunk (chiều cao thế giới / 16)
     * @return mảng dữ liệu mới, hoặc null nếu không đọc được / không có gì để thay
     */
    static byte[] strip(byte[] data, int expectedSections, int hiddenSections) {
        if (hiddenSections <= 0 || expectedSections <= 0) {
            return null;
        }
        Layout use = null;
        int[] offsets = null;

        Layout cached = detected;
        if (cached != null) {
            offsets = offsets(data, cached, expectedSections);
            if (offsets != null) {
                use = cached;
            }
        }
        if (use == null) {
            for (Layout layout : order) {
                if (layout == cached) {
                    continue;
                }
                offsets = offsets(data, layout, expectedSections);
                if (offsets != null) {
                    use = layout;
                    detected = layout;
                    break;
                }
            }
        }
        if (use == null) {
            return null;
        }

        int hidden = Math.min(hiddenSections, offsets.length - 1);
        byte[] empty = emptySection(use);
        int tail = data.length - offsets[hidden];
        byte[] out = new byte[empty.length * hidden + tail];
        for (int i = 0; i < hidden; i++) {
            System.arraycopy(empty, 0, out, i * empty.length, empty.length);
        }
        System.arraycopy(data, offsets[hidden], out, empty.length * hidden, tail);
        return out;
    }

    /** Một section rỗng: 0 block, toàn không khí (id 0), biome id 0. */
    static byte[] emptySection(Layout l) {
        int size = l.header + (2 + (l.lengthPrefix ? 1 : 0)) * 2;
        byte[] b = new byte[size]; // toàn số 0 chính là: count=0, bits=0, giá trị=0, [độ dài=0]
        return b;
    }

    /**
     * Trả về vị trí bắt đầu của từng section (kèm vị trí kết thúc ở cuối),
     * hoặc null nếu mảng không khớp định dạng.
     */
    static int[] offsets(byte[] d, Layout l, int expected) {
        int[] offs = new int[expected + 1];
        int n = 0;
        int pos = 0;
        while (pos < d.length) {
            if (n >= expected) {
                return null; // nhiều section hơn dự kiến
            }
            pos += l.header;
            if (pos > d.length) {
                return null;
            }
            pos = skipContainer(d, pos, 4096, 8, l.lengthPrefix);
            if (pos < 0) {
                return null;
            }
            pos = skipContainer(d, pos, 64, 3, l.lengthPrefix);
            if (pos < 0) {
                return null;
            }
            offs[++n] = pos;
        }
        if (pos != d.length || n != expected) {
            return null;
        }
        return offs;
    }

    /** Bỏ qua một paletted container, trả về vị trí sau nó hoặc -1 nếu dữ liệu sai. */
    private static int skipContainer(byte[] d, int pos, int entries, int maxIndirectBits, boolean prefix) {
        if (pos >= d.length) {
            return -1;
        }
        int bits = d[pos++] & 0xFF;
        if (bits > 32) {
            return -1;
        }
        if (bits == 0) {
            long r = readVarInt(d, pos);
            if (r < 0) {
                return -1;
            }
            pos = (int) r;
        } else if (bits <= maxIndirectBits) {
            long r = readVarInt(d, pos);
            if (r < 0) {
                return -1;
            }
            long count = r >>> 32;
            pos = (int) r;
            if (count > 4096) {
                return -1;
            }
            for (long i = 0; i < count; i++) {
                r = readVarInt(d, pos);
                if (r < 0) {
                    return -1;
                }
                pos = (int) r;
            }
        }
        // bits lớn hơn ngưỡng: palette trực tiếp, không có dữ liệu palette

        long longs;
        if (bits == 0) {
            longs = 0;
            if (prefix) {
                long r = readVarInt(d, pos);
                if (r < 0 || (r >>> 32) != 0) {
                    return -1;
                }
                pos = (int) r;
            }
        } else {
            int perLong = 64 / bits;
            long expected = (entries + perLong - 1) / perLong;
            if (prefix) {
                long r = readVarInt(d, pos);
                if (r < 0 || (r >>> 32) != expected) {
                    return -1;
                }
                pos = (int) r;
            }
            longs = expected;
        }
        long end = pos + longs * 8L;
        if (end > d.length) {
            return -1;
        }
        return (int) end;
    }

    /** Đọc VarInt không âm. Trả về (giá trị << 32) | vị trí mới, hoặc -1 nếu lỗi. */
    private static long readVarInt(byte[] d, int pos) {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            if (pos >= d.length) {
                return -1;
            }
            int b = d[pos++];
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                if (value < 0) {
                    return -1;
                }
                return ((long) value << 32) | (pos & 0xFFFFFFFFL);
            }
        }
        return -1;
    }

    /** Dùng cho kiểm thử: so sánh hai mảng. */
    static boolean same(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }
}
