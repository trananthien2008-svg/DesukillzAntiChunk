package me.desukillz.antichunk;

import com.comphenix.protocol.events.PacketContainer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tạo bản sao của gói MAP_CHUNK trong đó các section thấp (và block entity bên trong chúng)
 * đã bị xoá. Tìm các trường bằng kiểu dữ liệu thay vì tên nên chạy được với mọi kiểu mapping.
 */
final class ChunkEditor {

    private static final class Access {
        Field holder;          // trường chứa dữ liệu chunk (null = nằm ngay trong gói)
        Field buffer;          // byte[] dữ liệu các section
        Field blockEntities;   // List<BlockEntityInfo>, có thể null
    }

    private static final Map<Class<?>, Access> ACCESS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field[]> INT_FIELDS = new ConcurrentHashMap<>();

    private ChunkEditor() {
    }

    /**
     * @return gói mới đã cắt, hoặc null nếu không có gì để cắt / không đọc được định dạng
     */
    static PacketContainer strip(PacketContainer packet, int minY, int height, int hideBelowY) throws Exception {
        int hiddenSections = (hideBelowY - minY) / 16;
        int total = height / 16;
        if (hiddenSections <= 0 || total <= 0) {
            return null;
        }

        Object handle = packet.getHandle();
        Access a = ACCESS.computeIfAbsent(handle.getClass(), ChunkEditor::resolve);
        if (a.buffer == null) {
            throw new IllegalStateException("Không tìm thấy mảng dữ liệu chunk trong " + handle.getClass().getName());
        }

        Object holder = a.holder == null ? handle : a.holder.get(handle);
        byte[] data = (byte[]) a.buffer.get(holder);
        byte[] stripped = SectionCodec.strip(data, total, Math.min(hiddenSections, total));
        if (stripped == null) {
            return null;
        }

        Object newHandle = PacketUtil.shallowCopy(handle);
        Object newHolder = newHandle;
        if (a.holder != null) {
            newHolder = PacketUtil.shallowCopy(holder);
            a.holder.set(newHandle, newHolder);
        }
        a.buffer.set(newHolder, stripped);

        if (a.blockEntities != null) {
            List<?> list = (List<?>) a.blockEntities.get(holder);
            if (list != null && !list.isEmpty()) {
                List<Object> keep = new ArrayList<>(list.size());
                for (Object info : list) {
                    if (blockEntityY(info) >= hideBelowY) {
                        keep.add(info);
                    }
                }
                if (keep.size() != list.size()) {
                    a.blockEntities.set(newHolder, keep);
                }
            }
        }
        return new PacketContainer(packet.getType(), newHandle);
    }

    private static Access resolve(Class<?> packetClass) {
        Access a = new Access();
        Field direct = firstOfType(packetClass, byte[].class);
        if (direct != null) {
            a.buffer = direct;
            a.blockEntities = firstAssignable(packetClass, List.class);
            return a;
        }
        for (Field f : PacketUtil.instanceFields(packetClass)) {
            Class<?> t = f.getType();
            if (t.isPrimitive() || t.isArray() || t == String.class || t.getName().startsWith("java.")) {
                continue;
            }
            Field buf = firstOfType(t, byte[].class);
            if (buf != null) {
                a.holder = f;
                a.buffer = buf;
                a.blockEntities = firstAssignable(t, List.class);
                break;
            }
        }
        return a;
    }

    private static Field firstOfType(Class<?> c, Class<?> type) {
        for (Field f : PacketUtil.instanceFields(c)) {
            if (f.getType() == type) {
                return f;
            }
        }
        return null;
    }

    private static Field firstAssignable(Class<?> c, Class<?> type) {
        for (Field f : PacketUtil.instanceFields(c)) {
            if (type.isAssignableFrom(f.getType())) {
                return f;
            }
        }
        return null;
    }

    /** Độ cao Y của một BlockEntityInfo (trường int thứ hai: packedXZ rồi y). */
    private static int blockEntityY(Object info) {
        try {
            Field[] ints = INT_FIELDS.computeIfAbsent(info.getClass(), k -> {
                List<Field> list = new ArrayList<>();
                for (Field f : PacketUtil.instanceFields(k)) {
                    if (f.getType() == int.class) {
                        list.add(f);
                    }
                }
                return list.toArray(new Field[0]);
            });
            if (ints.length >= 2) {
                return ints[1].getInt(info);
            }
        } catch (Throwable ignored) {
            // giữ lại block entity nếu không đọc được
        }
        return Integer.MAX_VALUE;
    }
}
